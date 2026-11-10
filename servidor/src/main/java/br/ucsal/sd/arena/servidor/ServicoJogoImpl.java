package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
import br.ucsal.sd.arena.compartilhado.dto.JogadorOnline;
import br.ucsal.sd.arena.compartilhado.dto.StatusJogador;
import br.ucsal.sd.arena.compartilhado.excecao.AcaoInvalidaException;
import br.ucsal.sd.arena.compartilhado.excecao.CriaturaIndisponivelException;
import br.ucsal.sd.arena.compartilhado.excecao.FaseInvalidaException;
import br.ucsal.sd.arena.compartilhado.excecao.ForaDaRodadaException;
import br.ucsal.sd.arena.compartilhado.excecao.JogadorJaEmPartidaException;
import br.ucsal.sd.arena.compartilhado.excecao.LimiteDeEscolhasException;
import br.ucsal.sd.arena.compartilhado.excecao.NomeEmUsoException;
import br.ucsal.sd.arena.compartilhado.excecao.SessaoInvalidaException;
import br.ucsal.sd.arena.compartilhado.remoto.ClienteCallback;
import br.ucsal.sd.arena.compartilhado.remoto.ServicoJogo;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/** Implementação do serviço remoto. Lobby e matchmaking (card 14); draft/batalha pertencem a outros cards. */
public class ServicoJogoImpl extends UnicastRemoteObject implements ServicoJogo {

    private static final Logger LOG = Logger.getLogger(ServicoJogoImpl.class.getName());

    private final ConcurrentHashMap<String, SessaoJogador> sessoes = new ConcurrentHashMap<>();
    /** Nome (minúsculas) -> token; o putIfAbsent atômico é o que garante a unicidade do nome. */
    private final ConcurrentHashMap<String, String> tokenPorNome = new ConcurrentHashMap<>();
    private final NotificadorCallbacks notificador;

    /**
     * Protege a fila de matchmaking, o pareamento e a montagem + envio da lista do lobby. As notificações só são
     * ENFILEIRADAS sob este lock (o envio de rede acontece em outras threads), então ele nunca espera um cliente lento.
     */
    private final ReentrantLock lobbyLock = new ReentrantLock();
    private final Deque<SessaoJogador> fila = new ArrayDeque<>(); // guardado por lobbyLock

    public ServicoJogoImpl(NotificadorCallbacks notificador) throws RemoteException {
        super();
        this.notificador = notificador;
        // card 22: callback que falha (cliente morto) derruba a sessão
        notificador.setAoFalhar(s -> removerSessao(s, "falha ao notificar o callback"));
    }

    @Override
    public String conectar(String nome, ClienteCallback callback) throws RemoteException, NomeEmUsoException {
        if (nome == null || nome.isBlank() || callback == null) {
            throw new IllegalArgumentException("nome e callback são obrigatórios");
        }
        String nomeLimpo = nome.strip();
        String token = UUID.randomUUID().toString();

        if (tokenPorNome.putIfAbsent(nomeLimpo.toLowerCase(Locale.ROOT), token) != null) {
            throw new NomeEmUsoException("Nome já em uso: " + nomeLimpo);
        }
        SessaoJogador sessao = new SessaoJogador(nomeLimpo, token, callback);
        lobbyLock.lock();
        try {
            sessoes.put(token, sessao);
            notificarLobby();
        } finally {
            lobbyLock.unlock();
        }
        LOG.info("Jogador conectado: " + nomeLimpo);
        return token;
    }

    @Override
    public void desconectar(String token) throws RemoteException, SessaoInvalidaException {
        removerSessao(exigirSessao(token), "saída voluntária");
    }

    @Override
    public List<JogadorOnline> listarJogadoresOnline(String token) throws RemoteException, SessaoInvalidaException {
        exigirSessao(token);
        lobbyLock.lock();
        try {
            return jogadoresOnline();
        } finally {
            lobbyLock.unlock();
        }
    }

    @Override
    public void heartbeat(String token) throws RemoteException, SessaoInvalidaException {
        exigirSessao(token).registrarHeartbeat();
    }

    @Override
    public void buscarPartida(String token) throws RemoteException, SessaoInvalidaException,
            JogadorJaEmPartidaException {
        SessaoJogador sessao = exigirSessao(token);
        lobbyLock.lock();
        try {
            if (!sessao.trocarStatus(StatusJogador.LIVRE, StatusJogador.EM_FILA)) {
                throw new JogadorJaEmPartidaException("Jogador já está na fila ou em partida");
            }
            fila.addLast(sessao);
            parear();
            notificarLobby();
        } finally {
            lobbyLock.unlock();
        }
    }

    @Override
    public void cancelarBusca(String token) throws RemoteException, SessaoInvalidaException {
        SessaoJogador sessao = exigirSessao(token);
        lobbyLock.lock();
        try {
            if (sessao.trocarStatus(StatusJogador.EM_FILA, StatusJogador.LIVRE)) {
                fila.remove(sessao);
                notificarLobby();
            } // fora da fila: sem efeito
        } finally {
            lobbyLock.unlock();
        }
    }

