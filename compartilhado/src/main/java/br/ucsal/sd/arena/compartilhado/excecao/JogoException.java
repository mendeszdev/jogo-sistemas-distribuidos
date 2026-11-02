package br.ucsal.sd.arena.compartilhado.excecao;

import java.io.Serial;

/** Raiz das exceções de regra de negócio do jogo; checked para obrigar o chamador a tratá-las. */
public class JogoException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public JogoException(String mensagem) {
        super(mensagem);
    }

    public JogoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
