package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
import br.ucsal.sd.arena.compartilhado.dto.Criatura;
import br.ucsal.sd.arena.compartilhado.dto.EstadoBatalha;
import br.ucsal.sd.arena.compartilhado.dto.EstadoDraft;
import br.ucsal.sd.arena.compartilhado.dto.FasePartida;
import br.ucsal.sd.arena.compartilhado.dto.PartidaInfo;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoPartida;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoRodada;
import br.ucsal.sd.arena.servidor.batalha.MotorBatalha;
import br.ucsal.sd.arena.servidor.batalha.PoliticaAcaoPadrao;
import br.ucsal.sd.arena.servidor.batalha.RodadaSimultanea;
import br.ucsal.sd.arena.servidor.batalha.TimerTurno;
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
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Estado autoritativo de uma sala. Draft, motor e transições são protegidos pelo monitor da partida. */
public final class Partida {
    private static final ScheduledThreadPoolExecutor TIMERS = criarTimers();
    private final Object lock = new Object();
    private final List<Criatura> pool = new ArrayList<>(CatalogoCriaturas.listar());
    private final Set<Integer> escolhasA = new LinkedHashSet<>();
    private final Set<Integer> escolhasB = new LinkedHashSet<>();
    private final ScheduledExecutorService scheduler;
    private final long duracaoNanos;
    private final Duration duracaoTurno;
    private final Consumer<Partida> aoEncerrar;
    private TimerTurno timerTurno;
    private RodadaSimultanea rodada;
    private MotorBatalha motor;
    private long prazoTurnoNanos;
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

    public Partida(String id, SessaoJogador jogadorA, SessaoJogador jogadorB, NotificadorCallbacks notificador,
                   Consumer<Partida> aoEncerrar) {
        this(id, jogadorA, jogadorB, notificador, TIMERS, Duration.ofSeconds(30),
                TimerTurno.DURACAO_PADRAO, aoEncerrar);
    }

    // Scheduler e duração injetáveis para testar sem esperar 30 segundos.
    Partida(String id, SessaoJogador jogadorA, SessaoJogador jogadorB, NotificadorCallbacks notificador,
            ScheduledExecutorService scheduler, Duration duracao) {
        this(id, jogadorA, jogadorB, notificador, scheduler, duracao, TimerTurno.DURACAO_PADRAO, p -> { });
    }

