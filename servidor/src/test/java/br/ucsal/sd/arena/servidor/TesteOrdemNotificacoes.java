package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.dto.EstadoBatalha;
import br.ucsal.sd.arena.compartilhado.dto.EstadoDraft;
import br.ucsal.sd.arena.compartilhado.dto.PartidaInfo;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoPartida;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoRodada;
import br.ucsal.sd.arena.compartilhado.remoto.ClienteCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** Teste sem framework: {@code java -cp ... TesteOrdemNotificacoes}. Sai com código 1 se falhar. */
public final class TesteOrdemNotificacoes {

    private static final int TOTAL = 50;

    /** Callback falso (sem RMI) que registra a ordem de chegada e simula latência variável. */
    private static class Registrador implements ClienteCallback {
        final List<Integer> recebidos = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch fim = new CountDownLatch(TOTAL);
        final long atrasoMaxMs;

        Registrador(long atrasoMaxMs) {
            this.atrasoMaxMs = atrasoMaxMs;
        }

        @Override
        public void jogadoresOnlineAtualizados(List<String> nomes) {
            try {
                if (atrasoMaxMs > 0) {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(atrasoMaxMs + 1));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            recebidos.add(Integer.parseInt(nomes.get(0)));
            fim.countDown();
        }

        @Override public void partidaEncontrada(PartidaInfo i) { }
        @Override public void draftAtualizado(EstadoDraft e) { }
        @Override public void batalhaIniciada(EstadoBatalha e) { }
        @Override public void rodadaResolvida(ResultadoRodada r) { }
        @Override public void partidaEncerrada(ResultadoPartida r) { }
        @Override public void adversarioDesconectado(int s) { }
    }

    public static void main(String[] args) throws Exception {
        NotificadorCallbacks notificador = new NotificadorCallbacks();
        boolean ok = true;
        try {
            Registrador rapido = new Registrador(0);
            Registrador aleatorio = new Registrador(5);
            Registrador lento = new Registrador(0) { // 100 ms fixos por evento: ~5 s no total
                @Override
                public void jogadoresOnlineAtualizados(List<String> nomes) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    super.jogadoresOnlineAtualizados(nomes);
                }
            };
            SessaoJogador sRapido = new SessaoJogador("Rapido", "t1", rapido);
            SessaoJogador sAleatorio = new SessaoJogador("Aleatorio", "t2", aleatorio);
            SessaoJogador sLento = new SessaoJogador("Lento", "t3", lento);

            long inicio = System.nanoTime();
            for (int i = 0; i < TOTAL; i++) {
                final List<String> lista = List.of(String.valueOf(i));
                for (SessaoJogador s : List.of(sRapido, sAleatorio, sLento)) {
                    notificador.notificar(s, cb -> cb.jogadoresOnlineAtualizados(lista));
                }
            }
            rapido.fim.await(10, TimeUnit.SECONDS);
            long rapidoMs = (System.nanoTime() - inicio) / 1_000_000;
            aleatorio.fim.await(10, TimeUnit.SECONDS);
            lento.fim.await(15, TimeUnit.SECONDS);
            long lentoMs = (System.nanoTime() - inicio) / 1_000_000;

            List<Integer> esperado = new ArrayList<>();
            for (int i = 0; i < TOTAL; i++) {
                esperado.add(i);
            }
            ok &= verificar("cliente rápido", rapido.recebidos, esperado);
            ok &= verificar("cliente com latência aleatória (0-5 ms)", aleatorio.recebidos, esperado);
            ok &= verificar("cliente lento (100 ms/evento)", lento.recebidos, esperado);

            boolean paralelo = rapidoMs < lentoMs / 2;
            System.out.printf("rápido terminou em %d ms; lento em %d ms -> %s%n", rapidoMs, lentoMs,
                    paralelo ? "OK (cliente lento não atrasou o rápido)" : "FALHOU (envios não estão em paralelo)");
            ok &= paralelo;

            // Liberação: após encerrar a fila, novos envios são ignorados.
            Registrador removido = new Registrador(0);
            SessaoJogador sRemovido = new SessaoJogador("Removido", "t4", removido);
            notificador.liberar(sRemovido);
            notificador.notificar(sRemovido, cb -> cb.jogadoresOnlineAtualizados(List.of("0")));
            Thread.sleep(200);
            boolean liberado = removido.recebidos.isEmpty();
            System.out.println("envio após liberar a fila ignorado -> " + (liberado ? "OK" : "FALHOU"));
            ok &= liberado;
        } finally {
            notificador.encerrar();
        }
        System.out.println(ok ? "RESULTADO: PASSOU" : "RESULTADO: FALHOU");
        System.exit(ok ? 0 : 1);
    }

    private static boolean verificar(String nome, List<Integer> recebidos, List<Integer> esperado) {
        boolean ok;
        synchronized (recebidos) {
            ok = recebidos.equals(esperado);
        }
        System.out.println(nome + ": recebeu " + recebidos.size() + "/" + TOTAL + " em ordem -> "
                + (ok ? "OK" : "FALHOU " + recebidos));
        return ok;
    }
}
