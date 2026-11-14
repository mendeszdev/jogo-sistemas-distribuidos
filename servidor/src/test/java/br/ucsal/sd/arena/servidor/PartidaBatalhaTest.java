package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.dto.*;
import br.ucsal.sd.arena.compartilhado.excecao.*;
import br.ucsal.sd.arena.compartilhado.remoto.ClienteCallback;
import java.lang.reflect.Proxy;
import java.rmi.server.UnicastRemoteObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartidaBatalhaTest {
    private final NotificadorCallbacks notificador = new NotificadorCallbacks();
    private final ScheduledExecutorService draftTimer = Executors.newSingleThreadScheduledExecutor();
    private final List<Partida> partidas = new ArrayList<>();
    private final Eventos eventosA = new Eventos();
    private final Eventos eventosB = new Eventos();
    private final SessaoJogador a = new SessaoJogador("Alice", "A", eventosA.callback());
    private final SessaoJogador b = new SessaoJogador("Bruno", "B", eventosB.callback());
    private final AtomicInteger encerramentos = new AtomicInteger();

    private static class Eventos {
        final BlockingQueue<EstadoBatalha> inicios = new LinkedBlockingQueue<>();
        final BlockingQueue<ResultadoRodada> rodadas = new LinkedBlockingQueue<>();
        final BlockingQueue<ResultadoPartida> finais = new LinkedBlockingQueue<>();
        final List<String> ordem = new CopyOnWriteArrayList<>();

        ClienteCallback callback() {
            return (ClienteCallback) Proxy.newProxyInstance(ClienteCallback.class.getClassLoader(),
                    new Class<?>[]{ClienteCallback.class}, (proxy, metodo, args) -> {
                        ordem.add(metodo.getName());
                        switch (metodo.getName()) {
                            case "batalhaIniciada" -> inicios.add((EstadoBatalha) args[0]);
                            case "rodadaResolvida" -> rodadas.add((ResultadoRodada) args[0]);
                            case "partidaEncerrada" -> finais.add((ResultadoPartida) args[0]);
                            default -> { }
                        }
                        return null;
                    });
        }
    }

    private static <T> T receber(BlockingQueue<T> fila) throws InterruptedException {
        T valor = fila.poll(5, TimeUnit.SECONDS);
        assertNotNull(valor, "Evento não recebido no prazo");
        return valor;
    }

    private Partida nova(Duration turno) throws Exception {
        Partida partida = new Partida("batalha", a, b, notificador, draftTimer,
                Duration.ofHours(1), turno, p -> encerramentos.incrementAndGet());
        partidas.add(partida);
        partida.iniciar();
        for (int id = 1; id <= 3; id++) partida.escolherCriatura(a, id);
        for (int id = 4; id <= 6; id++) partida.escolherCriatura(b, id);
        return partida;
    }

    @AfterEach
    void encerrar() {
        for (Partida partida : partidas) partida.encerrarPorWO(a);
        draftTimer.shutdownNow();
        notificador.encerrar();
    }

    @Test
    void draftIniciaRelogioEAcoesResolvemProximaRodadaComVisoesIndividuais() throws Exception {
        Partida partida = nova(Duration.ofSeconds(30));
        EstadoBatalha inicioA = receber(eventosA.inicios);
        EstadoBatalha inicioB = receber(eventosB.inicios);
        assertTrue(inicioA.segundosRestantes() > 0);
        assertEquals(inicioA.minhaAtiva(), inicioB.ativaAdversario());
        assertTrue(eventosA.ordem.lastIndexOf("draftAtualizado") < eventosA.ordem.indexOf("batalhaIniciada"));
        assertThrows(AcaoInvalidaException.class, () -> partida.enviarAcao(a, null));
        assertThrows(AcaoInvalidaException.class, () -> partida.enviarAcao(a, new Acao(TipoAcao.ATACAR, 4, 1)));
        assertThrows(ForaDaRodadaException.class, () -> partida.enviarAcao(a, new Acao(TipoAcao.ATACAR, 0)));
        partida.enviarAcao(a, new Acao(TipoAcao.ATACAR, 0, 1));
        assertThrows(ForaDaRodadaException.class, () -> partida.enviarAcao(a, new Acao(TipoAcao.TROCAR, 0, 1)));
        assertTrue(eventosA.rodadas.isEmpty());
        partida.enviarAcao(b, new Acao(TipoAcao.ATACAR, 0, 1));
        ResultadoRodada rA = receber(eventosA.rodadas);
        ResultadoRodada rB = receber(eventosB.rodadas);
        assertEquals(2, rA.estadoAtual().numeroRodada());
        assertEquals(72, rA.estadoAtual().minhaAtiva().hpAtual());
        assertEquals(53, rB.estadoAtual().minhaAtiva().hpAtual());
        assertTrue(rA.estadoAtual().segundosRestantes() > 0);
        assertEquals(rA.estadoAtual().minhaAtiva(), rB.estadoAtual().ativaAdversario());
        assertThrows(ForaDaRodadaException.class, () -> partida.enviarAcao(b, new Acao(TipoAcao.ATACAR, 0, 1)));
    }

    @Test
    void timeoutRealResolveSemAcoesEDisparaRodadaSeguinte() throws Exception {
        Partida partida = nova(Duration.ofMillis(100));
        ResultadoRodada rodada = receber(eventosA.rodadas);
        partida.encerrarPorWO(a);
        assertEquals(1, rodada.numeroRodada());
        assertEquals(2, rodada.estadoAtual().numeroRodada());
        assertEquals(72, rodada.estadoAtual().minhaAtiva().hpAtual());
        assertEquals(53, rodada.estadoAtual().ativaAdversario().hpAtual());
    }

    @Test
    void timeoutPreservaTrocaRecebidaEWOLevaEstatisticasReais() throws Exception {
        Partida partida = nova(Duration.ofMillis(200));
        partida.enviarAcao(a, new Acao(TipoAcao.TROCAR, 0, 1));
        ResultadoRodada r = receber(eventosA.rodadas);
        assertEquals(new EstadoCriaturaEmBatalha(2, 57), r.estadoAtual().minhaAtiva());
        assertTrue(partida.encerrarPorWO(a));
        assertFalse(partida.encerrarPorWO(a));
        ResultadoPartida fim = receber(eventosB.finais);
        assertTrue(fim.porWO());
        assertEquals("Bruno", fim.vencedor());
        assertEquals(1, fim.rodadasJogadas());
        assertEquals(3, fim.meuTime().size());
        assertEquals(new EstadoCriaturaEmBatalha(2, 57), fim.timeAdversario().get(1));
        assertNull(eventosA.rodadas.poll(300, TimeUnit.MILLISECONDS));
        assertThrows(FaseInvalidaException.class, () -> partida.enviarAcao(b, new Acao(TipoAcao.ATACAR, 0, 2)));
    }

    @Test
    void eliminacaoNotificaUmaVezOsDoisEEncerraTimer() throws Exception {
        Partida partida = nova(Duration.ofSeconds(30));
        for (int numero = 1; numero <= 5; numero++) {
            partida.enviarAcao(a, new Acao(TipoAcao.ATACAR, 3, numero));
            partida.enviarAcao(b, new Acao(TipoAcao.ATACAR, 3, numero));
            assertEquals(numero, receber(eventosA.rodadas).numeroRodada());
            receber(eventosB.rodadas);
        }
        ResultadoPartida fimA = receber(eventosA.finais);
        ResultadoPartida fimB = receber(eventosB.finais);
        assertEquals(FasePartida.ENCERRADA, partida.getFase());
        assertEquals("Alice", fimA.vencedor());
        assertFalse(fimA.porWO());
        assertEquals(5, fimA.rodadasJogadas());
        assertEquals(26, fimA.meuTime().getFirst().hpAtual());
        assertTrue(fimA.timeAdversario().stream().allMatch(EstadoCriaturaEmBatalha::derrubada));
        assertEquals(fimA.meuTime(), fimB.timeAdversario());
        assertEquals(fimA.timeAdversario(), fimB.meuTime());
        assertEquals(1, encerramentos.get());
        assertFalse(partida.encerrarPorWO(a));
        assertEquals("partidaEncerrada", eventosA.ordem.getLast());
    }

    @Test
    void ultimaAcaoContraWOProduzUmUnicoResultadoFinalCoerente() throws Exception {
        Partida partida = nova(Duration.ofSeconds(30));
        for (int n = 1; n <= 4; n++) {
            partida.enviarAcao(a, new Acao(TipoAcao.ATACAR, 3, n));
            partida.enviarAcao(b, new Acao(TipoAcao.ATACAR, 3, n));
            receber(eventosA.rodadas);
            receber(eventosB.rodadas);
        }
        partida.enviarAcao(a, new Acao(TipoAcao.ATACAR, 3, 5));
        ExecutorService threads = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Future<?> acao = threads.submit(() -> {
                try {
                    largada.await();
                    partida.enviarAcao(b, new Acao(TipoAcao.ATACAR, 3, 5));
                } catch (FaseInvalidaException | ForaDaRodadaException esperado) {
                    // O W.O. pode encerrar a sala antes de a ação entrar na barreira.
                } catch (Exception e) { throw new AssertionError(e); }
            });
            Future<?> wo = threads.submit(() -> {
                try { largada.await(); partida.encerrarPorWO(a); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            });
            largada.countDown();
            acao.get(5, TimeUnit.SECONDS);
            wo.get(5, TimeUnit.SECONDS);
        } finally { threads.shutdownNow(); }
        ResultadoPartida fim = receber(eventosB.finais);
        notificador.encerrar();
        assertEquals(FasePartida.ENCERRADA, partida.getFase());
        assertEquals(fim.porWO() ? "Bruno" : "Alice", fim.vencedor());
        assertEquals(fim.porWO() ? 4 : 5, fim.rodadasJogadas());
        assertEquals(fim.porWO() ? 0 : 1, eventosB.rodadas.size());
        assertTrue(eventosB.finais.isEmpty());
        assertEquals(fim.porWO() ? 0 : 1, eventosA.finais.size());
    }

    @Test
    void servicoLiberaJogadoresEAceitaNovaPartidaAposVitoria() throws Exception {
        ServicoJogoImpl servico = new ServicoJogoImpl(notificador);
        try {
            String tokenA = servico.conectar("Alice", eventosA.callback());
            String tokenB = servico.conectar("Bruno", eventosB.callback());
            assertThrows(SessaoInvalidaException.class,
                    () -> servico.enviarAcao("invalido", new Acao(TipoAcao.ATACAR, 0, 1)));
            assertThrows(FaseInvalidaException.class,
                    () -> servico.enviarAcao(tokenA, new Acao(TipoAcao.ATACAR, 0, 1)));
            servico.buscarPartida(tokenA);
            servico.buscarPartida(tokenB);
            assertThrows(FaseInvalidaException.class,
                    () -> servico.enviarAcao(tokenA, new Acao(TipoAcao.ATACAR, 0, 1)));
            for (int id = 1; id <= 3; id++) servico.escolherCriatura(tokenA, id);
            for (int id = 4; id <= 6; id++) servico.escolherCriatura(tokenB, id);
            for (int n = 1; n <= 5; n++) {
                servico.enviarAcao(tokenA, new Acao(TipoAcao.ATACAR, 3, n));
                servico.enviarAcao(tokenB, new Acao(TipoAcao.ATACAR, 3, n));
            }
            assertEquals(5, receber(eventosA.finais).rodadasJogadas());
            assertTrue(servico.listarJogadoresOnline(tokenA).stream()
                    .allMatch(j -> j.statusJogador() == StatusJogador.LIVRE));
            assertTrue(servico.sessoesAtivas().stream().allMatch(s -> s.getPartida() == null));
            servico.buscarPartida(tokenA);
            servico.buscarPartida(tokenB);
            assertTrue(servico.sessoesAtivas().stream().allMatch(s -> s.getPartida() != null));
            servico.desconectar(tokenA);
            servico.desconectar(tokenB);
        } finally {
            for (SessaoJogador sessao : servico.sessoesAtivas()) servico.removerSessao(sessao, "fim do teste");
            UnicastRemoteObject.unexportObject(servico, true);
        }
    }
}
