package br.ucsal.sd.arena.cliente.ui;

import br.ucsal.sd.arena.cliente.rede.ConexaoServidor;
import br.ucsal.sd.arena.compartilhado.dto.*;
import br.ucsal.sd.arena.compartilhado.excecao.*;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Apresenta somente o estado autoritativo recebido; cliques não antecipam escolhas. */
public final class TelaDraft {
    private final ConexaoServidor conexao;
    private final VBox raiz = new VBox(12);
    private final TilePane grade = new TilePane(10, 10);
    private final Label contador = new Label("--:--");
    private final Label progresso = new Label("Aguardando criaturas do servidor...");
    private final Label aviso = new Label();
    private final Label situacao = new Label("Escolha 3 criaturas. Quem escolher primeiro fica com ela.");
    private final Timeline relogio;
    private final PauseTransition esconderAviso = new PauseTransition(Duration.seconds(3));
    private final Map<Integer, Image> imagens = new HashMap<>();
    private final Set<Integer> pendentes = new HashSet<>();
    private EstadoDraft estado;
    private int segundos;
    private boolean encerrada;

    public TelaDraft(ConexaoServidor conexao, String adversario) {
        this.conexao = conexao;
        Label titulo = new Label("Escolha seu time");
        titulo.getStyleClass().add("titulo");
        Label oponente = new Label("Adversário: " + adversario);
        oponente.getStyleClass().add("secundario");
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        contador.getStyleClass().add("contador-draft");
        HBox cabecalho = new HBox(16, new VBox(4, titulo, oponente), espaco, contador);
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        grade.setPrefColumns(5);
        grade.setPrefTileWidth(166);
        grade.setPrefTileHeight(430);
        grade.setTileAlignment(Pos.TOP_LEFT);
        ScrollPane rolagem = new ScrollPane(grade);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);
        aviso.getStyleClass().add("erro");
        aviso.setMinHeight(22);
        aviso.setWrapText(true);
        situacao.setWrapText(true);
        progresso.getStyleClass().add("subtitulo");
        raiz.setPadding(new Insets(20));
        raiz.getStyleClass().add("raiz");
        raiz.getChildren().addAll(cabecalho, situacao, progresso, rolagem, aviso);
        Estilo.aplicar(raiz);
        esconderAviso.setOnFinished(e -> aviso.setText(""));
        relogio = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            segundos = Math.max(0, segundos - 1);
            desenharContador();
        }));
        relogio.setCycleCount(Timeline.INDEFINITE);
    }

    public Parent getRaiz() { return raiz; }

    /** Também pode receber um EstadoDraft montado à mão em um teste visual. */
    public void atualizar(EstadoDraft novoEstado) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> atualizar(novoEstado));
            return;
        }
        if (encerrada) return;
        estado = novoEstado;
        segundos = Math.max(0, estado.segundosRestantes());
        desenharContador();
        if (segundos > 0) relogio.playFromStart();
        else relogio.stop();
        progresso.setText("Seu time: " + estado.minhasEscolhas().size() + "/3   •   Adversário: "
                + estado.escolhasAdversario().size() + "/3");
        if (estado.minhasEscolhas().size() == 3 && estado.escolhasAdversario().size() == 3) {
            situacao.setText("Draft concluído. Aguardando início da batalha...");
        } else if (segundos == 0) {
            situacao.setText("Aguardando atualização do servidor...");
        } else {
            situacao.setText("Escolha 3 criaturas. Quem escolher primeiro fica com ela.");
        }
        desenharGrade();
    }

    private void desenharContador() {
        contador.setText(String.format("%02d:%02d", segundos / 60, segundos % 60));
        contador.getStyleClass().remove("contador-urgente");
        if (segundos <= 5) contador.getStyleClass().add("contador-urgente");
        if (segundos == 0) {
            relogio.stop();
            // O relógio local nunca encerra a fase nem escolhe criaturas.
            situacao.setText("Aguardando atualização do servidor...");
        }
    }

    private void desenharGrade() {
        grade.getChildren().setAll(estado.pool().stream().map(this::cartao).toList());
    }

    private Button cartao(Criatura criatura) {
        boolean minha = estado.minhasEscolhas().contains(criatura.id());
        boolean adversaria = estado.escolhasAdversario().contains(criatura.id());
        Label nome = new Label(criatura.nome());
        nome.getStyleClass().add("subtitulo");
        Label tipo = new Label(criatura.tipo().name());
        tipo.getStyleClass().addAll("tag", "tipo-" + criatura.tipo().name().toLowerCase(java.util.Locale.ROOT));
        VBox conteudo = new VBox(6, nome, tipo);
        conteudo.setAlignment(Pos.TOP_LEFT);
        Image imagem = imagens.computeIfAbsent(criatura.id(), id -> {
            var recurso = TelaDraft.class.getResource("/br/ucsal/sd/arena/cliente/criaturas/" + id + ".png");
            return recurso == null ? null : new Image(recurso.toExternalForm(), 160, 100, true, true);
        });
        if (imagem != null) {
            ImageView arte = new ImageView(imagem);
            arte.setFitWidth(160);
            arte.setFitHeight(100);
            arte.setPreserveRatio(true);
            conteudo.getChildren().add(arte);
        }
        conteudo.getChildren().add(new Label("HP " + criatura.hpMax() + "   •   Vel " + criatura.velocidade()));
        for (Ataque ataque : criatura.ataques()) {
            Label linha = new Label(ataque.nome() + "\n" + ataque.tipo() + " · Poder " + ataque.poder());
            linha.setWrapText(true);
            linha.getStyleClass().add("ataque-draft");
            conteudo.getChildren().add(linha);
        }
        String selo = minha ? "Sua" : adversaria ? "Adversário" : pendentes.contains(criatura.id())
                ? "Enviando..." : "Disponível";
        conteudo.getChildren().add(new Label(selo));
        Button botao = new Button();
        botao.setGraphic(conteudo);
        botao.setMinSize(0, 0);
        botao.setPrefSize(166, 430);
        botao.setMaxSize(166, 430);
        botao.getStyleClass().add("criatura-draft");
        if (minha) botao.getStyleClass().add("criatura-minha");
        if (adversaria) botao.getStyleClass().add("criatura-adversaria");
        botao.setDisable(encerrada || minha || adversaria || estado.minhasEscolhas().size() >= 3
                || pendentes.contains(criatura.id()));
        botao.setAccessibleText(criatura.nome() + ", " + selo);
        botao.setOnAction(e -> escolher(criatura));
        return botao;
    }

    private void escolher(Criatura criatura) {
        if (encerrada || !pendentes.add(criatura.id())) return;
        desenharGrade();
        // Captura a sessão antes do trabalho remoto; desconectar não altera esta chamada.
        var servico = conexao.getServico();
        String token = conexao.getToken();
        Thread chamada = new Thread(() -> {
            String erro = null;
            try {
                servico.escolherCriatura(token, criatura.id());
            } catch (CriaturaIndisponivelException e) {
                erro = criatura.nome() + " já foi escolhida pelo adversário!";
            } catch (LimiteDeEscolhasException e) {
                erro = "Você já tem 3 criaturas";
            } catch (FaseInvalidaException e) {
                erro = "O draft já terminou. Aguarde o servidor.";
            } catch (SessaoInvalidaException e) {
                erro = "Sessão expirada. Conecte-se novamente.";
            } catch (Exception e) {
                erro = "Falha de comunicação com o servidor.";
            }
            String mensagem = erro;
            Platform.runLater(() -> {
                pendentes.remove(criatura.id());
                if (encerrada) return;
                desenharGrade();
                if (mensagem != null) {
                    aviso.setText(mensagem);
                    esconderAviso.playFromStart();
                }
            });
        }, "escolher-criatura");
        chamada.setDaemon(true);
        chamada.start();
    }

    /** Chamado ao sair: timers e retornos remotos antigos deixam de afetar a tela. */
    public void encerrar() {
        encerrada = true;
        relogio.stop();
        esconderAviso.stop();
        pendentes.clear();
    }
}
