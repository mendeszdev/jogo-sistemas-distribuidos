package br.ucsal.sd.arena.compartilhado.excecao;

import java.io.Serial;

/** A criatura escolhida não existe no pool ou já foi pega por um dos jogadores. */
public class CriaturaIndisponivelException extends JogoException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CriaturaIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
