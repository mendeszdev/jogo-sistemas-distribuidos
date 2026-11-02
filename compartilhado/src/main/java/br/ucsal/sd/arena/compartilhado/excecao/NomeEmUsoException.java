package br.ucsal.sd.arena.compartilhado.excecao;

import java.io.Serial;

/** O nome de jogador informado já está em uso por outra sessão ativa. */
public class NomeEmUsoException extends JogoException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NomeEmUsoException(String mensagem) {
        super(mensagem);
    }
}
