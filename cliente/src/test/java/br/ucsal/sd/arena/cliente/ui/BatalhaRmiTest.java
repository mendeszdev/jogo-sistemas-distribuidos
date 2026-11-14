package br.ucsal.sd.arena.cliente.ui;

import br.ucsal.sd.arena.cliente.rede.ConexaoServidor;
import br.ucsal.sd.arena.compartilhado.dto.*;
import br.ucsal.sd.arena.compartilhado.remoto.ServicoJogo;
import br.ucsal.sd.arena.servidor.NotificadorCallbacks;
import br.ucsal.sd.arena.servidor.ServicoJogoImpl;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatalhaRmiTest {
    @Test
    void doisClientesJogamDaSelecaoAteResultadoERetornoAoLobby() throws Exception {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        CountDownLatch plataforma = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); plataforma.countDown(); }); }
        catch (IllegalStateException e) { Platform.runLater(plataforma::countDown); }
        assertTrue(plataforma.await(10, TimeUnit.SECONDS));
        NotificadorCallbacks notificacoes = new NotificadorCallbacks();
        ServicoJogoImpl servidor = new ServicoJogoImpl(notificacoes);
        int porta;
        try (ServerSocket socket = new ServerSocket(0)) { porta = socket.getLocalPort(); }
        Registry registry = LocateRegistry.createRegistry(porta);
        registry.rebind(ServicoJogo.NOME_NO_REGISTRY, servidor);
        ConexaoServidor[] conexoes = {new ConexaoServidor("127.0.0.1", porta, null),
                new ConexaoServidor("127.0.0.1", porta, null)};
        TelaBatalha[] telas = new TelaBatalha[2];
        Scene[] cenas = new Scene[2];
        AtomicReferenceArray<List<Criatura>> catalogos = new AtomicReferenceArray<>(2);
        CountDownLatch iniciou = new CountDownLatch(2);
        CountDownLatch terminou = new CountDownLatch(2);
        List<BlockingQueue<ResultadoRodada>> rodadas = List.of(new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>());
        AtomicReferenceArray<ResultadoPartida> resultados = new AtomicReferenceArray<>(2);
        AtomicBoolean voltou = new AtomicBoolean();
        AtomicBoolean buscou = new AtomicBoolean();
        try {
            for (int i = 0; i < 2; i++) {
                int indice = i;
                TelaLobby[] lobby = new TelaLobby[1];
                DraftRmiTest.fx(() -> {
                    lobby[0] = new TelaLobby(iNome(indice), "localhost", conexoes[indice]);
                    lobby[0].setAoDraftAtualizado(e -> catalogos.set(indice, e.pool()));
                    lobby[0].setAoBatalhaIniciada(e -> {
                        telas[indice] = new TelaBatalha(conexoes[indice], catalogos.get(indice));
                        telas[indice].atualizar(e);
                        cenas[indice] = new Scene(telas[indice].getRaiz(), 960, 600);
                        iniciou.countDown();
                    });
                    lobby[0].setAoRodadaResolvida(e -> {
                        telas[indice].rodadaResolvida(e);
                        rodadas.get(indice).add(e);
                    });
                    lobby[0].setAoPartidaEncerrada(e -> {
                        telas[indice].encerrar();
                        resultados.set(indice, e);
                        cenas[indice] = new Scene(new TelaResultado(iNome(indice), e, catalogos.get(indice),
                                () -> voltou.set(true), () -> buscou.set(true)).getRaiz(), 960, 600);
                        terminou.countDown();
                    });
                });
                conexoes[i].conectar(iNome(i), lobby[0]);
            }
            for (ConexaoServidor c : conexoes) c.getServico().buscarPartida(c.getToken());
            for (int id = 1; id <= 3; id++) conexoes[0].getServico().escolherCriatura(conexoes[0].getToken(), id);
            for (int id = 4; id <= 6; id++) conexoes[1].getServico().escolherCriatura(conexoes[1].getToken(), id);
            assertTrue(iniciou.await(10, TimeUnit.SECONDS));
            DraftRmiTest.fx(() -> {
                assertTrue(botoes(telas[0].getRaiz()).stream().noneMatch(Node::isDisabled));
                botoes(telas[0].getRaiz()).stream().filter(b -> b.getText().startsWith("Trocar:"))
                        .findFirst().orElseThrow().fire();
                botoes(telas[1].getRaiz()).get(3).fire();
            });
            ResultadoRodada primeira = receber(rodadas.get(0));
            receber(rodadas.get(1));
            assertEquals(new EstadoCriaturaEmBatalha(2, 47), primeira.estadoAtual().minhaAtiva());
            assertEquals(85, primeira.estadoAtual().ativaAdversario().hpAtual());
            DraftRmiTest.fx(() -> {
                assertTrue(texto(telas[0].getRaiz(), "HP 47 / 65"));
                assertTrue(texto(telas[0].getRaiz(), "Batalha · Rodada 2"));
                salvar(cenas[0], "target/batalha-integrada-preview.png");
            });
            for (int numero = 2; numero <= 6; numero++) {
                DraftRmiTest.fx(() -> {
                    botoes(telas[0].getRaiz()).get(3).fire();
                    botoes(telas[1].getRaiz()).get(3).fire();
                });
                assertEquals(numero, receber(rodadas.get(0)).numeroRodada());
                receber(rodadas.get(1));
            }
            assertTrue(terminou.await(10, TimeUnit.SECONDS));
            assertEquals("Alice", resultados.get(0).vencedor());
            assertEquals(6, resultados.get(0).rodadasJogadas());
            assertEquals(resultados.get(0).meuTime(), resultados.get(1).timeAdversario());
            assertTrue(conexoes[0].getServico().listarJogadoresOnline(conexoes[0].getToken()).stream()
                    .allMatch(j -> j.statusJogador() == StatusJogador.LIVRE));
            DraftRmiTest.fx(() -> {
                assertTrue(texto(cenas[0].getRoot(), "Vitória"));
                assertTrue(texto(cenas[1].getRoot(), "Derrota"));
                assertTrue(texto(cenas[0].getRoot(), "Rodadas jogadas: 6"));
                assertTrue(texto(cenas[0].getRoot(), "Criaturas adversárias derrubadas: 3"));
                salvar(cenas[0], "target/resultado-integrado-preview.png");
                botoes(cenas[0].getRoot()).get(0).fire();
                botoes(cenas[0].getRoot()).get(1).fire();
                assertTrue(voltou.get());
                assertTrue(buscou.get());
            });
        } finally {
            for (ConexaoServidor c : conexoes) c.desconectar();
            DraftRmiTest.fx(() -> { for (TelaBatalha tela : telas) if (tela != null) tela.encerrar(); });
            notificacoes.encerrar();
            UnicastRemoteObject.unexportObject(servidor, true);
            UnicastRemoteObject.unexportObject(registry, true);
        }
    }

    private static String iNome(int indice) { return indice == 0 ? "Alice" : "Bruno"; }

    private static ResultadoRodada receber(BlockingQueue<ResultadoRodada> fila) throws InterruptedException {
        ResultadoRodada resultado = fila.poll(10, TimeUnit.SECONDS);
        assertNotNull(resultado, "A rodada não foi recebida via RMI");
        return resultado;
    }

    private static List<Button> botoes(Node raiz) {
        return DraftRmiTest.nodes(raiz).filter(Button.class::isInstance).map(Button.class::cast).toList();
    }

    private static boolean texto(Node raiz, String texto) {
        return DraftRmiTest.nodes(raiz).anyMatch(n -> n instanceof Label l && l.getText().equals(texto));
    }

    private static void salvar(Scene cena, String arquivo) {
        cena.getRoot().applyCss();
        cena.getRoot().resize(cena.getWidth(), cena.getHeight());
        cena.getRoot().layout();
        if (arquivo.contains("resultado")) {
            assertTrue(cena.getRoot().getChildrenUnmodifiable().getFirst().getBoundsInParent().getMinY() >= 24);
            for (var filho : cena.getRoot().getChildrenUnmodifiable()) {
                assertTrue(filho.getBoundsInParent().getMaxY() <= cena.getHeight(), "Conteúdo fora da tela de resultado");
            }
        }
        var captura = cena.getRoot().snapshot(null, null);
        BufferedImage imagem = new BufferedImage((int) captura.getWidth(), (int) captura.getHeight(), BufferedImage.TYPE_INT_ARGB);
        captura.getPixelReader().getPixels(0, 0, imagem.getWidth(), imagem.getHeight(),
                javafx.scene.image.PixelFormat.getIntArgbInstance(),
                ((java.awt.image.DataBufferInt) imagem.getRaster().getDataBuffer()).getData(), 0, imagem.getWidth());
        try { ImageIO.write(imagem, "png", new File(arquivo)); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
