package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
import br.ucsal.sd.arena.compartilhado.dto.FasePartida;
import br.ucsal.sd.arena.compartilhado.dto.PartidaInfo;
import br.ucsal.sd.arena.compartilhado.excecao.AcaoInvalidaException;
import br.ucsal.sd.arena.compartilhado.excecao.CriaturaIndisponivelException;
import br.ucsal.sd.arena.compartilhado.excecao.FaseInvalidaException;
import br.ucsal.sd.arena.compartilhado.excecao.ForaDaRodadaException;
import br.ucsal.sd.arena.compartilhado.excecao.LimiteDeEscolhasException;

import java.util.Objects;

public final class Partida {

    private final String id;
    private final SessaoJogador jogadorA;
    private final SessaoJogador jogadorB;
    private final NotificadorCallbacks notificador;
    private volatile FasePartida fase = FasePartida.DRAFT;

    public Partida(String id, SessaoJogador jogadorA, SessaoJogador jogadorB, NotificadorCallbacks notificador) {
        this.id = Objects.requireNonNull(id);
        this.jogadorA = Objects.requireNonNull(jogadorA);
        this.jogadorB = Objects.requireNonNull(jogadorB);
        this.notificador = Objects.requireNonNull(notificador);
    }

    public String getId() {
        return id;
    }

    public SessaoJogador getJogadorA() {
        return jogadorA;
    }

    public SessaoJogador getJogadorB() {
        return jogadorB;
    }

    public FasePartida getFase() {
        return fase;
    }

    public boolean contem(SessaoJogador jogador) {
        return jogador == jogadorA || jogador == jogadorB;
    }

    public SessaoJogador getAdversario(SessaoJogador jogador) {
        if (jogador == jogadorA) {
            return jogadorB;
        }
        if (jogador == jogadorB) {
            return jogadorA;
        }
        throw new IllegalArgumentException("Jogador não pertence à partida " + id);
    }

    public void iniciar() {
        notificador.notificar(jogadorA, cb -> cb.partidaEncontrada(new PartidaInfo(id, jogadorB.getNome())));
        notificador.notificar(jogadorB, cb -> cb.partidaEncontrada(new PartidaInfo(id, jogadorA.getNome())));
    }

    public void escolherCriatura(SessaoJogador jogador, int idCriatura) throws FaseInvalidaException,
            CriaturaIndisponivelException, LimiteDeEscolhasException {
        throw new UnsupportedOperationException("TODO card 15");
    }

    public void enviarAcao(SessaoJogador jogador, Acao acao) throws FaseInvalidaException,
            ForaDaRodadaException, AcaoInvalidaException {
        throw new UnsupportedOperationException("TODO card 19");
    }
}