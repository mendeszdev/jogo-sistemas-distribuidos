package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
import br.ucsal.sd.arena.compartilhado.dto.Criatura;
import br.ucsal.sd.arena.compartilhado.dto.EstadoBatalha;
import br.ucsal.sd.arena.compartilhado.dto.EstadoCriaturaEmBatalha;
import br.ucsal.sd.arena.compartilhado.dto.EstadoDraft;
import br.ucsal.sd.arena.compartilhado.dto.FasePartida;
import br.ucsal.sd.arena.compartilhado.dto.PartidaInfo;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoPartida;
import br.ucsal.sd.arena.compartilhado.excecao.AcaoInvalidaException;
import br.ucsal.sd.arena.compartilhado.excecao.CriaturaIndisponivelException;
import br.ucsal.sd.arena.compartilhado.excecao.FaseInvalidaException;
import br.ucsal.sd.arena.compartilhado.excecao.ForaDaRodadaException;
import br.ucsal.sd.arena.compartilhado.excecao.LimiteDeEscolhasException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Estado autoritativo de uma sala. Todo estado mutável do draft é protegido por lock. */
public final class Partida {
    private static final ScheduledThreadPoolExecutor TIMERS = criarTimers();
    private final Object lock = new Object();
    private final List<Criatura> pool = new ArrayList<>(CatalogoCriaturas.listar());
    private final Set<Integer> escolhasA = new LinkedHashSet<>();
    private final Set<Integer> escolhasB = new LinkedHashSet<>();
    private final ScheduledExecutorService scheduler;
    private final long duracaoNanos;
    private boolean iniciada;
    private long prazoNanos;
    private long epoca;
    private ScheduledFuture<?> timerDraft;
    private final String id;
    private final SessaoJogador jogadorA;
    private final SessaoJogador jogadorB;
    private final NotificadorCallbacks notificador;
    private volatile FasePartida fase = FasePartida.DRAFT;

    public Partida(String id, SessaoJogador jogadorA, SessaoJogador jogadorB, NotificadorCallbacks notificador) {
        this(id, jogadorA, jogadorB, notificador, TIMERS, Duration.ofSeconds(30));
    }

    // Scheduler e duração injetáveis para testar sem esperar 30 segundos.
    Partida(String id, SessaoJogador jogadorA, SessaoJogador jogadorB, NotificadorCallbacks notificador,
            ScheduledExecutorService scheduler, Duration duracao) {
        this.id = Objects.requireNonNull(id);
        this.jogadorA = Objects.requireNonNull(jogadorA);
        this.jogadorB = Objects.requireNonNull(jogadorB);
        this.notificador = Objects.requireNonNull(notificador);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.duracaoNanos = duracao.toNanos();
        if (duracaoNanos <= 0 || jogadorA == jogadorB) {
            throw new IllegalArgumentException("Duração positiva e dois jogadores distintos são obrigatórios");
        }
    }

    public String getId() {
        return id;
    }

    public SessaoJogador getJogadorA() {
        return jogadorA;
    }

    public SessaoJogador getJogadorB() {
        return jogadorB;
    }

    public FasePartida getFase() {
        return fase;
    }

    public boolean contem(SessaoJogador jogador) {
        return jogador == jogadorA || jogador == jogadorB;
    }

    public SessaoJogador getAdversario(SessaoJogador jogador) {
        if (jogador == jogadorA) {
            return jogadorB;
        }
        if (jogador == jogadorB) {
            return jogadorA;
        }
        throw new IllegalArgumentException("Jogador não pertence à partida " + id);
    }

    public void iniciar() {
        synchronized (lock) {
            if (iniciada || fase != FasePartida.DRAFT) return;
            long epocaDraft = ++epoca;
            prazoNanos = System.nanoTime() + duracaoNanos;
            timerDraft = scheduler.schedule(() -> fecharDraftPorTimeout(epocaDraft), duracaoNanos,
                    TimeUnit.NANOSECONDS);
            iniciada = true;
            notificador.notificar(jogadorA, cb -> cb.partidaEncontrada(new PartidaInfo(id, jogadorB.getNome())));
            notificador.notificar(jogadorB, cb -> cb.partidaEncontrada(new PartidaInfo(id, jogadorA.getNome())));
            notificarDraft();
        }
    }

    public void escolherCriatura(SessaoJogador jogador, int idCriatura) throws FaseInvalidaException,
            CriaturaIndisponivelException, LimiteDeEscolhasException {
        synchronized (lock) {
            if (!iniciada || fase != FasePartida.DRAFT) {
                throw new FaseInvalidaException("A partida não está em draft ativo");
            }
            Set<Integer> escolhas = escolhasDe(jogador);
            if (pool.stream().noneMatch(c -> c.id() == idCriatura)
                    || escolhasA.contains(idCriatura) || escolhasB.contains(idCriatura)) {
                throw new CriaturaIndisponivelException("Criatura indisponível: " + idCriatura);
            }
            if (escolhas.size() == 3) {
                throw new LimiteDeEscolhasException("O jogador já escolheu 3 criaturas");
            }
            escolhas.add(idCriatura);
            if (escolhasA.size() == 3 && escolhasB.size() == 3) terminarDraft();
            notificarDraft();
        }
    }

