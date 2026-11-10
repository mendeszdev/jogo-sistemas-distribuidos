package br.ucsal.sd.arena.cliente;

import br.ucsal.sd.arena.cliente.ui.TelaConexao;
import br.ucsal.sd.arena.cliente.ui.TelaLobby;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClienteApp extends Application {

    private Scene cena;
    private TelaLobby lobbyAtual;

    @Override
    public void start(Stage stage) {
        cena = new Scene(new TelaConexao(this::mostrarLobby).getRaiz(), 960, 600);
        stage.setTitle("Arena de Elementais");
        stage.setScene(cena);
        stage.show();
    }

    private void mostrarConexao() {
        lobbyAtual = null;
        trocarTela(new TelaConexao(this::mostrarLobby).getRaiz());
    }

    private void mostrarLobby(TelaLobby lobby) {
        lobbyAtual = lobby;
        lobby.setAoSair(this::mostrarConexao);
        trocarTela(lobby.getRaiz());
    }

    private void trocarTela(Parent raiz) {
        cena.setRoot(raiz);
    }

    @Override
    public void stop() {
        if (lobbyAtual != null) {
            lobbyAtual.getConexao().desconectar(); // libera o nome e o callback ao fechar a janela
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
