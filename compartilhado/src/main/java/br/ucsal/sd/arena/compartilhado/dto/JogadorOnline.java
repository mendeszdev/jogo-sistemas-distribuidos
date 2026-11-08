package br.ucsal.sd.arena.compartilhado.dto;

import java.io.Serial;
import java.io.Serializable;

public record JogadorOnline(String nome, StatusJogador statusJogador) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

}
