package br.ucsal.sd.arena.compartilhado.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** Resultado final da partida. */
public record ResultadoPartida(String vencedor, boolean porWO, int rodadasJogadas,
                                List<EstadoCriaturaEmBatalha> meuTime, List<EstadoCriaturaEmBatalha> timeAdversario) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResultadoPartida{
        meuTime = List.copyOf(meuTime);
        timeAdversario = List.copyOf(timeAdversario);
    }
}
