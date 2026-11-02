package br.ucsal.sd.arena.compartilhado.dto;

import java.io.Serial;
import java.io.Serializable;

/** Ataque disponível para uma criatura. */
public record Ataque(String nome, Tipo tipo, int poder) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
