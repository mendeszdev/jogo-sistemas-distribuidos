package br.ucsal.sd.arena.servidor.batalha;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
import br.ucsal.sd.arena.compartilhado.dto.Ataque;
import br.ucsal.sd.arena.compartilhado.dto.Criatura;
import br.ucsal.sd.arena.compartilhado.dto.EstadoBatalha;
import br.ucsal.sd.arena.compartilhado.dto.EstadoCriaturaEmBatalha;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoPartida;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoRodada;
import br.ucsal.sd.arena.compartilhado.dto.TipoAcao;
import br.ucsal.sd.arena.compartilhado.excecao.AcaoInvalidaException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Regras de combate. A partida serializa o acesso ao estado deste motor com seu monitor. */
public final class MotorBatalha implements ResolvedorRodada {
    private final Time a;
    private final Time b;
    private int rodadasJogadas;

    public MotorBatalha(String tokenA, String nomeA, List<Criatura> timeA,
                        String tokenB, String nomeB, List<Criatura> timeB) {
        a = new Time(tokenA, nomeA, timeA);
        b = new Time(tokenB, nomeB, timeB);
        if (a.token.equals(b.token)) throw new IllegalArgumentException("Os jogadores devem ser distintos");
        HashSet<Integer> ids = new HashSet<>();
        for (Time time : List.of(a, b)) {
            for (Combatente criatura : time.criaturas) {
                if (!ids.add(criatura.base.id())) throw new IllegalArgumentException("Criatura repetida entre os times");
            }
        }
    }

    public void validarAcao(String token, Acao acao) throws AcaoInvalidaException {
        Time time = time(token);
        if (encerrada()) throw new AcaoInvalidaException("A batalha já terminou");
        if (acao == null || acao.tipo() == null || acao.indice() < 0) {
            throw new AcaoInvalidaException("Tipo e índice de ação válidos são obrigatórios");
        }
        if (acao.tipo() == TipoAcao.ATACAR) {
            if (time.ativa().hp == 0 || acao.indice() >= time.ativa().base.ataques().size()) {
                throw new AcaoInvalidaException("Ataque indisponível para a criatura ativa");
            }
        } else {
            List<Combatente> reservas = time.reservas();
            if (acao.indice() >= reservas.size() || reservas.get(acao.indice()).hp == 0) {
                throw new AcaoInvalidaException("Reserva inexistente ou derrotada");
            }
        }
    }

