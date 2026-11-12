package br.ucsal.sd.arena.compartilhado.remoto;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
import br.ucsal.sd.arena.compartilhado.excecao.AcaoInvalidaException;
import br.ucsal.sd.arena.compartilhado.excecao.CriaturaIndisponivelException;
import br.ucsal.sd.arena.compartilhado.excecao.FaseInvalidaException;
import br.ucsal.sd.arena.compartilhado.excecao.ForaDaRodadaException;
import br.ucsal.sd.arena.compartilhado.excecao.JogadorJaEmPartidaException;
import br.ucsal.sd.arena.compartilhado.excecao.LimiteDeEscolhasException;
import br.ucsal.sd.arena.compartilhado.excecao.NomeEmUsoException;
import br.ucsal.sd.arena.compartilhado.excecao.SessaoInvalidaException;
import br.ucsal.sd.arena.compartilhado.dto.JogadorOnline;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/** Serviço remoto exposto pelo servidor (registrado como "ArenaElementais"). */
public interface ServicoJogo extends Remote {

    /** Nome com o qual o serviço é registrado no RMI registry. */
    String NOME_NO_REGISTRY = "ArenaElementais";

    /**
     * Chamado ao entrar no jogo.
     *
     * @param nome     nome de exibição do jogador
     * @param callback objeto remoto do cliente para receber notificações do servidor
     * @return token de sessão, exigido em todas as demais chamadas
     * @throws NomeEmUsoException se já existe uma sessão ativa com esse nome
     */
    String conectar(String nome, ClienteCallback callback) throws RemoteException, NomeEmUsoException;

    /**
     * Chamado ao sair do jogo voluntariamente; encerra a sessão.
     *
     * @throws SessaoInvalidaException se o token não corresponde a uma sessão ativa
     */
    void desconectar(String token) throws RemoteException, SessaoInvalidaException;

    /**
     * Chamado para exibir o lobby.
     *
     * @return jogadores atualmente online, cada um com seu status
     * @throws SessaoInvalidaException se o token não corresponde a uma sessão ativa
     */
    List<JogadorOnline> listarJogadoresOnline(String token) throws RemoteException, SessaoInvalidaException;
    /**
     * Chamado ao clicar em "buscar partida"; coloca o jogador na fila de matchmaking.
     *
     * @throws SessaoInvalidaException     se o token não corresponde a uma sessão ativa
     * @throws JogadorJaEmPartidaException se o jogador já está em partida ou já está na fila
     */
    void buscarPartida(String token) throws RemoteException, SessaoInvalidaException, JogadorJaEmPartidaException;

    /**
     * Chamado para sair da fila de matchmaking; sem efeito se o jogador não estiver na fila.
     *
     * @throws SessaoInvalidaException se o token não corresponde a uma sessão ativa
     */
    void cancelarBusca(String token) throws RemoteException, SessaoInvalidaException;

    /**
     * Chamado durante o draft ao clicar em uma criatura; quem chegar primeiro leva.
     *
     * @param idCriatura id da criatura no pool
     * @throws SessaoInvalidaException       se o token não corresponde a uma sessão ativa
     * @throws FaseInvalidaException         se a partida do jogador não está na fase de draft
     * @throws CriaturaIndisponivelException se a criatura não existe ou já foi escolhida
     * @throws LimiteDeEscolhasException     se o jogador já tem o máximo de criaturas
     */
    void escolherCriatura(String token, int idCriatura) throws RemoteException, SessaoInvalidaException,
            FaseInvalidaException, CriaturaIndisponivelException, LimiteDeEscolhasException;

    /**
     * Chamado uma vez por rodada de batalha com a ação escolhida (atacar ou trocar).
     * A ação deve informar o número recebido em EstadoBatalha para impedir envios de rodadas antigas.
     *
     * @throws SessaoInvalidaException se o token não corresponde a uma sessão ativa
     * @throws FaseInvalidaException   se a partida do jogador não está na fase de batalha
     * @throws ForaDaRodadaException   se o jogador já enviou ação nesta rodada ou o tempo esgotou
     * @throws AcaoInvalidaException   se o índice do ataque/reserva é inválido ou a reserva está derrotada
     */
    void enviarAcao(String token, Acao acao) throws RemoteException, SessaoInvalidaException,
            FaseInvalidaException, ForaDaRodadaException, AcaoInvalidaException;

    /**
     * Chamado periodicamente pelo cliente para provar que continua vivo.
     *
     * @throws SessaoInvalidaException se o token não corresponde a uma sessão ativa (ex.: removida por timeout)
     */
    void heartbeat(String token) throws RemoteException, SessaoInvalidaException;
}
