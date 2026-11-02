package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.remoto.ClienteCallback;

import java.rmi.RemoteException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Despacha notificações aos clientes em threads próprias, para que um cliente lento ou
 * morto não bloqueie a thread da chamada RMI nem o loop do jogo.
 */
public final class NotificadorCallbacks {

    /** Envio de uma notificação a um callback; pode lançar RemoteException. */
    @FunctionalInterface
    public interface Envio {
        void enviar(ClienteCallback callback) throws RemoteException;
    }

    private static final Logger LOG = Logger.getLogger(NotificadorCallbacks.class.getName());

    private final ExecutorService executor = Executors.newCachedThreadPool(new FabricaThreads());

    /** Agenda o envio e retorna imediatamente. Falhas só são registradas em log (remoção do jogador: card 22). */
    public void notificar(SessaoJogador sessao, Envio envio) {
        try {
            executor.execute(() -> {
                try {
                    envio.enviar(sessao.getCallback());
                } catch (RemoteException e) {
                    LOG.log(Level.WARNING, "Falha ao notificar callback de " + sessao.getNome(), e);
                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "Erro inesperado ao notificar " + sessao.getNome(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            LOG.fine("Notificador encerrado; envio descartado para " + sessao.getNome());
        }
    }

    public void encerrar() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class FabricaThreads implements ThreadFactory {
        private final AtomicInteger contador = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "notificador-callback-" + contador.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
