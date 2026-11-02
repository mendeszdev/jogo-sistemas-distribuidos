package br.ucsal.sd.arena.compartilhado.excecao;

import java.io.Serial;

/** A operação não é permitida na fase atual da partida (ex.: escolher criatura durante a batalha). */
public class FaseInvalidaException extends JogoException {

    @Serial
    private static final long serialVersionUID = 1L;

    public FaseInvalidaException(String mensagem) {
        super(mensagem);
    }
}
