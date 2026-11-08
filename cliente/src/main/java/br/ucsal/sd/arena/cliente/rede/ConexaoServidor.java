package br.ucsal.sd.arena.cliente.rede;

import br.ucsal.sd.arena.compartilhado.excecao.JogoException;
import br.ucsal.sd.arena.compartilhado.excecao.NomeEmUsoException;
import br.ucsal.sd.arena.compartilhado.remoto.ServicoJogo;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Encapsula a conexão do cliente: lookup, exportação do callback, token e heartbeat. */
public class ConexaoServidor {

    /** Intervalo entre heartbeats, em segundos. */
    public static final int INTERVALO_HEARTBEAT_SEGUNDOS = 5;

    private static final Logger LOG = Logger.getLogger(ConexaoServidor.class.getName());

    private final String host;
    private final int porta;
    private final String ipLocal;

    private ServicoJogo servico;
    private ClienteCallbackImpl callback;
    private ScheduledExecutorService heartbeat;
    private volatile String token;

    /**
     * @param ipLocal IP do cliente alcançável pelo servidor; se nulo/vazio, o RMI escolhe sozinho
     *                (suficiente apenas quando servidor e cliente estão na mesma máquina).
     */
    public ConexaoServidor(String host, int porta, String ipLocal) {
        this.host = host;
        this.porta = porta;
        this.ipLocal = ipLocal;
    }

    public synchronized void conectar(String nome, OuvinteEventosJogo ouvinte)
            throws RemoteException, NotBoundException, NomeEmUsoException {
        if (token != null) {
            throw new IllegalStateException("Já conectado");
        }
        // Precisa vir ANTES de exportar o callback: o stub embute este endereço para o servidor chamar de volta.
        if (ipLocal != null && !ipLocal.isBlank()) {
            System.setProperty("java.rmi.server.hostname", ipLocal.strip());
        }
        Registry registry = LocateRegistry.getRegistry(host, porta);
        ServicoJogo s = (ServicoJogo) registry.lookup(ServicoJogo.NOME_NO_REGISTRY);

        ClienteCallbackImpl cb = new ClienteCallbackImpl(ouvinte);
        try {
            token = s.conectar(nome, cb);
        } catch (RemoteException | NomeEmUsoException | RuntimeException e) {
            UnicastRemoteObject.unexportObject(cb, true);
            throw e;
        }
        servico = s;
        callback = cb;
        iniciarHeartbeat();
    }

    private void iniciarHeartbeat() {
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-cliente");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(() -> {
            try {
                servico.heartbeat(token);
            } catch (RemoteException | JogoException e) {
                LOG.log(Level.WARNING, "Falha no heartbeat", e);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Erro inesperado no heartbeat", e);
            }
        }, INTERVALO_HEARTBEAT_SEGUNDOS, INTERVALO_HEARTBEAT_SEGUNDOS, TimeUnit.SECONDS);
    }

    public synchronized void desconectar() {
        if (token == null) {
            return;
        }
        if (heartbeat != null) {
            heartbeat.shutdownNow();
        }
        try {
            servico.desconectar(token);
        } catch (RemoteException | JogoException e) {
            LOG.log(Level.WARNING, "Falha ao desconectar do servidor", e);
        }
        try {
            UnicastRemoteObject.unexportObject(callback, true);
        } catch (RemoteException e) {
            LOG.log(Level.WARNING, "Falha ao desexportar callback", e);
        }
        token = null;
        servico = null;
        callback = null;
    }

    public ServicoJogo getServico() {
        return servico;
    }

    public String getToken() {
        return token;
    }
}