    @Override
    public Map<String, ResultadoRodada> resolver(int numero, Map<String, Acao> acoes) {
        if (numero != rodadasJogadas + 1 || acoes.size() != 2
                || !acoes.containsKey(a.token) || !acoes.containsKey(b.token)) {
            throw new IllegalArgumentException("Número de rodada ou jogadores inválidos");
        }
        // Valida as duas decisões antes de alterar HP ou composição do campo.
        try {
            validarAcao(a.token, acoes.get(a.token));
            validarAcao(b.token, acoes.get(b.token));
        } catch (AcaoInvalidaException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        List<String> eventos = new ArrayList<>();
        trocar(a, acoes.get(a.token), eventos);
        trocar(b, acoes.get(b.token), eventos);
        // No catálogo oficial não há empate; em catálogos de teste, A desempata.
        Time primeiro = a.ativa().base.velocidade() >= b.ativa().base.velocidade() ? a : b;
        Time segundo = adversario(primeiro);
        atacar(primeiro, segundo, acoes.get(primeiro.token), eventos);
        atacar(segundo, primeiro, acoes.get(segundo.token), eventos);
        promover(a, eventos);
        promover(b, eventos);
        rodadasJogadas = numero;
        if (encerrada()) eventos.add("Vitória de " + vencedor() + ".");
        return Map.of(a.token, new ResultadoRodada(numero, eventos, estado(a.token, 0)),
                b.token, new ResultadoRodada(numero, eventos, estado(b.token, 0)));
    }

    private void trocar(Time time, Acao acao, List<String> eventos) {
        if (acao.tipo() != TipoAcao.TROCAR) return;
        Combatente nova = time.reservas().get(acao.indice());
        String anterior = time.ativa().base.nome();
        time.indiceAtiva = time.criaturas.indexOf(nova);
        eventos.add(time.nome + " trocou " + anterior + " por " + nova.base.nome() + ".");
    }

    private void atacar(Time atacante, Time defensor, Acao acao, List<String> eventos) {
        if (acao.tipo() != TipoAcao.ATACAR) return;
        if (atacante.ativa().hp == 0) {
            eventos.add(atacante.ativa().base.nome() + " perdeu a ação por nocaute.");
            return;
        }
        Ataque ataque = atacante.ativa().base.ataques().get(acao.indice());
        Combatente alvo = defensor.ativa();
        double multiplicador = ataque.tipo().multiplicadorContra(alvo.base.tipo());
        int dano = (int) Math.round(ataque.poder() * 0.40 * multiplicador);
        int perdido = Math.min(alvo.hp, dano);
        alvo.hp -= perdido;
        String efetividade = multiplicador > 1 ? " Super efetivo!" : multiplicador < 1 ? " Pouco efetivo." : "";
        eventos.add(atacante.nome + ": " + atacante.ativa().base.nome() + " usou " + ataque.nome()
                + " em " + alvo.base.nome() + ": -" + perdido + " HP (" + alvo.hp + "/"
                + alvo.base.hpMax() + ")." + efetividade);
        if (alvo.hp == 0) eventos.add(alvo.base.nome() + " foi derrubada.");
    }

    private void promover(Time time, List<String> eventos) {
        if (time.ativa().hp > 0) return;
        for (Combatente reserva : time.reservas()) {
            if (reserva.hp > 0) {
                time.indiceAtiva = time.criaturas.indexOf(reserva);
                eventos.add(time.nome + ": " + reserva.base.nome() + " entrou após o nocaute.");
                return;
            }
        }
    }

    public EstadoBatalha estado(String token, int segundosRestantes) {
        Time meu = time(token);
        Time outro = adversario(meu);
        return new EstadoBatalha(encerrada() ? rodadasJogadas : rodadasJogadas + 1, meu.ativa().estado(),
                meu.reservas().stream().map(Combatente::estado).toList(), outro.ativa().estado(),
                (int) outro.reservas().stream().filter(c -> c.hp > 0).count(), segundosRestantes);
    }

    public boolean encerrada() { return a.derrotado() || b.derrotado(); }

    public String vencedor() {
        if (!encerrada()) throw new IllegalStateException("A batalha ainda está ativa");
        return a.derrotado() ? b.nome : a.nome;
    }

    public ResultadoPartida resultado(String token, String vencedor, boolean porWO) {
        Time meu = time(token);
        return new ResultadoPartida(vencedor, porWO, rodadasJogadas,
                meu.criaturas.stream().map(Combatente::estado).toList(),
                adversario(meu).criaturas.stream().map(Combatente::estado).toList());
    }

    private Time time(String token) {
        if (a.token.equals(token)) return a;
        if (b.token.equals(token)) return b;
        throw new IllegalArgumentException("Jogador não pertence à batalha");
    }

    private Time adversario(Time time) { return time == a ? b : a; }

    private static final class Combatente {
        final Criatura base;
        int hp;

        Combatente(Criatura base) {
            this.base = Objects.requireNonNull(base);
            if (base.hpMax() <= 0 || base.ataques().isEmpty()) {
                throw new IllegalArgumentException("Criatura deve ter HP positivo e ataques");
            }
            hp = base.hpMax();
        }

        EstadoCriaturaEmBatalha estado() { return new EstadoCriaturaEmBatalha(base.id(), hp); }
    }

    private static final class Time {
        final String token;
        final String nome;
        final List<Combatente> criaturas;
        int indiceAtiva;

        Time(String token, String nome, List<Criatura> criaturas) {
            this.token = Objects.requireNonNull(token);
            this.nome = Objects.requireNonNull(nome);
            if (criaturas.size() != 3) throw new IllegalArgumentException("O time deve conter três criaturas");
            this.criaturas = criaturas.stream().map(Combatente::new).toList();
        }

        Combatente ativa() { return criaturas.get(indiceAtiva); }
        List<Combatente> reservas() { return criaturas.stream().filter(c -> c != ativa()).toList(); }
        boolean derrotado() { return criaturas.stream().allMatch(c -> c.hp == 0); }
    }
}
