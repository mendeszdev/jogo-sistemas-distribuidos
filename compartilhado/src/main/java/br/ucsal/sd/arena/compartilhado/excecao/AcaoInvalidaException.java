package br.ucsal.sd.arena.compartilhado.excecao;

import java.io.Serial;

/** A ação enviada é inválida (índice inexistente, troca para criatura derrotada, etc.). */
public class AcaoInvalidaException extends JogoException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AcaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
