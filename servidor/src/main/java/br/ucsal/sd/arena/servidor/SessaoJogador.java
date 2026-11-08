package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.remoto.ClienteCallback;

import java.time.Instant;

/** Sessão de um jogador conectado: identidade, callback remoto e último sinal de vida. */
public final class SessaoJogador {

    private final String nome;
    private final String token;
    private final ClienteCallback callback;
    private final FilaSerial filaNotificacoes = new FilaSerial();
    private volatile Instant ultimoHeartbeat;

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
}
