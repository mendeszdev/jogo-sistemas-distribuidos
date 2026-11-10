package br.ucsal.sd.arena.cliente.ui;

import javafx.scene.Parent;

/** Aplica o stylesheet com a paleta do jogo. */
final class Estilo {

    private static final String CSS = Estilo.class.getResource("/br/ucsal/sd/arena/cliente/arena.css").toExternalForm();

    private Estilo() {
    }

    static void aplicar(Parent raiz) {
        raiz.getStylesheets().add(CSS);
    }
}
