package br.ucsal.sd.arena.compartilhado.remoto;

import br.ucsal.sd.arena.compartilhado.dto.*;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/** Objeto remoto do cliente, chamado pelo servidor para empurrar eventos (RMI callback). */
public interface ClienteCallback extends Remote {

    /** Chamado quando o matchmaking pareia o jogador com um adversário; marca o início do draft. */
    void partidaEncontrada(PartidaInfo info) throws RemoteException;

    /** Chamado a cada mudança no draft (escolha de qualquer jogador) e a cada tick do timer. */
    void draftAtualizado(EstadoDraft estado) throws RemoteException;

    /** Chamado quando o draft termina e a batalha começa, com o estado da rodada 1. */
    void batalhaIniciada(EstadoBatalha estado) throws RemoteException;

    /** Chamado ao fim de cada rodada, com os eventos ocorridos e o novo estado. */
    void rodadaResolvida(ResultadoRodada resultado) throws RemoteException;

    /** Chamado quando a partida acaba (vitória ou W.O.). */
    void partidaEncerrada(ResultadoPartida resultado) throws RemoteException;

    /**
     * Chamado quando o adversário perde a conexão; se ele não voltar, vence-se por W.O.
     *
     * @param segundosTolerancia tempo restante até a declaração do W.O.
     */
    void adversarioDesconectado(int segundosTolerancia) throws RemoteException;

    /** Chamado quando entra ou sai um jogador, para atualizar a lista do lobby. */
    void jogadoresOnlineAtualizados(List<JogadorOnline> jogadores) throws RemoteException;
}