    /** Timer antigo, cancelado ou posterior ao W.O. não altera mais o estado. */
    void fecharDraftPorTimeout(long epocaEsperada) {
        synchronized (lock) {
            if (!iniciada || fase != FasePartida.DRAFT || epoca != epocaEsperada) return;
            List<Integer> livres = new ArrayList<>();
            for (Criatura criatura : pool) {
                if (!escolhasA.contains(criatura.id()) && !escolhasB.contains(criatura.id())) {
                    livres.add(criatura.id());
                }
            }
            Collections.shuffle(livres);
            for (Set<Integer> escolhas : List.of(escolhasA, escolhasB)) {
                while (escolhas.size() < 3) {
                    escolhas.add(livres.remove(livres.size() - 1));
                    if (escolhasA.size() == 3 && escolhasB.size() == 3) terminarDraft();
                    notificarDraft();
                }
            }
        }
    }

    public EstadoDraft getEstadoDraft(SessaoJogador jogador) {
        synchronized (lock) {
            return estadoDraft(jogador);
        }
    }

    private Set<Integer> escolhasDe(SessaoJogador jogador) {
        if (jogador == jogadorA) return escolhasA;
        if (jogador == jogadorB) return escolhasB;
        throw new IllegalArgumentException("Jogador não pertence à partida " + id);
    }

    private EstadoDraft estadoDraft(SessaoJogador jogador) {
        long restante = iniciada && fase == FasePartida.DRAFT ? Math.max(0, prazoNanos - System.nanoTime()) : 0;
        int segundos = (int) Math.min(Integer.MAX_VALUE, TimeUnit.NANOSECONDS.toSeconds(restante)
                + (restante % TimeUnit.SECONDS.toNanos(1) == 0 ? 0 : 1));
        return new EstadoDraft(pool, new ArrayList<>(escolhasDe(jogador)),
                escolhasDe(getAdversario(jogador)), segundos);
    }

    private void notificarDraft() {
        // Captura e enfileiramento sob lock preservam a ordem. O envio remoto usa o executor.
        EstadoDraft estadoA = estadoDraft(jogadorA);
        EstadoDraft estadoB = estadoDraft(jogadorB);
        notificador.notificar(jogadorA, cb -> cb.draftAtualizado(estadoA));
        notificador.notificar(jogadorB, cb -> cb.draftAtualizado(estadoB));
    }

    private void terminarDraft() {
        if (!transitar(FasePartida.DRAFT, FasePartida.BATALHA)) return;
        cancelarTimer();
        EstadoBatalha estadoA = estadoBatalhaInicial(jogadorA);
        EstadoBatalha estadoB = estadoBatalhaInicial(jogadorB);
        notificador.notificar(jogadorA, cb -> cb.batalhaIniciada(estadoA));
        notificador.notificar(jogadorB, cb -> cb.batalhaIniciada(estadoB));
    }

    private EstadoBatalha estadoBatalhaInicial(SessaoJogador jogador) {
        List<Integer> minhas = new ArrayList<>(escolhasDe(jogador));
        List<Integer> adversarias = new ArrayList<>(escolhasDe(getAdversario(jogador)));
        EstadoCriaturaEmBatalha ativa = emBatalha(minhas.get(0));
        List<EstadoCriaturaEmBatalha> reservas = List.of(emBatalha(minhas.get(1)), emBatalha(minhas.get(2)));
        EstadoCriaturaEmBatalha ativaAdversario = emBatalha(adversarias.get(0));
        // Timer da primeira rodada será iniciado quando enviarAcao for implementado (card 19 pendente).
        return new EstadoBatalha(1, ativa, reservas, ativaAdversario, 2, 0);
    }

    private EstadoCriaturaEmBatalha emBatalha(int idCriatura) {
        Criatura criatura = pool.stream().filter(c -> c.id() == idCriatura).findFirst()
                .orElseThrow(() -> new IllegalStateException("Criatura ausente do pool: " + idCriatura));
        return new EstadoCriaturaEmBatalha(criatura.id(), criatura.hpMax());
    }

    private boolean transitar(FasePartida de, FasePartida para) {
        assert Thread.holdsLock(lock);
        if (fase != de) return false;
        fase = para;
        epoca++;
        return true;
    }

    private void cancelarTimer() {
        if (timerDraft != null) timerDraft.cancel(false);
    }

    private static ScheduledThreadPoolExecutor criarTimers() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, tarefa -> {
            Thread thread = new Thread(tarefa, "timer-draft");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    public void enviarAcao(SessaoJogador jogador, Acao acao) throws FaseInvalidaException,
            ForaDaRodadaException, AcaoInvalidaException {
        throw new UnsupportedOperationException("TODO card 19");
    }
    /**
     * W.O.: o jogador desconectado perde e o adversário vence. Idempotente: só a primeira chamada encerra.
     *
     * @return true se esta chamada encerrou a partida
     */
    public boolean encerrarPorWO(SessaoJogador desconectado) {
        synchronized (lock) {
            SessaoJogador vencedor = getAdversario(desconectado);
            if (fase == FasePartida.ENCERRADA) return false;
            transitar(fase, FasePartida.ENCERRADA);
            cancelarTimer();
            // TODO card 19: preencher rodadasJogadas e os times finais da batalha
            ResultadoPartida resultado = new ResultadoPartida(vencedor.getNome(), true, 0, List.of(), List.of());
            notificador.notificar(vencedor, cb -> cb.partidaEncerrada(resultado));
            return true;
        }
    }
}
