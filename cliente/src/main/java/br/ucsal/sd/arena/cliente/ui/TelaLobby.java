package br.ucsal.sd.arena.cliente.ui;

import br.ucsal.sd.arena.cliente.rede.ConexaoServidor;
import br.ucsal.sd.arena.cliente.rede.OuvinteEventosJogo;
import br.ucsal.sd.arena.compartilhado.dto.EstadoBatalha;
import br.ucsal.sd.arena.compartilhado.dto.EstadoDraft;
import br.ucsal.sd.arena.compartilhado.dto.JogadorOnline;
import br.ucsal.sd.arena.compartilhado.dto.PartidaInfo;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoPartida;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoRodada;
import br.ucsal.sd.arena.compartilhado.dto.StatusJogador;
import br.ucsal.sd.arena.compartilhado.excecao.JogadorJaEmPartidaException;
import br.ucsal.sd.arena.compartilhado.excecao.SessaoInvalidaException;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.rmi.RemoteException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tela 2: lobby. Também é o {@link OuvinteEventosJogo} da sessão: os eventos chegam em threads do RMI e
 * aqui são repassados à thread do JavaFX com {@code Platform.runLater}.
 */
public final class TelaLobby implements OuvinteEventosJogo {

    @FunctionalInterface
    private interface ChamadaRemota {
        void executar() throws Exception;
    }

    private final String nome;
    private final ConexaoServidor conexao;

    private final VBox raiz = new VBox(16);
    private final Label tituloLista = new Label("Jogadores online (0)");
    private final VBox listaJogadores = new VBox();
    private final Button botaoBuscar = new Button("Buscar partida");
    private final VBox caixaFila = new VBox(8);
    private final Label tempoFila = new Label("Na fila... 00:00");
    private final Label mensagem = new Label();

    private final Timeline relogioFila = new Timeline();
    private int segundosNaFila;
    /** Só usado na thread do JavaFX. O pareamento pode chegar ANTES do retorno de buscarPartida(). */
    private boolean partidaEncontrada;

    private Runnable aoSair = () -> { };
    private Consumer<PartidaInfo> aoPartidaEncontrada = info -> { };

    public TelaLobby(String nome, String endereco, ConexaoServidor conexao) {
        this.nome = nome;
        this.conexao = conexao;

        // --- cabeçalho ---
        Label titulo = new Label("Arena de Elementais");
        titulo.getStyleClass().add("titulo-pequeno");
        Label info = new Label("Jogador: " + nome + " · Conectado a " + endereco);
        info.getStyleClass().add("secundario");
        Button botaoSair = new Button("Sair");
        botaoSair.getStyleClass().add("botao");
        botaoSair.setOnAction(e -> sair());
        Region espaco1 = new Region();
        Region espaco2 = new Region();
        HBox.setHgrow(espaco1, Priority.ALWAYS);
        HBox.setHgrow(espaco2, Priority.ALWAYS);
        HBox cabecalho = new HBox(16, titulo, espaco1, info, espaco2, botaoSair);
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        cabecalho.getStyleClass().add("cartao");
        cabecalho.setPadding(new Insets(12));

        // --- jogadores online ---
        tituloLista.getStyleClass().add("subtitulo");
        ScrollPane rolagem = new ScrollPane(listaJogadores);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);
        VBox painelLista = new VBox(6, tituloLista, rolagem);
        painelLista.getStyleClass().add("cartao");
        painelLista.setPadding(new Insets(12));
        HBox.setHgrow(painelLista, Priority.ALWAYS);

        // --- partida ---
        Label tituloPartida = new Label("Partida");
        tituloPartida.getStyleClass().add("subtitulo");
        botaoBuscar.getStyleClass().addAll("botao", "botao-primario");
        botaoBuscar.setMaxWidth(Double.MAX_VALUE);
        botaoBuscar.setMinHeight(56);
        botaoBuscar.setOnAction(e -> buscar());

        tempoFila.getStyleClass().add("rotulo");
        Label aguardando = new Label("Aguardando um adversário.");
        aguardando.getStyleClass().add("secundario");
        Button botaoCancelar = new Button("Cancelar busca");
        botaoCancelar.getStyleClass().add("botao");
        botaoCancelar.setMaxWidth(Double.MAX_VALUE);
        botaoCancelar.setOnAction(e -> cancelar());
        caixaFila.getChildren().addAll(tempoFila, aguardando, botaoCancelar);
        caixaFila.getStyleClass().add("cartao-tracejado");
        caixaFila.setPadding(new Insets(12));
        mostrarCaixaFila(false);

        mensagem.getStyleClass().add("secundario");
        mensagem.setWrapText(true);

        VBox painelPartida = new VBox(14, tituloPartida, botaoBuscar, caixaFila, mensagem);
        painelPartida.getStyleClass().add("cartao");
        painelPartida.setPadding(new Insets(12));
        painelPartida.setPrefWidth(300);
        painelPartida.setMinWidth(300);

        HBox corpo = new HBox(16, painelLista, painelPartida);
        VBox.setVgrow(corpo, Priority.ALWAYS);

        raiz.getChildren().addAll(cabecalho, corpo);
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("raiz");
        Estilo.aplicar(raiz);

