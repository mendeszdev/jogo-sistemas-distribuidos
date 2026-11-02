package br.ucsal.sd.arena.compartilhado.dto;

import java.io.Serial;
import java.io.Serializable;

/** Ação de uma rodada; indice é o do ataque (ATACAR) ou da criatura reserva (TROCAR). */
public record Acao(TipoAcao tipo, int indice) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
