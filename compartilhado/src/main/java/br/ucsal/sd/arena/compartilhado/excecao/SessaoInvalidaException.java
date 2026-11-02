package br.ucsal.sd.arena.compartilhado.excecao;

import java.io.Serial;

/** O token de sessão é desconhecido, expirou ou foi encerrado. */
public class SessaoInvalidaException extends JogoException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SessaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
