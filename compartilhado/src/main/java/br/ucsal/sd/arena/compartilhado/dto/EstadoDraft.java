package br.ucsal.sd.arena.compartilhado.dto;

import java.util.List;
import java.util.Set;

import java.io.Serial;
import java.io.Serializable;

/** Visão do draft para um jogador (ids das criaturas já escolhidas por cada lado). */
public record EstadoDraft(List<Criatura> pool, List<Integer> minhasEscolhas, Set<Integer> escolhasAdversario, int segundosRestantes) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public EstadoDraft {
        pool = List.copyOf(pool);
        minhasEscolhas = List.copyOf(minhasEscolhas);
        escolhasAdversario = Set.copyOf(escolhasAdversario);
    }
}
