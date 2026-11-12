package br.ucsal.sd.arena.servidor.batalha;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Uma thread de temporização por sala, reutilizada entre rodadas. */
public final class TimerTurno implements AutoCloseable {
    public static final Duration DURACAO_PADRAO = Duration.ofSeconds(30);
    private final ScheduledThreadPoolExecutor executor;

    public TimerTurno(String idSala) {
        Objects.requireNonNull(idSala, "idSala");
        executor = new ScheduledThreadPoolExecutor(1, tarefa -> {
            Thread thread = new Thread(tarefa, "timer-turno-" + idSala);
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    ScheduledFuture<?> agendar(Runnable tarefa, Duration duracao) {
        long nanos = Objects.requireNonNull(duracao, "duracao").toNanos();
        if (nanos <= 0) throw new IllegalArgumentException("A duração do turno deve ser positiva");
        return executor.schedule(tarefa, nanos, TimeUnit.NANOSECONDS);
    }

    /** Cancela os prazos pendentes ao encerrar a sala, sem interromper uma resolução em curso. */
    @Override
    public void close() {
        executor.shutdown();
    }
}
