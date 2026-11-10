package br.ucsal.sd.arena.cliente.ui;

import br.ucsal.sd.arena.cliente.rede.ConexaoServidor;
import br.ucsal.sd.arena.compartilhado.dto.*;
import br.ucsal.sd.arena.compartilhado.excecao.CriaturaIndisponivelException;
import br.ucsal.sd.arena.compartilhado.excecao.LimiteDeEscolhasException;
import br.ucsal.sd.arena.compartilhado.remoto.ServicoJogo;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TelaDraftTest {
    private TelaDraft tela;
    private final List<Criatura> pool = IntStream.rangeClosed(1, 10)
            .mapToObj(id -> new Criatura(id, "Criatura " + id, Tipo.FOGO, 80, 50,
                    List.of(new Ataque("Chama", Tipo.FOGO, 40)))).toList();

    @BeforeAll static void iniciarJavaFX() throws Exception {
        CountDownLatch pronto = new CountDownLatch(1);
        try {
            Platform.startup(() -> { Platform.setImplicitExit(false); pronto.countDown(); });
        } catch (IllegalStateException jaIniciado) {
            Platform.runLater(pronto::countDown);
        }
        assertTrue(pronto.await(10, TimeUnit.SECONDS));
    }

    private static void fx(Runnable trabalho) throws Exception {
        FutureTask<Void> tarefa = new FutureTask<>(trabalho, null);
        Platform.runLater(tarefa);
        tarefa.get(10, TimeUnit.SECONDS);
    }

    @AfterEach void encerrar() throws Exception {
        if (tela != null) fx(tela::encerrar);
    }

    private void criar(ServicoJogo servico) throws Exception {
        fx(() -> tela = new TelaDraft(new ConexaoServidor("localhost", 1099, null) {
            @Override public ServicoJogo getServico() { return servico; }
            @Override public String getToken() { return "sessao-teste"; }
        }, "Oponente"));
    }

    private EstadoDraft estado(List<Integer> minhas, Set<Integer> outras, int segundos) {
        return new EstadoDraft(pool, minhas, outras, segundos);
    }

    private Stream<Node> descendentes(Node raiz) {
        // ScrollPane mantém seu conteúdo fora de getChildrenUnmodifiable antes de aplicar CSS.
        if (raiz instanceof javafx.scene.control.ScrollPane scroll) {
            return Stream.concat(Stream.of(raiz), descendentes(scroll.getContent()));
        }
        return Stream.concat(Stream.of(raiz), raiz instanceof Parent pai
                ? pai.getChildrenUnmodifiable().stream().flatMap(this::descendentes) : Stream.empty());
    }

    private List<Button> cartoes() {
        return descendentes(tela.getRaiz()).filter(n -> n instanceof Button)
                .map(n -> (Button) n).toList();
    }

    private boolean texto(String esperado) {
        return descendentes(tela.getRaiz()).filter(n -> n instanceof Label)
                .anyMatch(n -> ((Label) n).getText().equals(esperado));
    }

    @Test void refleteVisoesDoServidorELimiteSemAnteciparEscolhas() throws Exception {
        criar(null);
        // Simula callback na thread RMI, e aguarda a fila JavaFX.
        tela.atualizar(estado(List.of(1), Set.of(2), 23));
        fx(() -> {
            assertEquals(10, cartoes().size());
            assertTrue(cartoes().get(0).getStyleClass().contains("criatura-minha"));
            assertTrue(cartoes().get(1).getStyleClass().contains("criatura-adversaria"));
            assertTrue(cartoes().get(1).isDisabled());
            assertFalse(cartoes().get(2).isDisabled());
            assertTrue(texto("00:23"));
            tela.atualizar(estado(List.of(1, 3, 4), Set.of(2), 18));
            assertTrue(cartoes().stream().allMatch(Node::isDisabled));
        });
    }

    @Test void chamadaLentaNaoBloqueiaInterfaceENaoMarcaEscolhaLocal() throws Exception {
        CountDownLatch entrou = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicBoolean foraDaUI = new AtomicBoolean();
        ServicoJogo servico = (ServicoJogo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ServicoJogo.class}, (proxy, metodo, args) -> {
                    assertEquals("escolherCriatura", metodo.getName());
                    assertArrayEquals(new Object[]{"sessao-teste", 1}, args);
                    foraDaUI.set(!Platform.isFxApplicationThread());
                    entrou.countDown();
                    assertTrue(liberar.await(10, TimeUnit.SECONDS));
                    return null;
                });
        criar(servico);
        try {
            fx(() -> { tela.atualizar(estado(List.of(), Set.of(), 20)); cartoes().get(0).fire(); });
            assertTrue(entrou.await(5, TimeUnit.SECONDS));
            fx(() -> {
                assertTrue(foraDaUI.get());
                assertTrue(cartoes().get(0).isDisabled());
                assertFalse(cartoes().get(0).getStyleClass().contains("criatura-minha"));
                assertFalse(cartoes().get(1).isDisabled());
            });
        } finally { liberar.countDown(); }
    }

    @Test void mostraErrosDeDisputaELimiteComAvisoTemporario() throws Exception {
        AtomicBoolean limite = new AtomicBoolean();
        ServicoJogo servico = (ServicoJogo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ServicoJogo.class}, (proxy, metodo, args) -> {
                    if (limite.get()) throw new LimiteDeEscolhasException("limite");
                    throw new CriaturaIndisponivelException("disputa");
                });
        criar(servico);
        fx(() -> { tela.atualizar(estado(List.of(), Set.of(), 20)); cartoes().get(0).fire(); });
        aguardarTexto("Criatura 1 já foi escolhida pelo adversário!");
        limite.set(true);
        fx(() -> cartoes().get(1).fire());
        aguardarTexto("Você já tem 3 criaturas");
        Thread.sleep(3300);
        fx(() -> assertFalse(texto("Você já tem 3 criaturas")));
    }

    private void aguardarTexto(String mensagem) throws Exception {
        long prazo = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        AtomicBoolean encontrou = new AtomicBoolean();
        while (!encontrou.get() && System.nanoTime() < prazo) {
            fx(() -> encontrou.set(texto(mensagem)));
            if (!encontrou.get()) Thread.sleep(20);
        }
        assertTrue(encontrou.get(), mensagem);
    }

    @Test void relogioDecrementaRessincronizaENaoConcluiFaseSozinho() throws Exception {
        criar(null);
        fx(() -> tela.atualizar(estado(List.of(), Set.of(), 2)));
        Thread.sleep(1250);
        fx(() -> assertTrue(texto("00:01")));
        fx(() -> tela.atualizar(estado(List.of(), Set.of(), 23)));
        fx(() -> {
            assertTrue(texto("00:23"));
            tela.atualizar(estado(List.of(), Set.of(), 0));
            assertFalse(texto("Draft concluído"));
            assertTrue(cartoes().stream().noneMatch(Node::isDisabled));
            tela.encerrar();
            tela.atualizar(estado(List.of(1), Set.of(), 30));
            assertTrue(texto("00:00"));
        });
    }

    @Test void lobbyEncaminhaCallbacksNaOrdemPelaThreadJavaFX() throws Exception {
        criar(null);
        List<String> eventos = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<TelaLobby> lobby = new java.util.concurrent.atomic.AtomicReference<>();
        fx(() -> {
            TelaLobby l = new TelaLobby("A", "localhost", new ConexaoServidor("localhost", 1099, null));
            l.setAoPartidaEncontrada(info -> {
                assertTrue(Platform.isFxApplicationThread());
                eventos.add("partida");
            });
            l.setAoDraftAtualizado(estado -> {
                assertTrue(Platform.isFxApplicationThread());
                tela.atualizar(estado);
                eventos.add("draft");
            });
            l.setAoBatalhaIniciada(estado -> {
                assertTrue(Platform.isFxApplicationThread());
                tela.encerrar();
                eventos.add("batalha");
            });
            lobby.set(l);
        });
        lobby.get().partidaEncontrada(new PartidaInfo("partida", "B"));
        lobby.get().draftAtualizado(estado(List.of(1), Set.of(2), 5));
        lobby.get().batalhaIniciada(new EstadoBatalha(1, null, List.of(), null, 2, 30));
        fx(() -> assertEquals(List.of("partida", "draft", "batalha"), eventos));
    }
}
