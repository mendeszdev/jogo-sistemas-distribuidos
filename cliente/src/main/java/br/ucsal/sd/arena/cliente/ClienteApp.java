package br.ucsal.sd.arena.cliente;

import br.ucsal.sd.arena.cliente.ui.TelaConexao;
import br.ucsal.sd.arena.cliente.ui.TelaLobby;
import br.ucsal.sd.arena.cliente.ui.TelaDraft;
import br.ucsal.sd.arena.cliente.ui.TelaBatalha;
import br.ucsal.sd.arena.cliente.ui.TelaResultado;
import br.ucsal.sd.arena.compartilhado.dto.Criatura;
import java.util.List;
import br.ucsal.sd.arena.compartilhado.dto.EstadoBatalha;
import br.ucsal.sd.arena.compartilhado.dto.PartidaInfo;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClienteApp extends Application {

    private Scene cena;
    private TelaLobby lobbyAtual;
    private TelaDraft draftAtual;
    private TelaBatalha batalhaAtual;
    private List<Criatura> catalogo = List.of();

    @Override
    public void start(Stage stage) {
        cena = new Scene(new TelaConexao(this::mostrarLobby).getRaiz(), 960, 600);
        stage.setTitle("Arena de Elementais");
        stage.setScene(cena);
        stage.show();
    }

    private void mostrarConexao() {
        encerrarDraft();
        encerrarBatalha();
        lobbyAtual = null;
        trocarTela(new TelaConexao(this::mostrarLobby).getRaiz());
    }

    private void mostrarLobby(TelaLobby lobby) {
        lobbyAtual = lobby;
        lobby.setAoSair(this::mostrarConexao);
        lobby.setAoPartidaEncontrada(this::mostrarDraft);
        lobby.setAoDraftAtualizado(estado -> {
            catalogo = estado.pool();
            if (draftAtual != null) draftAtual.atualizar(estado);
        });
        lobby.setAoBatalhaIniciada(this::mostrarBatalha);
        lobby.setAoRodadaResolvida(resultado -> {
            if (batalhaAtual != null) batalhaAtual.rodadaResolvida(resultado);
        });
        lobby.setAoPartidaEncerrada(resultado -> {
            encerrarDraft();
            encerrarBatalha();
            trocarTela(new TelaResultado(lobbyAtual.getNome(), resultado, catalogo,
                    () -> trocarTela(lobbyAtual.getRaiz()), () -> {
                        trocarTela(lobbyAtual.getRaiz());
                        lobbyAtual.buscarNovaPartida();
                    }).getRaiz());
        });
        trocarTela(lobby.getRaiz());
    }

    private void mostrarDraft(PartidaInfo info) {
        encerrarDraft();
        encerrarBatalha();
        draftAtual = new TelaDraft(lobbyAtual.getConexao(), info.nomeAdversario());
        trocarTela(draftAtual.getRaiz());
    }

    /** Abre a batalha com o catálogo recebido durante o draft. */
    private void mostrarBatalha(EstadoBatalha estado) {
        encerrarDraft();
        encerrarBatalha();
        batalhaAtual = new TelaBatalha(lobbyAtual.getConexao(), catalogo);
        batalhaAtual.atualizar(estado);
        trocarTela(batalhaAtual.getRaiz());
    }

    private void encerrarDraft() {
        if (draftAtual != null) {
            draftAtual.encerrar();
            draftAtual = null;
        }
    }

    private void encerrarBatalha() {
        if (batalhaAtual != null) {
            batalhaAtual.encerrar();
            batalhaAtual = null;
        }
    }

    private void trocarTela(Parent raiz) {
        cena.setRoot(raiz);
    }

    @Override
    public void stop() {
        encerrarDraft();
        encerrarBatalha();
        if (lobbyAtual != null) {
            lobbyAtual.getConexao().desconectar(); // libera o nome e o callback ao fechar a janela
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