        relogioFila.getKeyFrames().add(new KeyFrame(Duration.seconds(1), e -> {
            segundosNaFila++;
            atualizarTempoFila();
        }));
        relogioFila.setCycleCount(Timeline.INDEFINITE);
    }

    public Parent getRaiz() {
        return raiz;
    }

    public ConexaoServidor getConexao() {
        return conexao;
    }

    /** O que fazer depois de sair do lobby (voltar à tela de conexão). */
    public void setAoSair(Runnable aoSair) {
        this.aoSair = aoSair;
    }

    /** Navegação para o draft (tela de outro card). */
    public void setAoPartidaEncontrada(Consumer<PartidaInfo> aoPartidaEncontrada) {
        this.aoPartidaEncontrada = aoPartidaEncontrada;
    }

    // ---------------------------------------------------------------- ações do usuário

    private void buscar() {
        botaoBuscar.setDisable(true);
        mensagem.setText("");
        remoto(() -> conexao.getServico().buscarPartida(conexao.getToken()),
                this::entrarNaFila, () -> botaoBuscar.setDisable(false));
    }

    private void cancelar() {
        remoto(() -> conexao.getServico().cancelarBusca(conexao.getToken()), this::sairDaFila, () -> { });
    }

    private void sair() {
        relogioFila.stop();
        Thread t = new Thread(() -> {
            conexao.desconectar();
            Platform.runLater(aoSair);
        }, "sair-do-servidor");
        t.setDaemon(true);
        t.start();
    }

    /** Executa a chamada RMI fora da thread da interface; o resultado volta pela thread do JavaFX. */
    private void remoto(ChamadaRemota chamada, Runnable aoSucesso, Runnable aoFalhar) {
        Thread t = new Thread(() -> {
            try {
                chamada.executar();
                Platform.runLater(aoSucesso);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mensagem.setText(traduzir(e));
                    aoFalhar.run();
                });
            }
        }, "chamada-remota");
        t.setDaemon(true);
        t.start();
    }

    private static String traduzir(Exception e) {
        if (e instanceof JogadorJaEmPartidaException) {
            return "Você já está na fila ou em uma partida.";
        }
        if (e instanceof SessaoInvalidaException) {
            return "Sessão expirada. Saia e conecte-se novamente.";
        }
        if (e instanceof RemoteException) {
            return "Falha de comunicação com o servidor.";
        }
        return "Erro: " + e.getMessage();
    }

    // ---------------------------------------------------------------- estado da fila

    private void entrarNaFila() {
        if (partidaEncontrada) {
            return; // o evento partidaEncontrada já chegou: não mostrar a fila
        }
        segundosNaFila = 0;
        atualizarTempoFila();
        mostrarCaixaFila(true);
        botaoBuscar.setDisable(true);
        relogioFila.playFromStart();
    }

    private void sairDaFila() {
        relogioFila.stop();
        mostrarCaixaFila(false);
        botaoBuscar.setDisable(false);
    }

    private void mostrarCaixaFila(boolean visivel) {
        caixaFila.setVisible(visivel);
        caixaFila.setManaged(visivel);
    }

    private void atualizarTempoFila() {
        tempoFila.setText(String.format("Na fila... %02d:%02d", segundosNaFila / 60, segundosNaFila % 60));
    }

    // ---------------------------------------------------------------- eventos do servidor (threads do RMI)

    @Override
    public void jogadoresOnlineAtualizados(List<JogadorOnline> jogadores) {
        Platform.runLater(() -> {
            tituloLista.setText("Jogadores online (" + jogadores.size() + ")");
            listaJogadores.getChildren().setAll(jogadores.stream().map(this::linha).toList());
        });
    }

    private HBox linha(JogadorOnline jogador) {
        String texto = jogador.nome().equals(nome) ? "Você (" + nome + ")" : jogador.nome();
        Label nomeJogador = new Label(texto);
        nomeJogador.setStyle("-fx-font-size: 15px;");
        Label tag = new Label(rotulo(jogador.statusJogador()));
        tag.getStyleClass().add("tag");
        if (jogador.statusJogador() == StatusJogador.EM_FILA) {
            tag.getStyleClass().add("tag-em-fila");
        }
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox linha = new HBox(8, nomeJogador, espaco, tag);
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.getStyleClass().add("linha-jogador");
        return linha;
    }

    private static String rotulo(StatusJogador status) {
        return switch (status) {
            case LIVRE -> "Livre";
            case EM_FILA -> "Em fila";
            case EM_PARTIDA -> "Em partida";
        };
    }

    @Override
    public void partidaEncontrada(PartidaInfo info) {
        Platform.runLater(() -> {
            partidaEncontrada = true;
            relogioFila.stop();
            mostrarCaixaFila(false);
            botaoBuscar.setDisable(true);
            mensagem.setText("Partida encontrada, indo para o draft.");
            aoPartidaEncontrada.accept(info);
        });
    }

    // Eventos das fases seguintes: tratados pelas telas de draft, batalha e resultado (outros cards).
    @Override
    public void draftAtualizado(EstadoDraft estado) { }

    @Override
    public void batalhaIniciada(EstadoBatalha estado) { }

    @Override
    public void rodadaResolvida(ResultadoRodada resultado) { }

    @Override
    public void partidaEncerrada(ResultadoPartida resultado) { }

    @Override
    public void adversarioDesconectado(int segundosTolerancia) { }
}
