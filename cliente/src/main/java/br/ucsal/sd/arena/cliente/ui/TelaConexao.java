package br.ucsal.sd.arena.cliente.ui;

import br.ucsal.sd.arena.cliente.rede.ConexaoServidor;
import br.ucsal.sd.arena.compartilhado.excecao.NomeEmUsoException;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.function.Consumer;

/** Tela 1: conexão (IP, porta e nome). Ao conectar, entrega a {@link TelaLobby} já ligada ao servidor. */
public final class TelaConexao {

    private final StackPane raiz = new StackPane();
    private final TextField campoIp = new TextField("127.0.0.1");
    private final TextField campoPorta = new TextField("1099");
    private final TextField campoNome = new TextField();
    private final Button botaoConectar = new Button("Conectar");
    private final Label estado = new Label();
    private final Consumer<TelaLobby> aoConectar;

    public TelaConexao(Consumer<TelaLobby> aoConectar) {
        this.aoConectar = aoConectar;

        Label titulo = new Label("Arena de Elementais");
        titulo.getStyleClass().add("titulo");
        Label subtitulo = new Label("Conecte-se ao servidor para jogar.");
        subtitulo.getStyleClass().add("secundario");

        campoNome.setPromptText("Digite seu nome");
        botaoConectar.getStyleClass().addAll("botao", "botao-primario");
        botaoConectar.setMaxWidth(Double.MAX_VALUE);
        botaoConectar.setDefaultButton(true);
        botaoConectar.setOnAction(e -> conectar());
        estado.getStyleClass().add("secundario");
        estado.setWrapText(true);

        VBox cartao = new VBox(16, titulo, subtitulo,
                campo("IP do servidor", campoIp), campo("Porta", campoPorta), campo("Nome do jogador", campoNome),
                botaoConectar, estado);
        cartao.getStyleClass().add("cartao");
        cartao.setPadding(new javafx.geometry.Insets(28));
        cartao.setMaxWidth(420);
        cartao.setMaxHeight(Region.USE_PREF_SIZE);

        raiz.getChildren().add(cartao);
        raiz.setAlignment(Pos.CENTER);
        raiz.getStyleClass().add("raiz");
        Estilo.aplicar(raiz);
    }

    public Parent getRaiz() {
        return raiz;
    }

    private static VBox campo(String rotulo, TextField campo) {
        Label l = new Label(rotulo);
        l.getStyleClass().add("rotulo");
        l.setLabelFor(campo);
        return new VBox(4, l, campo);
    }

    private void conectar() {
        String host = campoIp.getText().strip();
        String nome = campoNome.getText().strip();
        if (host.isEmpty()) {
            mostrarErro("Informe o IP do servidor.");
            return;
        }
        int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().strip());
            if (porta < 1 || porta > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            mostrarErro("Porta inválida.");
            return;
        }
        if (nome.isEmpty()) {
            mostrarErro("Informe seu nome.");
            return;
        }

        definirOcupado(true);
        estado.getStyleClass().remove("erro");
        estado.setText("Conectando...");

        // IP do cliente alcançável pelo servidor: só necessário quando servidor e cliente estão em máquinas diferentes.
        ConexaoServidor conexao = new ConexaoServidor(host, porta, System.getProperty("arena.ipLocal"));
        TelaLobby lobby = new TelaLobby(nome, host + ":" + porta, conexao); // é o OuvinteEventosJogo
        Thread t = new Thread(() -> {
            try {
                conexao.conectar(nome, lobby);
                Platform.runLater(() -> aoConectar.accept(lobby));
            } catch (NomeEmUsoException e) {
                Platform.runLater(() -> falhar("Nome já em uso"));
            } catch (NotBoundException | RemoteException e) {
                Platform.runLater(() -> falhar("Servidor não encontrado"));
            } catch (RuntimeException e) {
                Platform.runLater(() -> falhar("Erro ao conectar: " + e.getMessage()));
            }
        }, "conectar-servidor");
        t.setDaemon(true);
        t.start();
    }

    private void falhar(String mensagem) {
        definirOcupado(false);
        mostrarErro(mensagem);
    }

    private void mostrarErro(String mensagem) {
        if (!estado.getStyleClass().contains("erro")) {
            estado.getStyleClass().add("erro");
        }
        estado.setText(mensagem);
    }

    private void definirOcupado(boolean ocupado) {
        botaoConectar.setDisable(ocupado);
        campoIp.setDisable(ocupado);
        campoPorta.setDisable(ocupado);
        campoNome.setDisable(ocupado);
    }
}
