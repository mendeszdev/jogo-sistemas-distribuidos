package br.ucsal.sd.arena.compartilhado.excecao;

import java.io.Serial;

/** A ação chegou fora da janela da rodada (já enviada ou o tempo esgotou). */
public class ForaDaRodadaException extends JogoException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ForaDaRodadaException(String mensagem) {
        super(mensagem);
    }
}
