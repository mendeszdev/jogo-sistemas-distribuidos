package br.ucsal.sd.arena.cliente.ui;
import br.ucsal.sd.arena.cliente.rede.*;
import br.ucsal.sd.arena.compartilhado.dto.*;
import br.ucsal.sd.arena.compartilhado.remoto.*;
import br.ucsal.sd.arena.servidor.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public class DraftRmiTest {
    static void fx(Runnable r) throws Exception {
        FutureTask<Void> f = new FutureTask<>(r, null); Platform.runLater(f); f.get(10, TimeUnit.SECONDS);
    }
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
    static java.util.stream.Stream<Node> nodes(Node n) {
        if (n instanceof ScrollPane s) return java.util.stream.Stream.concat(java.util.stream.Stream.of(n), nodes(s.getContent()));
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(n), n instanceof Parent p ? p.getChildrenUnmodifiable().stream().flatMap(DraftRmiTest::nodes) : java.util.stream.Stream.empty());
    }
    @org.junit.jupiter.api.Test void disputaETimeoutComDoisClientesRmi() throws Exception {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        try { Platform.startup(() -> Platform.setImplicitExit(false)); } catch (IllegalStateException jaIniciado) { }
        NotificadorCallbacks notificacoes = new NotificadorCallbacks();
        ServicoJogoImpl servidor = new ServicoJogoImpl(notificacoes);
        int porta;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            porta = socket.getLocalPort();
        }
        Registry registry = LocateRegistry.createRegistry(porta);
        registry.rebind(ServicoJogo.NOME_NO_REGISTRY, servidor);
        ConexaoServidor[] conexoes = {new ConexaoServidor("127.0.0.1", porta, null), new ConexaoServidor("127.0.0.1", porta, null)};
        TelaDraft[] telas = new TelaDraft[2];
        AtomicReferenceArray<EstadoDraft> estados = new AtomicReferenceArray<>(2);
        CountDownLatch inicial = new CountDownLatch(2);
        try {
            fx(() -> { for (int i=0;i<2;i++) telas[i] = new TelaDraft(conexoes[i], "Oponente"); });
            for (int i=0;i<2;i++) {
                final int indice=i;
                // Mesmo ouvinte fixo do cliente real: lobby encaminha os eventos para a tela.
                AtomicReference<TelaLobby> lobby = new AtomicReference<>();
                fx(() -> {
                    TelaLobby l = new TelaLobby("Teste"+indice, "localhost", conexoes[indice]);
                    l.setAoDraftAtualizado(e -> { telas[indice].atualizar(e); estados.set(indice,e); inicial.countDown(); });
                    lobby.set(l);
                });
                conexoes[i].conectar("Teste"+i, lobby.get());
            }
            for (ConexaoServidor c : conexoes) c.getServico().buscarPartida(c.getToken());
            check(inicial.await(10,TimeUnit.SECONDS), "estado inicial RMI");
            fx(() -> {
                for (TelaDraft t : telas) nodes(t.getRaiz()).filter(n -> n instanceof Button).map(n -> (Button)n).findFirst().orElseThrow().fire();
            });
            long prazo = System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            AtomicBoolean aviso = new AtomicBoolean();
            while (System.nanoTime()<prazo && !aviso.get()) {
                fx(() -> aviso.set(java.util.Arrays.stream(telas).anyMatch(t -> nodes(t.getRaiz()).anyMatch(n -> n instanceof Label l && l.getText().contains("já foi escolhida pelo adversário")))));
                Thread.sleep(20);
            }
            check(aviso.get(), "perdedor vê aviso de disputa RMI");
            check(estados.get(0).minhasEscolhas().size()+estados.get(1).minhasEscolhas().size()==1, "um único vencedor");
            check(estados.get(0).escolhasAdversario().size()+estados.get(1).escolhasAdversario().size()==1, "marcação adversário");
            fx(() -> {
                Scene scene = new Scene(telas[0].getRaiz(),960,1080);
                scene.getRoot().applyCss(); scene.getRoot().layout();
                var snap = scene.getRoot().snapshot(null,null);
                BufferedImage img = new BufferedImage((int)snap.getWidth(),(int)snap.getHeight(),BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<img.getHeight();y++) for(int x=0;x<img.getWidth();x++) img.setRGB(x,y,snap.getPixelReader().getArgb(x,y));
                try { ImageIO.write(img,"png",new File("target/draft-preview.png")); } catch(Exception e) { throw new RuntimeException(e); }
            });
            prazo = System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
            while (System.nanoTime()<prazo && (estados.get(0).minhasEscolhas().size()!=3 || estados.get(1).minhasEscolhas().size()!=3)) Thread.sleep(100);
            check(estados.get(0).minhasEscolhas().size()==3 && estados.get(1).minhasEscolhas().size()==3,"timeout completa times");
            check(estados.get(0).segundosRestantes()==0,"timeout autoritativo");
            fx(() -> {
                for (TelaDraft t : telas) {
                    check(nodes(t.getRaiz()).anyMatch(n -> n instanceof Label l && l.getText().startsWith("Draft concluído")), "conclusão confirmada pelo servidor");
                }
            });
            System.out.println("PASS: 2 clientes RMI; disputa simultânea; aviso; marcação adversário; timeout; renderização.");
        } finally {
            for (ConexaoServidor c:conexoes) c.desconectar();
            fx(() -> {for(TelaDraft t:telas) if(t!=null)t.encerrar();});
            notificacoes.encerrar();
            UnicastRemoteObject.unexportObject(servidor,true);
            UnicastRemoteObject.unexportObject(registry,true);
            
        }
    }
}

