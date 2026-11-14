package br.ucsal.sd.arena.cliente.ui;

import br.ucsal.sd.arena.compartilhado.dto.Criatura;
import br.ucsal.sd.arena.compartilhado.dto.EstadoCriaturaEmBatalha;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoPartida;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;

/** Resumo autoritativo da partida e navegação para o próximo jogo. */
public final class TelaResultado {
    private final VBox raiz = new VBox(16);

    public TelaResultado(String meuNome, ResultadoPartida resultado, List<Criatura> catalogo,
                         Runnable aoVoltar, Runnable aoBuscar) {
        Map<Integer, Criatura> criaturas = catalogo.stream()
                .collect(Collectors.toMap(Criatura::id, Function.identity()));
        boolean venceu = meuNome.equals(resultado.vencedor());
        Label titulo = new Label(venceu ? "Vitória" + (resultado.porWO() ? " por W.O." : "") : "Derrota");
        titulo.getStyleClass().add("titulo");
        Label vencedor = new Label("Vencedor: " + resultado.vencedor());
        Label rodadas = new Label("Rodadas jogadas: " + resultado.rodadasJogadas());
        long derrubadas = resultado.timeAdversario().stream().filter(EstadoCriaturaEmBatalha::derrubada).count();
        Label abates = new Label("Criaturas adversárias derrubadas: " + derrubadas);
        VBox times = new VBox(12, time("Seu time", resultado.meuTime(), criaturas),
                time("Time adversário", resultado.timeAdversario(), criaturas));
        ScrollPane rolagem = new ScrollPane(times);
        rolagem.setFitToWidth(true);
        rolagem.setMinHeight(100);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);
        Button voltar = new Button("Voltar ao lobby");
        voltar.getStyleClass().add("botao");
        voltar.setOnAction(e -> aoVoltar.run());
        Button buscar = new Button("Buscar nova partida");
        buscar.getStyleClass().addAll("botao", "botao-primario");
        buscar.setOnAction(e -> aoBuscar.run());
        raiz.getChildren().addAll(titulo, vencedor, rodadas, abates, rolagem, voltar, buscar);
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("raiz");
        Estilo.aplicar(raiz);
    }

    private VBox time(String titulo, List<EstadoCriaturaEmBatalha> time, Map<Integer, Criatura> catalogo) {
        Label cabecalho = new Label(titulo);
        cabecalho.getStyleClass().add("subtitulo");
        VBox caixa = new VBox(8, cabecalho);
        caixa.getStyleClass().add("cartao");
        caixa.setPadding(new Insets(12));
        for (EstadoCriaturaEmBatalha estado : time) {
            Criatura criatura = catalogo.get(estado.idCriatura());
            String nome = criatura == null ? "Criatura " + estado.idCriatura() : criatura.nome();
            Label linha = new Label(nome + " · HP " + estado.hpAtual()
                    + (criatura == null ? "" : " / " + criatura.hpMax())
                    + (estado.derrubada() ? " · Derrubada" : " · Viva"));
            linha.setWrapText(true);
            caixa.getChildren().add(linha);
        }
        if (time.isEmpty()) caixa.getChildren().add(new Label("Partida encerrada durante o draft."));
        return caixa;
    }

    public Parent getRaiz() { return raiz; }
}
