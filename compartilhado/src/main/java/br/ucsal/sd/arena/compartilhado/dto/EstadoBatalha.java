package br.ucsal.sd.arena.compartilhado.dto;

import java.util.List;

import java.io.Serial;
import java.io.Serializable;

/** Visão da batalha para um jogador. Das reservas do adversário só a quantidade é exposta, nunca quais são. */
public record EstadoBatalha(int numeroRodada, EstadoCriaturaEmBatalha minhaAtiva, List<EstadoCriaturaEmBatalha> minhasReservas, EstadoCriaturaEmBatalha ativaAdversario, int reservasRestantesAdversario, int segundosRestantes) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public EstadoBatalha {
        minhasReservas = List.copyOf(minhasReservas);
    }
}
