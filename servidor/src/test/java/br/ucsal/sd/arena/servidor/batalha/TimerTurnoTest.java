package br.ucsal.sd.arena.servidor.batalha;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
import br.ucsal.sd.arena.compartilhado.dto.TipoAcao;
import br.ucsal.sd.arena.compartilhado.excecao.ForaDaRodadaException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimerTurnoTest {
    private static final Acao PADRAO = new Acao(TipoAcao.ATACAR, 0);
    private static final Acao TROCA = new Acao(TipoAcao.TROCAR, 1);

    @Test
    void acaoAposPrazoEhRejeitadaMesmoQuandoThreadDoTimerEstaOcupada() throws Exception {
        CountDownLatch entrou = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicReference<Map<String, Acao>> acoes = new AtomicReference<>();
        AtomicInteger chamadas = new AtomicInteger();
        try (TimerTurno timer = new TimerTurno("ocupada")) {
            timer.agendar(() -> {
                entrou.countDown();
                try { liberar.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, Duration.ofMillis(1));
            assertTrue(entrou.await(5, TimeUnit.SECONDS));
            RodadaSimultanea rodada = new RodadaSimultanea(1, "A", "B", (n, recebidas) -> {
                acoes.set(recebidas); chamadas.incrementAndGet(); return Map.of();
            }, PoliticaAcaoPadrao.ATAQUE_BASICO, r -> {}, timer, Duration.ofMillis(10));
            assertFalse(new CountDownLatch(1).await(30, TimeUnit.MILLISECONDS));
            assertThrows(ForaDaRodadaException.class, () -> rodada.registrarAcao("A", TROCA));
            assertEquals(Map.of("A", PADRAO, "B", PADRAO), acoes.get());
            rodada.fecharPorTimeout();
            assertEquals(1, chamadas.get());
        } finally { liberar.countDown(); }
    }

    @Test
    void prazoRealCompletaSomenteQuemNaoJogou() throws Exception {
        try (TimerTurno timer = new TimerTurno("sala")) {
            CountDownLatch terminou = new CountDownLatch(1);
            AtomicReference<Map<String, Acao>> recebidas = new AtomicReference<>();
            RodadaSimultanea rodada = new RodadaSimultanea(1, "A", "B", (numero, acoes) -> {
                recebidas.set(acoes);
                return Map.of();
            }, PoliticaAcaoPadrao.ATAQUE_BASICO, r -> terminou.countDown(), timer, Duration.ofMillis(200));
            rodada.registrarAcao("A", TROCA);
            assertTrue(terminou.await(5, TimeUnit.SECONDS));
            assertEquals(Map.of("A", TROCA, "B", PADRAO), recebidas.get());
            assertThrows(ForaDaRodadaException.class, () -> rodada.registrarAcao("B", TROCA));
        }
    }

    @Test
    void salasTemThreadsIndependentesMesmoComResolvedorBloqueado() throws Exception {
        CountDownLatch entrou = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        CountDownLatch outraSala = new CountDownLatch(1);
        try (TimerTurno primeiro = new TimerTurno("primeira"); TimerTurno segundo = new TimerTurno("segunda")) {
            new RodadaSimultanea(1, "A", "B", (n, a) -> {
                entrou.countDown();
                try { liberar.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return Map.of();
            }, PoliticaAcaoPadrao.ATAQUE_BASICO, r -> {}, primeiro, Duration.ofMillis(10));
            assertTrue(entrou.await(5, TimeUnit.SECONDS));
            new RodadaSimultanea(1, "C", "D", (n, a) -> Map.of(), PoliticaAcaoPadrao.ATAQUE_BASICO,
                    r -> outraSala.countDown(), segundo, Duration.ofMillis(10));
            assertTrue(outraSala.await(5, TimeUnit.SECONDS));
        } finally { liberar.countDown(); }
    }

    @Test
    void cancelamentoEDuasAcoesImpedemTimeoutAntigoNaProximaRodada() throws Exception {
        try (TimerTurno timer = new TimerTurno("sala")) {
            AtomicInteger resolucoes = new AtomicInteger();
            RodadaSimultanea cancelada = new RodadaSimultanea(1, "A", "B", (n, a) -> {
                resolucoes.addAndGet(100); return Map.of();
            }, PoliticaAcaoPadrao.ATAQUE_BASICO, r -> {}, timer, Duration.ofMillis(200));
            cancelada.cancelar();
            assertThrows(ForaDaRodadaException.class, () -> cancelada.registrarAcao("A", TROCA));
            RodadaSimultanea completa = new RodadaSimultanea(2, "A", "B", (n, a) -> {
                resolucoes.incrementAndGet(); return Map.of();
            }, PoliticaAcaoPadrao.ATAQUE_BASICO, r -> {}, timer, Duration.ofMillis(200));
            completa.registrarAcao("A", TROCA);
            completa.registrarAcao("B", TROCA);
            CountDownLatch terminou = new CountDownLatch(1);
            new RodadaSimultanea(3, "A", "B", (n, a) -> {
                assertEquals(Map.of("A", PADRAO, "B", PADRAO), a);
                resolucoes.incrementAndGet(); return Map.of();
            }, PoliticaAcaoPadrao.ATAQUE_BASICO, r -> terminou.countDown(), timer, Duration.ofMillis(400));
            assertTrue(terminou.await(5, TimeUnit.SECONDS));
            assertEquals(2, resolucoes.get());
        }
    }

    @Test
    void rejeitaDuracaoNaoPositivaETimerEncerrado() {
        TimerTurno timer = new TimerTurno("sala");
        try {
            assertThrows(IllegalArgumentException.class, () -> timer.agendar(() -> {}, Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> timer.agendar(() -> {}, Duration.ofSeconds(-1)));
        } finally { timer.close(); }
        assertThrows(java.util.concurrent.RejectedExecutionException.class,
                () -> timer.agendar(() -> {}, Duration.ofSeconds(1)));
    }
}
