package br.ucsal.sd.arena.compartilhado.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import static java.awt.SystemColor.info;

public record EstadoPartida(PartidaInfo partida, FasePartida fase, EstadoDraft draft, EstadoBatalha batalha) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public EstadoPartida {
        Objects.requireNonNull(partida, "partida");
        Objects.requireNonNull(fase, "fase");
        if(fase == FasePartida.DRAFT) {
            Objects.requireNonNull(draft, "draft obrigatóriona frase DRAFT");
        }

        if(fase == FasePartida.BATALHA) {
            Objects.requireNonNull(batalha, "batalha obrigatória na fase BATALHA");
        }

    }


}
