package br.ucsal.sd.arena.compartilhado.dto;

import java.util.List;

import java.io.Serial;
import java.io.Serializable;

/** Resultado de uma rodada: eventos em ordem de ocorrência e o estado resultante. */
public record ResultadoRodada(int numeroRodada, List<String> eventos, EstadoBatalha estadoAtual) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResultadoRodada {
        eventos = List.copyOf(eventos);
    }
}
