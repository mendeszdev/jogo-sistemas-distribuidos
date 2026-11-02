package br.ucsal.sd.arena.compartilhado.excecao;

import java.io.Serial;

/** O jogador já escolheu o número máximo de criaturas no draft. */
public class LimiteDeEscolhasException extends JogoException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LimiteDeEscolhasException(String mensagem) {
        super(mensagem);
    }
}
