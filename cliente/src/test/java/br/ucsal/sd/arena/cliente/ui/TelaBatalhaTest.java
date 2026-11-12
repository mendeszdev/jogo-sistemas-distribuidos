package br.ucsal.sd.arena.cliente.ui;

import br.ucsal.sd.arena.cliente.rede.ConexaoServidor;
import br.ucsal.sd.arena.compartilhado.dto.*;
import br.ucsal.sd.arena.compartilhado.remoto.ServicoJogo;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class TelaBatalhaTest {
    private TelaBatalha tela;
    private final List<Criatura> catalogo = List.of(
            new Criatura(1, "Chama", Tipo.FOGO, 100, 40, List.of(new Ataque("Brasa", Tipo.FOGO, 30))),
            new Criatura(2, "Reserva", Tipo.FOGO, 80, 30, List.of()),
            new Criatura(3, "Inimigo", Tipo.FOGO, 90, 30, List.of()));

    @BeforeAll static void iniciar() throws Exception {
        CountDownLatch pronto = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); pronto.countDown(); }); }
        catch (IllegalStateException e) { Platform.runLater(pronto::countDown); }
        assertTrue(pronto.await(10, TimeUnit.SECONDS));
    }
    private static void fx(Runnable r) throws Exception {
        FutureTask<Void> t = new FutureTask<>(r, null);
        Platform.runLater(t); t.get(10, TimeUnit.SECONDS);
    }
    @AfterEach void fechar() throws Exception { if (tela != null) fx(tela::encerrar); }
    private EstadoBatalha estado(int rodada, int segundos) {
        return new EstadoBatalha(rodada, new EstadoCriaturaEmBatalha(1, 70),
                List.of(new EstadoCriaturaEmBatalha(2, 60), new EstadoCriaturaEmBatalha(3, 0)),
                new EstadoCriaturaEmBatalha(3, 40), 2, segundos);
    }
    private Stream<Node> nos(Node n) {
        if (n instanceof ScrollPane s) return Stream.concat(Stream.of(n), nos(s.getContent()));
        return Stream.concat(Stream.of(n), n instanceof Parent p
                ? p.getChildrenUnmodifiable().stream().flatMap(this::nos) : Stream.empty());
    }
    private List<Button> botoes() { return nos(tela.getRaiz()).filter(Button.class::isInstance).map(Button.class::cast).toList(); }
    private boolean texto(String t) { return nos(tela.getRaiz()).anyMatch(n -> n instanceof Label l && l.getText().equals(t)); }
    private void criar(ServicoJogo s) throws Exception {
        fx(() -> tela = new TelaBatalha(new ConexaoServidor("localhost", 1099, null) {
            @Override public ServicoJogo getServico() { return s; }
            @Override public String getToken() { return "token"; }
        }, catalogo));
    }

    @Test void exibeHpReservasLogETempoSemAlterarEstadoLocal() throws Exception {
        criar(null);
        fx(() -> {
            tela.atualizar(estado(1, 30));
            assertTrue(texto("HP 70 / 100"));
            assertTrue(texto("HP 40 / 90"));
            assertTrue(texto("00:30"));
            assertEquals(3, botoes().size());
            assertTrue(botoes().get(2).isDisabled());
            ResultadoRodada r = new ResultadoRodada(1, List.of("Brasa causou dano"), estado(2, 25));
            tela.rodadaResolvida(r); tela.rodadaResolvida(r);
            TextArea log = (TextArea) nos(tela.getRaiz()).filter(TextArea.class::isInstance).findFirst().orElseThrow();
            assertEquals("Rodada 1\nBrasa causou dano\n\n", log.getText());
            tela.atualizar(estado(2, 0));
            assertTrue(botoes().stream().allMatch(Node::isDisabled));
            assertTrue(texto("Aguardando atualização do servidor..."));
        });
    }

    @Test void enviaIndiceDaReservaForaDaUiEIgnoraRetornoDeRodadaAntiga() throws Exception {
        CountDownLatch entrou = new CountDownLatch(1), liberar = new CountDownLatch(1), saiu = new CountDownLatch(1);
        AtomicReference<Acao> acao = new AtomicReference<>();
        ServicoJogo s = (ServicoJogo) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ServicoJogo.class}, (p, m, args) -> {
            assertFalse(Platform.isFxApplicationThread());
            assertEquals("enviarAcao", m.getName()); assertEquals("token", args[0]);
            acao.set((Acao) args[1]); entrou.countDown();
            try { assertTrue(liberar.await(5, TimeUnit.SECONDS)); } finally { saiu.countDown(); }
            return null;
        });
        criar(s);
        try {
            fx(() -> { tela.atualizar(estado(1, 30)); botoes().get(1).fire(); });
            assertTrue(entrou.await(5, TimeUnit.SECONDS));
            assertEquals(new Acao(TipoAcao.TROCAR, 0), acao.get());
            fx(() -> { assertTrue(botoes().stream().allMatch(Node::isDisabled)); tela.atualizar(estado(2, 30)); });
        } finally { liberar.countDown(); }
        assertTrue(saiu.await(5, TimeUnit.SECONDS));
        fx(() -> assertFalse(botoes().getFirst().isDisabled()));
    }

    @Test void confirmaAtaqueEAguardaAdversario() throws Exception {
        CountDownLatch enviou = new CountDownLatch(1);
        ServicoJogo s = (ServicoJogo) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ServicoJogo.class}, (p, m, args) -> {
            assertEquals(new Acao(TipoAcao.ATACAR, 0), args[1]); enviou.countDown(); return null;
        });
        criar(s);
        fx(() -> { tela.atualizar(estado(1, 30)); botoes().getFirst().fire(); });
        assertTrue(enviou.await(5, TimeUnit.SECONDS));
        long prazo = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean[] esperando = {false};
        while (!esperando[0] && System.nanoTime() < prazo) {
            fx(() -> esperando[0] = texto("Aguardando adversário..."));
            if (!esperando[0]) Thread.sleep(10);
        }
        assertTrue(esperando[0]);
        fx(() -> { assertTrue(botoes().stream().allMatch(Node::isDisabled)); tela.encerrar(); tela.atualizar(estado(2, 30)); assertTrue(texto("Batalha · Rodada 1")); });
    }
}
