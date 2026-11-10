package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.remoto.ClienteCallback;
import java.util.concurrent.atomic.AtomicReference;
import br.ucsal.sd.arena.compartilhado.dto.StatusJogador;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;

/** Sessão de um jogador conectado: identidade, callback remoto e último sinal de vida. */
public final class SessaoJogador {

    private final String nome;
    private final String token;
    private final ClienteCallback callback;
    private final FilaSerial filaNotificacoes = new FilaSerial();
    private volatile Instant ultimoHeartbeat;
    private final AtomicReference<StatusJogador> status = new AtomicReference<>(StatusJogador.LIVRE);
    private volatile Partida partida;

    public SessaoJogador(String nome, String token, ClienteCallback callback) {
        this.nome = nome;
        this.token = token;
        this.callback = callback;
        this.ultimoHeartbeat = Instant.now();
    }

    public String getNome() {
        return nome;
    }

    public String getToken() {
        return token;
    }

    public ClienteCallback getCallback() {
        return callback;
    }

    /** Fila que serializa as notificações desta sessão (uso do {@link NotificadorCallbacks}). */
    FilaSerial getFilaNotificacoes() {
        return filaNotificacoes;
    }

    public Instant getUltimoHeartbeat() {
        return ultimoHeartbeat;
    }

    public void registrarHeartbeat() {
        this.ultimoHeartbeat = Instant.now();
    }

    public StatusJogador getStatus() {
        return status.get();
    }

    /** Troca o status só se o atual for {@code esperado} (compareAndSet); devolve se a troca ocorreu. */
        public boolean trocarStatus(StatusJogador esperado, StatusJogador novo) {
            return status.compareAndSet(esperado, novo);
        }

    /** Partida em que o jogador está, ou null. */
        public Partida getPartida() {
            return partida;
        }
        public void setPartida(Partida partida) {
                this.partida = partida;
           }
}
