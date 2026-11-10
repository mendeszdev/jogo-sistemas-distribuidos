package br.ucsal.sd.arena.servidor;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Varre as sessões e remove quem ficou sem heartbeat por mais que o tempo de tolerância. */
final class HeartbeatMonitor implements AutoCloseable {

    /** Tempo de tolerância oficial do documento de regras (DISCONNECT_TOLERANCE_SEC). */
    static final int DISCONNECT_TOLERANCE_SEC = 20;

    private static final Logger LOG = Logger.getLogger(HeartbeatMonitor.class.getName());

    private final Supplier<Collection<SessaoJogador>> sessoes;
    private final BiConsumer<SessaoJogador, String> aoExpirar;
    private final Duration tolerancia;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "heartbeat-monitor");
        t.setDaemon(true);
        return t;
    });

    HeartbeatMonitor(Supplier<Collection<SessaoJogador>> sessoes, BiConsumer<SessaoJogador, String> aoExpirar,
                     Duration tolerancia) {
        this.sessoes = sessoes;
        this.aoExpirar = aoExpirar;
        this.tolerancia = tolerancia;
    }

    void iniciar() {
        executor.scheduleWithFixedDelay(this::verificar, 1, 1, TimeUnit.SECONDS);
    }

    void verificar() {
        try {
            Instant limite = Instant.now().minus(tolerancia);
            for (SessaoJogador s : sessoes.get()) {
                if (s.getUltimoHeartbeat().isBefore(limite)) {
                    aoExpirar.accept(s, "sem heartbeat há mais de " + tolerancia.toSeconds() + "s");
                }
            }
        } catch (RuntimeException e) { // não pode morrer: o scheduler suprime execuções futuras após exceção
            LOG.log(Level.SEVERE, "Erro no monitor de heartbeat", e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}