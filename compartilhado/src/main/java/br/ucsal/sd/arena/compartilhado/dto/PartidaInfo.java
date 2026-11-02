package br.ucsal.sd.arena.compartilhado.dto;

import java.io.Serial;
import java.io.Serializable;

/** Dados enviados quando uma partida é encontrada. */
public record PartidaInfo(String idPartida, String nomeAdversario) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
