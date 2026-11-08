package br.ucsal.sd.arena.servidor;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fila serial sobre um executor compartilhado: as tarefas rodam uma por vez e na ordem de
 * submissão, sem thread fixa (só ocupa uma thread do executor enquanto houver tarefa pendente).
 * Não usa lock: a exclusão mútua vem de um flag atômico, e as tarefas rodam fora de qualquer lock.
 */
final class FilaSerial {

    private static final Logger LOG = Logger.getLogger(FilaSerial.class.getName());

    private final Queue<Runnable> pendentes = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean drenando = new AtomicBoolean();
    private volatile boolean encerrada;

    /** Enfileira a tarefa; descartada se a fila já foi encerrada. */
    void submeter(Executor executor, Runnable tarefa) {
        if (encerrada) {
            return;
        }
        pendentes.add(tarefa);
        agendarDreno(executor);
    }

    /** Descarta o que estiver pendente e rejeita novas tarefas (a tarefa em curso termina). */
    void encerrar() {
        encerrada = true;
        pendentes.clear();
    }

    private void agendarDreno(Executor executor) {
        if (!drenando.compareAndSet(false, true)) {
            return; // já há um dreno ativo; ele vai pegar a nova tarefa
        }
        try {
            executor.execute(() -> drenar(executor));
        } catch (RejectedExecutionException e) {
            drenando.set(false);
            pendentes.clear();
            throw e;
        }
    }

    private void drenar(Executor executor) {
        try {
            Runnable t;
            while (!encerrada && (t = pendentes.poll()) != null) {
                try {
                    t.run();
                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "Erro inesperado em tarefa da fila", e);
                }
            }
        } finally {
            drenando.set(false);
        }
        // Tarefa chegou entre o último poll e o reset do flag: reagenda para não ficar presa.
        if (!encerrada && !pendentes.isEmpty()) {
            try {
                agendarDreno(executor);
            } catch (RejectedExecutionException e) {
                LOG.fine("Executor encerrado; tarefas pendentes descartadas");
            }
        }
    }
}
