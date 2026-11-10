package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.dto.*;
import br.ucsal.sd.arena.compartilhado.excecao.*;
import br.ucsal.sd.arena.compartilhado.remoto.ClienteCallback;
import java.lang.reflect.Proxy;
import java.rmi.server.UnicastRemoteObject;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartidaDraftTest {
    private final NotificadorCallbacks notificador = new NotificadorCallbacks();
    private final ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1);
    private final ExecutorService threads = Executors.newFixedThreadPool(2);
    private final List<EstadoDraft> eventosA = new CopyOnWriteArrayList<>();
    private final List<EstadoDraft> eventosB = new CopyOnWriteArrayList<>();
    private final SessaoJogador a = new SessaoJogador("A", "A", callback(eventosA::add));
    private final SessaoJogador b = new SessaoJogador("B", "B", callback(eventosB::add));

    private static ClienteCallback callback(Consumer<EstadoDraft> receber) {
        return (ClienteCallback) Proxy.newProxyInstance(ClienteCallback.class.getClassLoader(),
                new Class<?>[]{ClienteCallback.class}, (proxy, method, args) -> {
                    if (method.getName().equals("draftAtualizado")) receber.accept((EstadoDraft) args[0]);
                    return null;
                });
    }

    private Partida nova(Duration duracao) {
        Partida partida = new Partida("teste", a, b, notificador, timers, duracao);
        partida.iniciar();
        return partida;
    }

    private Partida nova() { return nova(Duration.ofHours(1)); }

    @AfterEach
    void encerrar() {
        timers.shutdownNow();
        threads.shutdownNow();
        notificador.encerrar();
    }

    private void disputar(Runnable primeira, Runnable segunda) throws Exception {
        CountDownLatch prontos = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (Runnable tarefa : List.of(primeira, segunda)) {
            futuros.add(threads.submit(() -> {
                prontos.countDown();
                try {
                    assertTrue(largada.await(5, TimeUnit.SECONDS));
                    tarefa.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }));
        }
        assertTrue(prontos.await(5, TimeUnit.SECONDS));
        largada.countDown();
        for (Future<?> futuro : futuros) futuro.get(5, TimeUnit.SECONDS);
    }

    private Runnable escolher(Partida partida, SessaoJogador jogador, int id, Class<?> erroEsperado) {
        return () -> {
            try {
                partida.escolherCriatura(jogador, id);
            } catch (JogoException e) {
                if (erroEsperado == null || !erroEsperado.isInstance(e)) throw new AssertionError(e);
            }
        };
    }

    @Test
    void disputaPelaMesmaCriaturaMilVezes() throws Exception {
        for (int i = 0; i < 1000; i++) {
            Partida partida = nova();
            java.util.concurrent.atomic.AtomicInteger perdedores = new java.util.concurrent.atomic.AtomicInteger();
            Consumer<SessaoJogador> tentar = jogador -> {
                try {
                    partida.escolherCriatura(jogador, 1);
                } catch (CriaturaIndisponivelException e) {
                    perdedores.incrementAndGet();
                } catch (JogoException e) {
                    throw new AssertionError(e);
                }
            };
            disputar(() -> tentar.accept(a), () -> tentar.accept(b));
            assertEquals(1, perdedores.get());
            assertEquals(1, partida.getEstadoDraft(a).minhasEscolhas().size()
                    + partida.getEstadoDraft(b).minhasEscolhas().size());
            partida.encerrarPorWO(a);
        }
    }

    @Test
    void validacaoRespeitaFaseDisponibilidadeELimite() throws Exception {
        Partida partida = nova();
        for (int id = 1; id <= 3; id++) partida.escolherCriatura(a, id);
        assertThrows(CriaturaIndisponivelException.class, () -> partida.escolherCriatura(a, -1));
        assertThrows(CriaturaIndisponivelException.class, () -> partida.escolherCriatura(a, 1));
        assertThrows(LimiteDeEscolhasException.class, () -> partida.escolherCriatura(a, 4));
        for (int id = 4; id <= 6; id++) partida.escolherCriatura(b, id);
        assertEquals(FasePartida.BATALHA, partida.getFase());
        assertThrows(FaseInvalidaException.class, () -> partida.escolherCriatura(a, 1));
        partida.fecharDraftPorTimeout(1);
        assertEquals(List.of(1, 2, 3), partida.getEstadoDraft(a).minhasEscolhas());
    }

    @Test
    void timerRealCompletaTimesENotificaCadaEscolha() throws Exception {
        CountDownLatch completos = new CountDownLatch(2);
        List<EstadoDraft> estados = new CopyOnWriteArrayList<>();
        ClienteCallback cb = callback(estado -> {
            estados.add(estado);
            if (estado.minhasEscolhas().size() == 3 && estado.escolhasAdversario().size() == 3) {
                completos.countDown();
            }
        });
        SessaoJogador x = new SessaoJogador("X", "X", cb);
        SessaoJogador y = new SessaoJogador("Y", "Y", cb);
        Partida partida = new Partida("timer", x, y, notificador, timers, Duration.ofMillis(30));
        partida.iniciar();
        assertTrue(completos.await(5, TimeUnit.SECONDS));
        assertEquals(FasePartida.BATALHA, partida.getFase());
        assertEquals(14, estados.size()); // estado inicial + seis escolhas para cada jogador
        EstadoDraft estado = partida.getEstadoDraft(x);
        assertEquals(3, estado.minhasEscolhas().size());
        assertEquals(3, estado.escolhasAdversario().size());
        assertTrue(Collections.disjoint(estado.minhasEscolhas(), estado.escolhasAdversario()));
        assertEquals(0, estado.segundosRestantes());
    }

    @Test
    void escolhasManuaisNotificamOsDoisNaOrdemComVisoesIndividuais() throws Exception {
        Partida partida = nova();
        partida.escolherCriatura(a, 1);
        partida.escolherCriatura(b, 2);
        notificador.encerrar();
        assertEquals(3, eventosA.size());
        assertEquals(3, eventosB.size());
        assertEquals(List.of(1), eventosA.get(1).minhasEscolhas());
        assertEquals(Set.of(1), eventosB.get(1).escolhasAdversario());
        assertEquals(List.of(2), eventosB.get(2).minhasEscolhas());
        assertEquals(Set.of(2), eventosA.get(2).escolhasAdversario());
        assertEquals(10, eventosA.get(2).pool().size());
        assertTrue(eventosA.get(2).segundosRestantes() > 0);
        assertTrue(eventosA.get(0).minhasEscolhas().isEmpty()); // snapshot imutável
    }

    @Test
    void escolhaContraTimeoutMilVezes() throws Exception {
        for (int i = 0; i < 1000; i++) {
            Partida partida = nova();
            for (int id = 1; id <= 3; id++) partida.escolherCriatura(a, id);
            partida.escolherCriatura(b, 4);
            partida.escolherCriatura(b, 5);
            disputar(escolher(partida, b, 6, FaseInvalidaException.class),
                    () -> partida.fecharDraftPorTimeout(1));
            assertEquals(FasePartida.BATALHA, partida.getFase());
            EstadoDraft estado = partida.getEstadoDraft(a);
            assertEquals(3, estado.minhasEscolhas().size());
            assertEquals(3, estado.escolhasAdversario().size());
            assertTrue(Collections.disjoint(estado.minhasEscolhas(), estado.escolhasAdversario()));
            partida.fecharDraftPorTimeout(1);
            assertEquals(estado, partida.getEstadoDraft(a));
        }
        notificador.encerrar();
        // Sete snapshots por sala: inicial + seis escolhas. Timeout tardio não duplica o final.
        assertEquals(7000, eventosA.size());
        assertEquals(7000, eventosB.size());
    }

    @Test
    void inicioIdempotenteETimerCanceladoAoCompletar() throws Exception {
        timers.setRemoveOnCancelPolicy(true);
        Partida partida = nova();
        partida.iniciar();
        assertEquals(1, timers.getQueue().size());
        for (int id = 1; id <= 3; id++) partida.escolherCriatura(a, id);
        for (int id = 4; id <= 6; id++) partida.escolherCriatura(b, id);
        assertTrue(timers.getQueue().isEmpty());
        partida.iniciar();
        assertTrue(timers.getQueue().isEmpty());
        notificador.encerrar();
        assertEquals(7, eventosA.size());
        assertEquals(7, eventosB.size());
    }

    @Test
    void salasIndependentesEWOCancelaDraft() throws Exception {
        Partida primeira = nova();
        Partida segunda = nova();
        primeira.escolherCriatura(a, 1);
        segunda.escolherCriatura(b, 1);
        assertEquals(List.of(1), primeira.getEstadoDraft(a).minhasEscolhas());
        assertEquals(List.of(1), segunda.getEstadoDraft(b).minhasEscolhas());
        assertTrue(primeira.encerrarPorWO(a));
        primeira.fecharDraftPorTimeout(1);
        assertFalse(primeira.encerrarPorWO(a));
        assertEquals(FasePartida.ENCERRADA, primeira.getFase());
        assertTrue(primeira.getEstadoDraft(b).minhasEscolhas().isEmpty());
        assertEquals(FasePartida.DRAFT, segunda.getFase());
    }

    @Test
    void servicoValidaTokenEDelegaParaPartidaDaSessao() throws Exception {
        ServicoJogoImpl servico = new ServicoJogoImpl(notificador);
        try {
            String tokenA = servico.conectar("A", callback(eventosA::add));
            String tokenB = servico.conectar("B", callback(eventosB::add));
            assertThrows(SessaoInvalidaException.class, () -> servico.escolherCriatura("invalido", 1));
            assertThrows(FaseInvalidaException.class, () -> servico.escolherCriatura(tokenA, 1));
            servico.buscarPartida(tokenA);
            servico.buscarPartida(tokenB);
            servico.escolherCriatura(tokenA, 1);
            assertThrows(CriaturaIndisponivelException.class, () -> servico.escolherCriatura(tokenB, 1));
            servico.desconectar(tokenA);
            servico.desconectar(tokenB);
        } finally {
            UnicastRemoteObject.unexportObject(servico, true);
        }
    }

    @Test
    void catalogoOficialTemDezCriaturasComQuatroAtaquesCada() {
        List<Criatura> catalogo = CatalogoCriaturas.listar();
        assertEquals(10, catalogo.size());
        assertEquals(10, catalogo.stream().map(Criatura::id).distinct().count());
        for (Criatura criatura : catalogo) {
            assertEquals(List.of(40, 60, 30, 90), criatura.ataques().stream().map(Ataque::poder).toList());
            assertTrue(criatura.ataques().stream().allMatch(at -> at.tipo() == criatura.tipo()));
        }
        assertEquals("Jato", catalogo.get(6).ataques().getFirst().nome());
    }
}
