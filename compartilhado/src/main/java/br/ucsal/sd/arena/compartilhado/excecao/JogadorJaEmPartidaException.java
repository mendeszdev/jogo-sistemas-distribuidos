package br.ucsal.sd.arena.compartilhado.excecao;

import java.io.Serial;

/** O jogador já está em uma partida (ou já está na fila) e não pode buscar outra. */
public class JogadorJaEmPartidaException extends JogoException {

    @Serial
    private static final long serialVersionUID = 1L;

    public JogadorJaEmPartidaException(String mensagem) {
        super(mensagem);
    }
}
