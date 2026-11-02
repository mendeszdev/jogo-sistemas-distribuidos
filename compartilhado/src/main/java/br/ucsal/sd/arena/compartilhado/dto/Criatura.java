package br.ucsal.sd.arena.compartilhado.dto;

import java.util.List;
import java.io.Serial;
import java.io.Serializable;

/** Criatura do pool de draft, com seus atributos base. */
public record Criatura(int id, String nome, Tipo tipo, int hpMax, int velocidade, List<Ataque> ataques) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public Criatura {
        ataques = List.copyOf(ataques);
    }
}
