package br.ucsal.sd.arena.cliente.ui;

import br.ucsal.sd.arena.cliente.rede.ConexaoServidor;
import br.ucsal.sd.arena.compartilhado.dto.*;
import br.ucsal.sd.arena.compartilhado.excecao.*;
import javafx.animation.KeyFrame;
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

import java.util.*;

/** Visão da batalha: HP e rodadas só mudam por eventos autoritativos do servidor. */
public final class TelaBatalha {
    private final ConexaoServidor conexao;
    private final Map<Integer, Criatura> catalogo = new HashMap<>();
    private final VBox raiz = new VBox(12);
    private final HBox arena = new HBox(16);
    private final FlowPane acoes = new FlowPane(8, 8);
    private final Label rodada = new Label();
    private final Label contador = new Label();
    private final Label situacao = new Label();
    private final Label aviso = new Label();
    private final TextArea log = new TextArea();
    private final Timeline relogio = new Timeline();
    private EstadoBatalha estado;
    private long prazo;
    private int segundos;
    private int ultimaRodadaRegistrada;
    private boolean pendente;
    private boolean enviada;
    private boolean encerrada;

    public TelaBatalha(ConexaoServidor conexao, List<Criatura> criaturas) {
        this.conexao = conexao;
        criaturas.forEach(c -> catalogo.put(c.id(), c));
        rodada.getStyleClass().add("titulo");
        contador.getStyleClass().add("contador-draft");
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox cabecalho = new HBox(16, rodada, espaco, contador);
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        situacao.setWrapText(true);
        aviso.setWrapText(true);
        aviso.getStyleClass().add("erro");
        log.setEditable(false);
        log.setWrapText(true);
        log.setPrefRowCount(4);
        log.setId("log-batalha");
        log.getStyleClass().add("log-batalha");
        Label tituloLog = new Label("Log da batalha");
        tituloLog.getStyleClass().add("subtitulo");
        VBox corpo = new VBox(12, arena, situacao, acoes, aviso, tituloLog, log);
        ScrollPane rolagem = new ScrollPane(corpo);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);
        raiz.setPadding(new Insets(20));
        raiz.getStyleClass().add("raiz");
        raiz.getChildren().addAll(cabecalho, rolagem);
        Estilo.aplicar(raiz);
        relogio.getKeyFrames().add(new KeyFrame(Duration.millis(200), e -> {
            segundos = (int) Math.max(0, Math.ceil((prazo - System.nanoTime()) / 1_000_000_000.0));
            atualizarContador();
            if (segundos == 0) { relogio.stop(); desenharAcoes(); }
        }));
        relogio.setCycleCount(Timeline.INDEFINITE);
    }

    public Parent getRaiz() { return raiz; }

    public void atualizar(EstadoBatalha novo) {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(() -> atualizar(novo)); return; }
        if (encerrada || (estado != null && novo.numeroRodada() < estado.numeroRodada())) return;
        if (estado == null || novo.numeroRodada() > estado.numeroRodada()) {
            pendente = false;
            enviada = false;
            aviso.setText("");
        }
        estado = novo;
        segundos = Math.max(0, novo.segundosRestantes());
        prazo = System.nanoTime() + segundos * 1_000_000_000L;
        rodada.setText("Batalha · Rodada " + novo.numeroRodada());
        arena.getChildren().setAll(cartao("Sua criatura", novo.minhaAtiva()),
                cartao("Adversário · " + novo.reservasRestantesAdversario() + " reservas", novo.ativaAdversario()));
        atualizarContador();
        relogio.stop();
        if (segundos > 0) relogio.playFromStart();
        desenharAcoes();
    }

    public void rodadaResolvida(ResultadoRodada resultado) {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(() -> rodadaResolvida(resultado)); return; }
        if (encerrada || resultado.numeroRodada() <= ultimaRodadaRegistrada) return;
        ultimaRodadaRegistrada = resultado.numeroRodada();
        log.appendText("Rodada " + resultado.numeroRodada() + "\n"
                + String.join("\n", resultado.eventos()) + "\n\n");
        log.positionCaret(log.getLength());
        atualizar(resultado.estadoAtual());
    }

    private VBox cartao(String dono, EstadoCriaturaEmBatalha atual) {
        VBox caixa = new VBox(6, new Label(dono));
        caixa.setPadding(new Insets(14));
        caixa.setAlignment(Pos.CENTER);
        caixa.getStyleClass().add("cartao");
        caixa.setMinWidth(0);
        caixa.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(caixa, Priority.ALWAYS);
        Criatura c = atual == null ? null : catalogo.get(atual.idCriatura());
        if (atual == null) { caixa.getChildren().add(new Label("Aguardando criatura")); return caixa; }
        Label nome = new Label(c == null ? "Criatura " + atual.idCriatura() : c.nome() + " · " + c.tipo());
        nome.setWrapText(true);
        nome.getStyleClass().add("subtitulo");
        caixa.getChildren().add(nome);
        var recurso = getClass().getResource("/br/ucsal/sd/arena/cliente/criaturas/" + atual.idCriatura() + ".png");
        if (recurso != null) {
            ImageView arte = new ImageView(new Image(recurso.toExternalForm(), 260, 150, true, true));
            arte.setFitWidth(260); arte.setFitHeight(150); arte.setPreserveRatio(true);
            caixa.getChildren().add(arte);
        }
        int hp = Math.max(0, atual.hpAtual());
        ProgressBar barra = new ProgressBar(c == null ? 0 : Math.min(1, hp / (double) c.hpMax()));
        barra.setMaxWidth(Double.MAX_VALUE);
        barra.getStyleClass().add("hp-batalha");
        barra.setAccessibleText("HP " + hp + (c == null ? "" : " de " + c.hpMax()));
        caixa.getChildren().addAll(barra, new Label("HP " + hp + (c == null ? "" : " / " + c.hpMax())
                + (atual.derrubada() ? " · Derrotada" : "")));
        return caixa;
    }

    private void atualizarContador() {
        contador.setText(String.format("%02d:%02d", segundos / 60, segundos % 60));
        contador.getStyleClass().remove("contador-urgente");
        if (segundos <= 5) contador.getStyleClass().add("contador-urgente");
        situacao.setText(pendente ? "Enviando ação..." : enviada ? "Aguardando adversário..."
                : segundos == 0 ? "Aguardando atualização do servidor..." : "Escolha um ataque ou troque sua criatura.");
    }

    private void desenharAcoes() {
        acoes.getChildren().clear();
        if (estado == null) return;
        Criatura ativa = estado.minhaAtiva() == null ? null : catalogo.get(estado.minhaAtiva().idCriatura());
        boolean bloquear = encerrada || pendente || enviada || segundos == 0;
        if (ativa != null) for (int i = 0; i < ativa.ataques().size(); i++) {
            Ataque ataque = ativa.ataques().get(i);
            adicionarAcao("Atacar: " + ataque.nome() + " · " + ataque.tipo() + " · Poder " + ataque.poder(),
                    new Acao(TipoAcao.ATACAR, i), bloquear || estado.minhaAtiva().derrubada());
        }
        for (int i = 0; i < estado.minhasReservas().size(); i++) {
            var reserva = estado.minhasReservas().get(i);
            Criatura c = catalogo.get(reserva.idCriatura());
            adicionarAcao("Trocar: " + (c == null ? "Criatura " + reserva.idCriatura() : c.nome())
                    + " · HP " + Math.max(0, reserva.hpAtual()), new Acao(TipoAcao.TROCAR, i), bloquear || reserva.derrubada());
        }
    }

    private void adicionarAcao(String texto, Acao acao, boolean desabilitado) {
        Button botao = new Button(texto);
        botao.setWrapText(true);
        botao.getStyleClass().add("botao");
        botao.setDisable(desabilitado);
        botao.setOnAction(e -> enviar(acao));
        acoes.getChildren().add(botao);
    }

    private void enviar(Acao acao) {
        if (encerrada || pendente || enviada || segundos == 0) return;
        int rodadaEnviada = estado.numeroRodada();
        pendente = true;
        aviso.setText("");
        atualizarContador(); desenharAcoes();
        var servico = conexao.getServico();
        String token = conexao.getToken();
        Thread chamada = new Thread(() -> {
            Exception falha = null;
            try { servico.enviarAcao(token, new Acao(acao.tipo(), acao.indice(), rodadaEnviada)); }
            catch (Exception e) { falha = e; }
            Exception erro = falha;
            Platform.runLater(() -> {
                // O callback da próxima rodada pode chegar antes do retorno desta chamada.
                if (encerrada || estado.numeroRodada() != rodadaEnviada) return;
                pendente = false;
                enviada = erro == null || erro instanceof ForaDaRodadaException;
                if (erro instanceof AcaoInvalidaException) aviso.setText("Ação inválida. Escolha outra ação.");
                else if (erro instanceof SessaoInvalidaException) { enviada = true; aviso.setText("Sessão expirada. Conecte-se novamente."); }
                else if (erro instanceof FaseInvalidaException) { enviada = true; aviso.setText("A batalha não está ativa. Aguarde o servidor."); }
                else if (erro instanceof ForaDaRodadaException) aviso.setText("Ação já enviada ou rodada encerrada. Aguarde o servidor.");
                else if (erro != null) { enviada = true; aviso.setText("Não foi possível confirmar a ação. Aguarde a próxima atualização do servidor."); }
                atualizarContador(); desenharAcoes();
            });
        }, "enviar-acao-batalha");
        chamada.setDaemon(true);
        chamada.start();
    }

    public void encerrar() {
        encerrada = true;
        relogio.stop();
        desenharAcoes();
    }
}