    Partida(String id, SessaoJogador jogadorA, SessaoJogador jogadorB, NotificadorCallbacks notificador,
            ScheduledExecutorService scheduler, Duration duracao, Duration duracaoTurno, Consumer<Partida> aoEncerrar) {
        this.id = Objects.requireNonNull(id);
        this.jogadorA = Objects.requireNonNull(jogadorA);
        this.jogadorB = Objects.requireNonNull(jogadorB);
        this.notificador = Objects.requireNonNull(notificador);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.duracaoNanos = duracao.toNanos();
        this.duracaoTurno = Objects.requireNonNull(duracaoTurno);
        this.aoEncerrar = Objects.requireNonNull(aoEncerrar);
        if (duracaoNanos <= 0 || duracaoTurno.toNanos() <= 0 || jogadorA == jogadorB) {
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
            notificarDraft();
            if (escolhasA.size() == 3 && escolhasB.size() == 3) terminarDraft();
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
                    notificarDraft();
                    if (escolhasA.size() == 3 && escolhasB.size() == 3) terminarDraft();
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
        long restante = iniciada && fase == FasePartida.DRAFT && escolhasA.size() + escolhasB.size() < 6
                ? Math.max(0, prazoNanos - System.nanoTime()) : 0;
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
        motor = new MotorBatalha(jogadorA.getToken(), jogadorA.getNome(), criaturasEscolhidas(jogadorA),
                jogadorB.getToken(), jogadorB.getNome(), criaturasEscolhidas(jogadorB));
        timerTurno = new TimerTurno(id);
        iniciarRodada(1);
        EstadoBatalha estadoA = motor.estado(jogadorA.getToken(), segundosTurno());
        EstadoBatalha estadoB = motor.estado(jogadorB.getToken(), segundosTurno());
        notificador.notificar(jogadorA, cb -> cb.batalhaIniciada(estadoA));
        notificador.notificar(jogadorB, cb -> cb.batalhaIniciada(estadoB));
    }

    private List<Criatura> criaturasEscolhidas(SessaoJogador jogador) {
        return escolhasDe(jogador).stream().map(idCriatura -> pool.stream()
                .filter(c -> c.id() == idCriatura).findFirst().orElseThrow()).toList();
    }

    private void iniciarRodada(int numero) {
        prazoTurnoNanos = System.nanoTime() + duracaoTurno.toNanos();
        rodada = new RodadaSimultanea(numero, jogadorA.getToken(), jogadorB.getToken(),
                this::resolverRodada, PoliticaAcaoPadrao.ATAQUE_BASICO, resultados -> { }, timerTurno, duracaoTurno);
    }

    private int segundosTurno() {
        long restante = Math.max(0, prazoTurnoNanos - System.nanoTime());
        return (int) Math.min(Integer.MAX_VALUE, TimeUnit.NANOSECONDS.toSeconds(restante)
                + (restante % TimeUnit.SECONDS.toNanos(1) == 0 ? 0 : 1));
    }

    private Map<String, ResultadoRodada> resolverRodada(int numero, Map<String, Acao> acoes) {
        Map<String, ResultadoRodada> resultados;
        boolean terminou;
        synchronized (lock) {
            // Um timeout que já estava em execução perde efeito após o W.O. ou avanço da rodada.
            if (fase != FasePartida.BATALHA || rodada.getNumero() != numero) return Map.of();
            Map<String, ResultadoRodada> resolvidos = motor.resolver(numero, acoes);
            terminou = motor.encerrada();
            if (terminou) {
                transitar(FasePartida.BATALHA, FasePartida.ENCERRADA);
                timerTurno.close();
            } else {
                iniciarRodada(numero + 1);
            }
            int segundos = terminou ? 0 : segundosTurno();
            ResultadoRodada resultadoA = new ResultadoRodada(numero, resolvidos.get(jogadorA.getToken()).eventos(),
                    motor.estado(jogadorA.getToken(), segundos));
            ResultadoRodada resultadoB = new ResultadoRodada(numero, resolvidos.get(jogadorB.getToken()).eventos(),
                    motor.estado(jogadorB.getToken(), segundos));
            resultados = Map.of(jogadorA.getToken(), resultadoA, jogadorB.getToken(), resultadoB);
            notificador.notificar(jogadorA, cb -> cb.rodadaResolvida(resultadoA));
            notificador.notificar(jogadorB, cb -> cb.rodadaResolvida(resultadoB));
            if (terminou) {
                ResultadoPartida finalA = motor.resultado(jogadorA.getToken(), motor.vencedor(), false);
                ResultadoPartida finalB = motor.resultado(jogadorB.getToken(), motor.vencedor(), false);
                notificador.notificar(jogadorA, cb -> cb.partidaEncerrada(finalA));
                notificador.notificar(jogadorB, cb -> cb.partidaEncerrada(finalB));
            }
        }
        // O lobby tem seu próprio lock: nunca entrar nele segurando o monitor da sala.
        if (terminou) aoEncerrar.accept(this);
        return resultados;
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
        RodadaSimultanea atual;
        synchronized (lock) {
            if (!contem(jogador)) throw new ForaDaRodadaException("Jogador não pertence à partida");
            if (fase != FasePartida.BATALHA) throw new FaseInvalidaException("A batalha não está ativa");
            atual = rodada;
            if (acao != null && acao.numeroRodada() != atual.getNumero()) {
                throw new ForaDaRodadaException("A ação não pertence à rodada atual");
            }
            motor.validarAcao(jogador.getToken(), acao);
        }
        // Registrar pode resolver a rodada; não manter o monitor da partida durante essa chamada.
        atual.registrarAcao(jogador.getToken(), acao);
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
            if (rodada != null) rodada.cancelar();
            if (timerTurno != null) timerTurno.close();
            ResultadoPartida resultado = motor == null
                    ? new ResultadoPartida(vencedor.getNome(), true, 0, List.of(), List.of())
                    : motor.resultado(vencedor.getToken(), vencedor.getNome(), true);
            notificador.notificar(vencedor, cb -> cb.partidaEncerrada(resultado));
            return true;
        }
    }
}
