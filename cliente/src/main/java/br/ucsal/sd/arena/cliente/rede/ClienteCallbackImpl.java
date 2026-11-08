package br.ucsal.sd.arena.cliente.rede;

import br.ucsal.sd.arena.compartilhado.dto.EstadoBatalha;
import br.ucsal.sd.arena.compartilhado.dto.EstadoDraft;
import br.ucsal.sd.arena.compartilhado.dto.PartidaInfo;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoPartida;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoRodada;
import br.ucsal.sd.arena.compartilhado.remoto.ClienteCallback;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Objects;

/** Objeto remoto do cliente: apenas repassa cada evento ao {@link OuvinteEventosJogo}. Sem JavaFX. */
public class ClienteCallbackImpl extends UnicastRemoteObject implements ClienteCallback {

    private final transient OuvinteEventosJogo ouvinte;

    public ClienteCallbackImpl(OuvinteEventosJogo ouvinte) throws RemoteException {
        super();
        this.ouvinte = Objects.requireNonNull(ouvinte, "ouvinte");
    }

    @Override
    public void partidaEncontrada(PartidaInfo info) throws RemoteException {
        ouvinte.partidaEncontrada(info);
    }

    @Override
    public void draftAtualizado(EstadoDraft estado) throws RemoteException {
        ouvinte.draftAtualizado(estado);
    }

    @Override
    public void batalhaIniciada(EstadoBatalha estado) throws RemoteException {
        ouvinte.batalhaIniciada(estado);
    }

    @Override
    public void rodadaResolvida(ResultadoRodada resultado) throws RemoteException {
        ouvinte.rodadaResolvida(resultado);
    }

    @Override
    public void partidaEncerrada(ResultadoPartida resultado) throws RemoteException {
        ouvinte.partidaEncerrada(resultado);
    }

    @Override
    public void adversarioDesconectado(int segundosTolerancia) throws RemoteException {
        ouvinte.adversarioDesconectado(segundosTolerancia);
    }

    @Override
    public void jogadoresOnlineAtualizados(List<String> nomes) throws RemoteException {
        ouvinte.jogadoresOnlineAtualizados(nomes);
    }
}
