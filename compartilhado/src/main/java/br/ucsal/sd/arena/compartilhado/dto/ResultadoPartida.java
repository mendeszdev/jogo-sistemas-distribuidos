package br.ucsal.sd.arena.compartilhado.dto;

import java.io.Serial;
import java.io.Serializable;

/** Resultado final da partida. */
public record ResultadoPartida(String vencedor, boolean porWO, int rodadasJogadas) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
