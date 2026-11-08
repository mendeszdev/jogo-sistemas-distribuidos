package br.ucsal.sd.arena.servidor.batalha;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoRodada;
import br.ucsal.sd.arena.compartilhado.dto.TipoAcao;
import br.ucsal.sd.arena.compartilhado.excecao.ForaDaRodadaException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RodadaSimultaneaTest {

    private static final Acao ATAQUE = new Acao(TipoAcao.ATACAR, 0);
    private static final Acao TROCA = new Acao(TipoAcao.TROCAR, 1);
    private static final Acao PADRAO = new Acao(TipoAcao.ATACAR, 0);

    /** Resolvedor falso: conta chamadas e guarda as ações recebidas. */
    private static class ResolvedorFalso implements ResolvedorRodada {
        final AtomicInteger chamadas = new AtomicInteger();
        final AtomicReference<Map<String, Acao>> ultimas = new AtomicReference<>();

        @Override
        public Map<String, ResultadoRodada> resolver(int numeroRodada, Map<String, Acao> acoesPorToken) {
            chamadas.incrementAndGet();
            ultimas.set(acoesPorToken);
            Map<String, ResultadoRodada> r = new HashMap<>();
            acoesPorToken.keySet().forEach(t -> r.put(t, new ResultadoRodada(numeroRodada, List.of("ev"), null)));
            return r;
        }
    }

    private final ResolvedorFalso resolvedor = new ResolvedorFalso();
    private final AtomicInteger notificacoes = new AtomicInteger();

    private RodadaSimultanea nova() {
        return new RodadaSimultanea(1, "A", "B", resolvedor, t -> PADRAO, r -> notificacoes.incrementAndGet());
    }

    @Test
    void duasAcoesResolvemUmaVezComAsDuas() throws Exception {
        RodadaSimultanea r = nova();
        r.registrarAcao("A", ATAQUE);
        assertFalse(r.estaFechada());
        r.registrarAcao("B", TROCA);

        assertTrue(r.estaFechada());
        assertEquals(1, resolvedor.chamadas.get());
        assertEquals(1, notificacoes.get());
        assertEquals(Map.of("A", ATAQUE, "B", TROCA), resolvedor.ultimas.get());
        assertEquals(1, r.getNumero());
    }

    @Test
    void acaoRepetidaEhRejeitada() throws Exception {
        RodadaSimultanea r = nova();
        r.registrarAcao("A", ATAQUE);
        assertThrows(ForaDaRodadaException.class, () -> r.registrarAcao("A", TROCA));
        assertEquals(0, resolvedor.chamadas.get());
    }

    @Test
    void acaoDepoisDoFechamentoEhRejeitada() throws Exception {
        RodadaSimultanea r = nova();
        r.registrarAcao("A", ATAQUE);
        r.registrarAcao("B", ATAQUE);
        assertThrows(ForaDaRodadaException.class, () -> r.registrarAcao("A", ATAQUE));
        r.fecharPorTimeout(); // tardio: não resolve de novo
        assertEquals(1, resolvedor.chamadas.get());
    }

    @Test
    void tokenDeForaDaRodadaEhRejeitado() {
        RodadaSimultanea r = nova();
        assertThrows(ForaDaRodadaException.class, () -> r.registrarAcao("X", ATAQUE));
        assertFalse(r.estaFechada());
    }

    @Test
    void timeoutComUmaAcaoPreencheAOutraComAPolitica() throws Exception {
        RodadaSimultanea r = nova();
        r.registrarAcao("A", TROCA);
        r.fecharPorTimeout();

        assertTrue(r.estaFechada());
        assertEquals(1, resolvedor.chamadas.get());
        assertEquals(Map.of("A", TROCA, "B", PADRAO), resolvedor.ultimas.get());
        assertThrows(ForaDaRodadaException.class, () -> r.registrarAcao("B", ATAQUE));
    }

    @Test
    void estresseSegundaAcaoContraTimeout() throws Exception {
        final int repeticoes = 1000;
        int acaoVenceu = 0, timeoutVenceu = 0, violacoes = 0;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        long inicio = System.nanoTime();
        try {
            for (int i = 0; i < repeticoes; i++) {
                ResolvedorFalso res = new ResolvedorFalso();
                RodadaSimultanea r = new RodadaSimultanea(i, "A", "B", res, t -> PADRAO, x -> { });
                r.registrarAcao("A", ATAQUE);

                CountDownLatch pronto = new CountDownLatch(2);
                CountDownLatch largada = new CountDownLatch(1);
                var fAcao = pool.submit(() -> {
                    pronto.countDown();
                    largada.await();
                    try {
                        r.registrarAcao("B", TROCA);
                        return true;
                    } catch (ForaDaRodadaException e) {
                        return false;
                    }
                });
                var fTimeout = pool.submit(() -> {
                    pronto.countDown();
                    largada.await();
                    r.fecharPorTimeout();
                    return null;
                });
                assertTrue(pronto.await(5, TimeUnit.SECONDS));
                largada.countDown();
                boolean aceita = fAcao.get(5, TimeUnit.SECONDS);
                fTimeout.get(5, TimeUnit.SECONDS);

                // Ou B entrou na rodada com a ação dele, ou recebeu exceção e a política preencheu; sempre 1 resolução.
                Acao acaoB = res.ultimas.get().get("B");
                boolean coerente = res.chamadas.get() == 1 && r.estaFechada()
                        && (aceita ? TROCA.equals(acaoB) : PADRAO.equals(acaoB));
                if (!coerente) violacoes++;
                if (aceita) acaoVenceu++; else timeoutVenceu++;
            }
        } finally {
            pool.shutdownNow();
        }
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        System.out.printf("[ESTRESSE] repeticoes=%d | resolvedor chamado exatamente 1x em %d | "
                        + "segunda acao venceu=%d | timeout venceu (acao rejeitada)=%d | violacoes=%d | tempo=%d ms%n",
                repeticoes, repeticoes - violacoes, acaoVenceu, timeoutVenceu, violacoes, ms);
        assertEquals(0, violacoes);
    }
}
