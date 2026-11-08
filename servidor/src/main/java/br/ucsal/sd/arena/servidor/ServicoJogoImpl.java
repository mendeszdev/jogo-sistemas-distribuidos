package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Implementação do serviço remoto. Lobby/draft/batalha pertencem a outros cards. */
public class ServicoJogoImpl extends UnicastRemoteObject implements ServicoJogo {

    private static final Logger LOG = Logger.getLogger(ServicoJogoImpl.class.getName());

    private final ConcurrentHashMap<String, SessaoJogador> sessoes = new ConcurrentHashMap<>();
    /** Nome (minúsculas) -> token; o putIfAbsent atômico é o que garante a unicidade do nome. */
    private final ConcurrentHashMap<String, String> tokenPorNome = new ConcurrentHashMap<>();
    private final NotificadorCallbacks notificador;

    public ServicoJogoImpl(NotificadorCallbacks notificador) throws RemoteException {
        super();
        this.notificador = notificador;
    }

    @Override
    public String conectar(String nome, ClienteCallback callback) throws RemoteException, NomeEmUsoException {
        if (nome == null || nome.isBlank() || callback == null) {
            throw new IllegalArgumentException("nome e callback são obrigatórios");
        }
        String nomeLimpo = nome.strip();
        String token = UUID.randomUUID().toString();

        if (tokenPorNome.putIfAbsent(nomeLimpo.toLowerCase(), token) != null) {
            throw new NomeEmUsoException("Nome já em uso: " + nomeLimpo);
        }
        sessoes.put(token, new SessaoJogador(nomeLimpo, token, callback));
        LOG.info("Jogador conectado: " + nomeLimpo);
        notificarLobby();
        return token;
    }

    @Override
    public void desconectar(String token) throws RemoteException, SessaoInvalidaException {
        SessaoJogador sessao = token == null ? null : sessoes.remove(token);
        if (sessao == null) {
            throw new SessaoInvalidaException("Sessão inválida");
        }
        tokenPorNome.remove(sessao.getNome().toLowerCase(), token);
        notificador.liberar(sessao);
        LOG.info("Jogador desconectado: " + sessao.getNome());
        notificarLobby();
    }

    @Override
    public List<String> listarJogadoresOnline(String token) throws RemoteException, SessaoInvalidaException {
        exigirSessao(token);
        return nomesOnline();
    }

    @Override
    public void heartbeat(String token) throws RemoteException, SessaoInvalidaException {
        exigirSessao(token).registrarHeartbeat();
    }

    @Override
    public void buscarPartida(String token) throws RemoteException, SessaoInvalidaException,
            JogadorJaEmPartidaException {
        throw new UnsupportedOperationException("TODO card 14");
    }

    @Override
    public void cancelarBusca(String token) throws RemoteException, SessaoInvalidaException {
        throw new UnsupportedOperationException("TODO card 14");
    }

    @Override
    public void escolherCriatura(String token, int idCriatura) throws RemoteException, SessaoInvalidaException,
            FaseInvalidaException, CriaturaIndisponivelException, LimiteDeEscolhasException {
        throw new UnsupportedOperationException("TODO card 15");
    }

    @Override
    public void enviarAcao(String token, Acao acao) throws RemoteException, SessaoInvalidaException,
            FaseInvalidaException, ForaDaRodadaException, AcaoInvalidaException {
        // TODO card 19: com a Partida (cards 14/15), obter a RodadaSimultanea corrente da partida do token e
        //  chamar rodada.registrarAcao(token, acao); os callbacks saem fora do lock (ver RodadaSimultanea).
        throw new UnsupportedOperationException("TODO card 19");
    }

    private SessaoJogador exigirSessao(String token) throws SessaoInvalidaException {
        SessaoJogador sessao = token == null ? null : sessoes.get(token);
        if (sessao == null) {
            throw new SessaoInvalidaException("Sessão inválida");
        }
        return sessao;
    }

    private List<String> nomesOnline() {
        List<String> nomes = new ArrayList<>();
        for (SessaoJogador s : sessoes.values()) {
            nomes.add(s.getNome());
        }
        nomes.sort(String.CASE_INSENSITIVE_ORDER);
        return nomes;
    }

    private void notificarLobby() {
        List<String> nomes = nomesOnline();
        for (SessaoJogador s : sessoes.values()) {
            notificador.notificar(s, cb -> cb.jogadoresOnlineAtualizados(nomes));
        }
    }
}
