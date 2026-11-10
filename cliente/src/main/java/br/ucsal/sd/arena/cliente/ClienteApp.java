package br.ucsal.sd.arena.cliente;

import br.ucsal.sd.arena.cliente.ui.TelaConexao;
import br.ucsal.sd.arena.cliente.ui.TelaLobby;
import br.ucsal.sd.arena.cliente.ui.TelaDraft;
import br.ucsal.sd.arena.compartilhado.dto.EstadoBatalha;
import br.ucsal.sd.arena.compartilhado.dto.PartidaInfo;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClienteApp extends Application {

    private Scene cena;
    private TelaLobby lobbyAtual;
    private TelaDraft draftAtual;

    @Override
    public void start(Stage stage) {
        cena = new Scene(new TelaConexao(this::mostrarLobby).getRaiz(), 960, 600);
        stage.setTitle("Arena de Elementais");
        stage.setScene(cena);
        stage.show();
    }

    private void mostrarConexao() {
        encerrarDraft();
        lobbyAtual = null;
        trocarTela(new TelaConexao(this::mostrarLobby).getRaiz());
    }

    private void mostrarLobby(TelaLobby lobby) {
        lobbyAtual = lobby;
        lobby.setAoSair(this::mostrarConexao);
        lobby.setAoPartidaEncontrada(this::mostrarDraft);
        lobby.setAoDraftAtualizado(estado -> {
            if (draftAtual != null) draftAtual.atualizar(estado);
        });
        lobby.setAoBatalhaIniciada(this::mostrarBatalha);
        lobby.setAoPartidaEncerrada(resultado -> {
            encerrarDraft();
            mostrarMensagem("Partida encerrada", "Vencedor: " + resultado.vencedor()
                    + (resultado.porWO() ? " (W.O.)" : ""));
        });
        trocarTela(lobby.getRaiz());
    }

    private void mostrarDraft(PartidaInfo info) {
        encerrarDraft();
        draftAtual = new TelaDraft(lobbyAtual.getConexao(), info.nomeAdversario());
        trocarTela(draftAtual.getRaiz());
    }

    /** Ponto único de troca para a futura tela de batalha, com o estado recebido do servidor. */
    private void mostrarBatalha(EstadoBatalha estado) {
        encerrarDraft();
        mostrarMensagem("Draft concluído", "Batalha iniciada — rodada " + estado.numeroRodada());
    }

    private void mostrarMensagem(String titulo, String texto) {
        Label cabecalho = new Label(titulo);
        cabecalho.getStyleClass().add("titulo");
        VBox raiz = new VBox(16, cabecalho, new Label(texto));
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("raiz");
        raiz.getStylesheets().add(ClienteApp.class.getResource("/br/ucsal/sd/arena/cliente/arena.css").toExternalForm());
        trocarTela(raiz);
    }

    private void encerrarDraft() {
        if (draftAtual != null) {
            draftAtual.encerrar();
            draftAtual = null;
        }
    }

    private void trocarTela(Parent raiz) {
        cena.setRoot(raiz);
    }

    @Override
    public void stop() {
        encerrarDraft();
        if (lobbyAtual != null) {
            lobbyAtual.getConexao().desconectar(); // libera o nome e o callback ao fechar a janela
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
