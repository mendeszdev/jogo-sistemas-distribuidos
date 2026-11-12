package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.dto.EstadoDraft;
import br.ucsal.sd.arena.compartilhado.dto.FasePartida;
import br.ucsal.sd.arena.compartilhado.excecao.CriaturaIndisponivelException;
import br.ucsal.sd.arena.compartilhado.excecao.FaseInvalidaException;
import br.ucsal.sd.arena.compartilhado.excecao.LimiteDeEscolhasException;
import br.ucsal.sd.arena.compartilhado.remoto.ClienteCallback;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/** Contenção no estado autoritativo do draft; sem simular rede nem depender de atrasos artificiais. */
@Timeout(120)
class DraftEstresseTest {
    private static final int THREADS = 32;
    private static final int ITERACOES = 1_000;
    private final ExecutorService trabalhadores = Executors.newFixedThreadPool(THREADS);
    private final ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1);
    private final NotificadorCallbacks notificador = new NotificadorCallbacks();
    private final SessaoJogador a = new SessaoJogador("A", "A", callback());
    private final SessaoJogador b = new SessaoJogador("B", "B", callback());

    private enum Resultado { ACEITA, INDISPONIVEL, LIMITE, FORA_DA_FASE, TIMEOUT }
    private record Pedido(SessaoJogador jogador, int criatura) { }

    private static ClienteCallback callback() {
        return (ClienteCallback) Proxy.newProxyInstance(ClienteCallback.class.getClassLoader(),
                new Class<?>[]{ClienteCallback.class}, (proxy, metodo, args) -> null);
    }

    @AfterEach
    void encerrar() throws InterruptedException {
        trabalhadores.shutdownNow();
        timers.shutdownNow();
        notificador.encerrar();
        assertTrue(trabalhadores.awaitTermination(10, TimeUnit.SECONDS), "Threads do teste não encerraram");
        assertTrue(timers.awaitTermination(10, TimeUnit.SECONDS), "Timer de draft não encerrou");
    }

    @Test
    void trintaEDuasThreadsDisputamMesmaCriaturaComUmUnicoVencedor() throws Exception {
        executar("mesma-criatura", i -> new Pedido(i % 2 == 0 ? a : b, 1), false, 1);
    }

    @Test
    void trintaEDuasEscolhasDoMesmoJogadorRespeitamLimiteDeTres() throws Exception {
        executar("limite-por-jogador", i -> new Pedido(a, i % 10 + 1), false, 3);
    }

    @Test
    void doisJogadoresDisputamPoolSemDuplicarOuUltrapassarTresEscolhas() throws Exception {
        executar("pool-compartilhado", i -> new Pedido((i / 10) % 2 == 0 ? a : b, i % 10 + 1), false, 6);
    }

    @Test
    void escolhasConcorrentesComTimeoutCompletamDoisTimesValidos() throws Exception {
        executar("escolhas-contra-timeout", i -> new Pedido(i % 2 == 0 ? a : b, (i / 2) % 10 + 1), true, 6);
    }

    private void executar(String cenario, IntFunction<Pedido> pedidos, boolean disputarTimeout, int totalEsperado)
            throws Exception {
        long inicio = System.nanoTime();
        EnumMap<Resultado, Integer> totais = new EnumMap<>(Resultado.class);
        for (Resultado resultado : Resultado.values()) totais.put(resultado, 0);
        int maiorTime = 0;
        int escolhasAutomaticas = 0;
        timers.setRemoveOnCancelPolicy(true);
        for (int iteracao = 0; iteracao < ITERACOES; iteracao++) {
            Partida partida = new Partida("estresse-" + iteracao, a, b, notificador, timers, Duration.ofHours(1));
            partida.iniciar();
            try {
                CountDownLatch prontos = new CountDownLatch(THREADS);
                CountDownLatch largada = new CountDownLatch(1);
                int indiceTimeout = iteracao % THREADS;
                List<Future<Resultado>> futuros = new ArrayList<>();
                for (int indice = 0; indice < THREADS; indice++) {
                    int tarefa = indice;
                    futuros.add(trabalhadores.submit(() -> {
                        prontos.countDown();
                        assertTrue(largada.await(10, TimeUnit.SECONDS), "Largada não liberada");
                        Resultado resultado;
                        if (disputarTimeout && tarefa == indiceTimeout) {
                            partida.fecharDraftPorTimeout(1);
                            resultado = Resultado.TIMEOUT;
                        } else {
                            Pedido pedido = pedidos.apply(tarefa);
                            resultado = escolher(partida, pedido);
                        }
                        // Os snapshots de cada chamada também devem respeitar as invariantes durante a disputa.
                        verificarSnapshot(partida.getEstadoDraft(a));
                        verificarSnapshot(partida.getEstadoDraft(b));
                        return resultado;
                    }));
                }
                try {
                    assertTrue(prontos.await(10, TimeUnit.SECONDS), "As 32 threads não chegaram à barreira");
                } finally {
                    largada.countDown();
                }
                EnumMap<Resultado, Integer> rodada = new EnumMap<>(Resultado.class);
                for (Future<Resultado> futuro : futuros) {
                    Resultado resultado = futuro.get(10, TimeUnit.SECONDS);
                    rodada.merge(resultado, 1, Integer::sum);
                    totais.merge(resultado, 1, Integer::sum);
                }
                EstadoDraft estadoA = partida.getEstadoDraft(a);
                EstadoDraft estadoB = partida.getEstadoDraft(b);
                verificarSnapshot(estadoA);
                verificarSnapshot(estadoB);
                assertEquals(new HashSet<>(estadoA.minhasEscolhas()), estadoB.escolhasAdversario());
                assertEquals(new HashSet<>(estadoB.minhasEscolhas()), estadoA.escolhasAdversario());
                int tamanhoA = estadoA.minhasEscolhas().size();
                int tamanhoB = estadoB.minhasEscolhas().size();
                assertEquals(totalEsperado, tamanhoA + tamanhoB, "Total incorreto na iteração " + iteracao);
                maiorTime = Math.max(maiorTime, Math.max(tamanhoA, tamanhoB));
                if (disputarTimeout) {
                    assertEquals(1, rodada.getOrDefault(Resultado.TIMEOUT, 0));
                    escolhasAutomaticas += totalEsperado - rodada.getOrDefault(Resultado.ACEITA, 0);
                    // Um timeout repetido não pode escolher novamente nem alterar times já completos.
                    partida.fecharDraftPorTimeout(1);
                    assertEquals(estadoA, partida.getEstadoDraft(a));
                    assertEquals(estadoB, partida.getEstadoDraft(b));
                } else {
                    assertEquals(totalEsperado, rodada.getOrDefault(Resultado.ACEITA, 0));
                }
                if (totalEsperado == 1) {
                    assertEquals(31, rodada.getOrDefault(Resultado.INDISPONIVEL, 0));
                    assertEquals(List.of(1), tamanhoA == 1 ? estadoA.minhasEscolhas() : estadoB.minhasEscolhas());
                }
                if (totalEsperado == 3) {
                    assertEquals(3, tamanhoA);
                    assertEquals(0, tamanhoB);
                    assertTrue(rodada.getOrDefault(Resultado.LIMITE, 0) > 0);
                }
                if (totalEsperado == 6) {
                    assertEquals(3, tamanhoA);
                    assertEquals(3, tamanhoB);
                    assertEquals(FasePartida.BATALHA, partida.getFase());
                } else {
                    assertEquals(FasePartida.DRAFT, partida.getFase());
                    assertEquals(0, rodada.getOrDefault(Resultado.FORA_DA_FASE, 0));
                }
            } finally {
                partida.encerrarPorWO(a);
            }
        }
        assertTrue(timers.getQueue().isEmpty(), "Prazos pendentes após encerrar as salas");
        assertEquals(THREADS * ITERACOES, totais.values().stream().mapToInt(Integer::intValue).sum());
        System.out.printf("[ESTRESSE-DRAFT] cenario=%s | iteracoes=%d | threads=%d | tarefas=%d | "
                        + "aceitas=%d | indisponiveis=%d | limite=%d | fora_da_fase=%d | timeouts=%d | "
                        + "automaticas=%d | maior_time=%d | duplicacoes=0 | violacoes=0 | tempo_ms=%d%n",
                cenario, ITERACOES, THREADS, THREADS * ITERACOES, totais.get(Resultado.ACEITA),
                totais.get(Resultado.INDISPONIVEL), totais.get(Resultado.LIMITE), totais.get(Resultado.FORA_DA_FASE),
                totais.get(Resultado.TIMEOUT), escolhasAutomaticas, maiorTime,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
    }

    private Resultado escolher(Partida partida, Pedido pedido) throws Exception {
        try {
            partida.escolherCriatura(pedido.jogador(), pedido.criatura());
            return Resultado.ACEITA;
        } catch (CriaturaIndisponivelException e) {
            return Resultado.INDISPONIVEL;
        } catch (LimiteDeEscolhasException e) {
            return Resultado.LIMITE;
        } catch (FaseInvalidaException e) {
            return Resultado.FORA_DA_FASE;
        }
    }

    private static void verificarSnapshot(EstadoDraft estado) {
        assertTrue(estado.minhasEscolhas().size() <= 3, "Um jogador ultrapassou três criaturas");
        assertTrue(estado.escolhasAdversario().size() <= 3, "O adversário ultrapassou três criaturas");
        assertEquals(estado.minhasEscolhas().size(), new HashSet<>(estado.minhasEscolhas()).size(),
                "Criatura repetida no mesmo time");
        assertTrue(Collections.disjoint(estado.minhasEscolhas(), estado.escolhasAdversario()),
                "A mesma criatura foi atribuída aos dois jogadores");
    }
}
