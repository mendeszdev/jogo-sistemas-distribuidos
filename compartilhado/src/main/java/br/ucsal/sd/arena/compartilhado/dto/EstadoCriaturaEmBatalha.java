package br.ucsal.sd.arena.compartilhado.dto;

import java.io.Serial;
import java.io.Serializable;

/** HP atual de uma criatura em batalha. */
public record EstadoCriaturaEmBatalha(int idCriatura, int hpAtual) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public boolean derrubada(){
        return hpAtual <= 0;
    }
}