    @Override
    public void escolherCriatura(String token, int idCriatura) throws RemoteException, SessaoInvalidaException,
            FaseInvalidaException, CriaturaIndisponivelException, LimiteDeEscolhasException {
        SessaoJogador sessao = exigirSessao(token);
        Partida partida = sessao.getPartida();
        if (partida == null) {
            throw new FaseInvalidaException("Jogador não está em uma partida");
        }
        partida.escolherCriatura(sessao, idCriatura);
    }

    @Override
    public void enviarAcao(String token, Acao acao) throws RemoteException, SessaoInvalidaException,
            FaseInvalidaException, ForaDaRodadaException, AcaoInvalidaException {
        // TODO card 19: com a Partida (cards 14/15), obter a RodadaSimultanea corrente da partida do token e
        //  chamar rodada.registrarAcao(token, acao); os callbacks saem fora do lock (ver RodadaSimultanea).
        throw new UnsupportedOperationException("TODO card 19");
    }

    /**
     * Caminho ÚNICO de saída de um jogador (saída voluntária, heartbeat expirado, falha de callback).
     * Idempotente: se a sessão já foi removida, não faz nada.
     */
    void removerSessao(SessaoJogador sessao, String motivo) {
        lobbyLock.lock();
        try {
            if (!sessoes.remove(sessao.getToken(), sessao)) {
                return;
            }
            tokenPorNome.remove(sessao.getNome().toLowerCase(Locale.ROOT), sessao.getToken());
            fila.remove(sessao);
            // --- card 22 (W.O.): só depois de existir Partida.encerrarPorWO ---
            Partida partida = sessao.getPartida();
            if (partida != null) { // W.O.: o adversário vence e volta a ficar livre
                SessaoJogador adversario = partida.getAdversario(sessao);
                if (partida.encerrarPorWO(sessao)) {
                    adversario.setPartida(null);
                    adversario.trocarStatus(StatusJogador.EM_PARTIDA, StatusJogador.LIVRE);
                }
            }
            notificarLobby();
        } finally {
            lobbyLock.unlock();
        }
        notificador.liberar(sessao);
        LOG.info("Jogador removido: " + sessao.getNome() + " (" + motivo + ")");
    }

    /** Sessões ativas (cópia), para o monitor de heartbeat. */
    Collection<SessaoJogador> sessoesAtivas() {
        return new ArrayList<>(sessoes.values());
    }

    private SessaoJogador exigirSessao(String token) throws SessaoInvalidaException {
        SessaoJogador sessao = token == null ? null : sessoes.get(token);
        if (sessao == null) {
            throw new SessaoInvalidaException("Sessão inválida");
        }
        return sessao;
    }

    /** Forma duplas FIFO enquanto houver 2 na fila. Chamar com o lobbyLock. */
    private void parear() {
        while (fila.size() >= 2) {
            SessaoJogador a = fila.pollFirst();
            SessaoJogador b = fila.pollFirst();
            boolean okA = a.trocarStatus(StatusJogador.EM_FILA, StatusJogador.EM_PARTIDA);
            boolean okB = b.trocarStatus(StatusJogador.EM_FILA, StatusJogador.EM_PARTIDA);
            if (okA && okB) {
                Partida partida = new Partida(UUID.randomUUID().toString(), a, b, notificador);
                a.setPartida(partida);
                b.setPartida(partida);
                partida.iniciar();
                LOG.info("Partida " + partida.getId() + ": " + a.getNome() + " x " + b.getNome());
            } else { // defensivo: status mudou fora do esperado; devolve quem ainda era válido à frente da fila
                if (okB) {
                    b.trocarStatus(StatusJogador.EM_PARTIDA, StatusJogador.EM_FILA);
                    fila.addFirst(b);
                }
                if (okA) {
                    a.trocarStatus(StatusJogador.EM_PARTIDA, StatusJogador.EM_FILA);
                    fila.addFirst(a);
                }
            }
        }
    }

    /** Monta a lista e ENFILEIRA a notificação para todos. Chamar com o lobbyLock (garante a ordem dos envios). */
    private void notificarLobby() {
        assert lobbyLock.isHeldByCurrentThread();
        List<JogadorOnline> jogadores = jogadoresOnline();
        for (SessaoJogador s : sessoes.values()) {
            notificador.notificar(s, cb -> cb.jogadoresOnlineAtualizados(jogadores));
        }
    }

    private List<JogadorOnline> jogadoresOnline() {
        List<JogadorOnline> jogadores = new ArrayList<>();
        for (SessaoJogador s : sessoes.values()) {
            jogadores.add(new JogadorOnline(s.getNome(), s.getStatus()));
        }
        jogadores.sort((x, y) -> String.CASE_INSENSITIVE_ORDER.compare(x.nome(), y.nome()));
        return jogadores;
    }
}
