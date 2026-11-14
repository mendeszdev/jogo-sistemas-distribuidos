package br.ucsal.sd.arena.compartilhado.dto;

import java.io.Serial;
import java.io.Serializable;

/** Ação de uma rodada; indice é o do ataque (ATACAR) ou da criatura reserva (TROCAR). */
public record Acao(TipoAcao tipo, int indice, int numeroRodada) implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    /** Ações locais do motor e do fallback não precisam identificar uma requisição remota. */
    public Acao(TipoAcao tipo, int indice) {
        this(tipo, indice, 0);
    }
}
