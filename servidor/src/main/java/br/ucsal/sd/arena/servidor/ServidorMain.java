package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.remoto.ServicoJogo;

import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ponto de entrada do servidor. Uso: {@code ServidorMain [porta] [hostname]}, ou as System
 * properties {@code arena.porta} e {@code java.rmi.server.hostname}.
 */
public final class ServidorMain {

    private static final Logger LOG = Logger.getLogger(ServidorMain.class.getName());
    private static final int PORTA_PADRAO = 1099;

    private ServidorMain() {
    }

    public static void main(String[] args) throws Exception {
        int porta = lerPorta(args);
        String hostname = lerHostname(args);

        // Precisa vir ANTES de exportar qualquer objeto: o stub embute esse endereço.
        if (hostname != null) {
            System.setProperty("java.rmi.server.hostname", hostname);
        }
        LOG.info("Iniciando servidor (porta=" + porta + ", hostname="
                + System.getProperty("java.rmi.server.hostname", "<automático>") + ")");

        NotificadorCallbacks notificador = new NotificadorCallbacks();
        ServicoJogoImpl servico = new ServicoJogoImpl(notificador);
        Registry registry = LocateRegistry.createRegistry(porta);
        registry.rebind(ServicoJogo.NOME_NO_REGISTRY, servico);
        LOG.info("Registry criado na porta " + porta);
        LOG.info("Serviço '" + ServicoJogo.NOME_NO_REGISTRY + "' registrado; aguardando clientes");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Encerrando servidor");
            try {
                registry.unbind(ServicoJogo.NOME_NO_REGISTRY);
            } catch (RemoteException | NotBoundException e) {
                LOG.log(Level.WARNING, "Falha no unbind", e);
            }
            try {
                UnicastRemoteObject.unexportObject(servico, true);
                UnicastRemoteObject.unexportObject(registry, true);
            } catch (NoSuchObjectException e) {
                LOG.log(Level.WARNING, "Falha no unexport", e);
            }
            notificador.encerrar();
        }, "shutdown-servidor"));
    }

    private static int lerPorta(String[] args) {
        String valor = args.length > 0 ? args[0] : System.getProperty("arena.porta");
        if (valor == null || valor.isBlank()) {
            return PORTA_PADRAO;
        }
        try {
            return Integer.parseInt(valor.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Porta inválida: " + valor, e);
        }
    }

    private static String lerHostname(String[] args) {
        String valor = args.length > 1 ? args[1] : System.getProperty("java.rmi.server.hostname");
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
