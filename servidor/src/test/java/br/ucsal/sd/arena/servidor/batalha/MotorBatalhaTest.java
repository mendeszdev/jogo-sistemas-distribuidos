package br.ucsal.sd.arena.servidor.batalha;

import br.ucsal.sd.arena.compartilhado.dto.*;
import br.ucsal.sd.arena.compartilhado.excecao.AcaoInvalidaException;
import br.ucsal.sd.arena.servidor.CatalogoCriaturas;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotorBatalhaTest {
    private static final Acao ATAQUE = new Acao(TipoAcao.ATACAR, 0);

    private List<Criatura> oficiais(int... ids) {
        return java.util.Arrays.stream(ids).mapToObj(id -> CatalogoCriaturas.listar().stream()
                .filter(c -> c.id() == id).findFirst().orElseThrow()).toList();
    }

    private MotorBatalha oficial(int[] a, int[] b) {
        return new MotorBatalha("A", "Jogador A", oficiais(a), "B", "Jogador B", oficiais(b));
    }

    private Criatura criatura(int id, Tipo tipo, int hp, int velocidade, int poder) {
        return new Criatura(id, "Criatura " + id, tipo, hp, velocidade,
                List.of(new Ataque("Golpe", tipo, poder)));
    }

    private MotorBatalha simples(Tipo atacante, Tipo defensor, int poder) {
        return new MotorBatalha("A", "A", List.of(criatura(1, atacante, 200, 90, poder),
                criatura(2, atacante, 200, 50, poder), criatura(3, atacante, 200, 40, poder)),
                "B", "B", List.of(criatura(4, defensor, 200, 30, 0),
                criatura(5, defensor, 200, 20, 0), criatura(6, defensor, 200, 10, 0)));
    }

    @Test
    void danoSegueTodasAsRelacoesElementaisEEscalaOficial() {
        for (Tipo ataque : Tipo.values()) for (Tipo defesa : Tipo.values()) {
            MotorBatalha motor = simples(ataque, defesa, 40);
            motor.resolver(1, Map.of("A", ATAQUE, "B", ATAQUE));
            int esperado = (int) Math.round(40 * 0.4 * ataque.multiplicadorContra(defesa));
            assertEquals(200 - esperado, motor.estado("B", 30).minhaAtiva().hpAtual());
        }
        MotorBatalha arredondado = simples(Tipo.FOGO, Tipo.FOGO, 33);
        arredondado.resolver(1, Map.of("A", ATAQUE, "B", ATAQUE));
        assertEquals(187, arredondado.estado("B", 30).minhaAtiva().hpAtual());
    }

    @Test
    void exemplosOficiaisResolvemPelaVelocidadeComLogsDeEfetividade() {
        MotorBatalha motor = oficial(new int[]{1, 2, 3}, new int[]{7, 8, 9});
        var r = motor.resolver(1, Map.of("A", new Acao(TipoAcao.ATACAR, 1), "B", ATAQUE));
        assertEquals(48, motor.estado("A", 30).minhaAtiva().hpAtual());
        assertEquals(68, motor.estado("B", 30).minhaAtiva().hpAtual());
        assertTrue(r.get("A").eventos().get(0).contains("Jato"));
        assertTrue(r.get("A").eventos().get(0).contains("Super efetivo!"));
        assertTrue(r.get("A").eventos().get(1).contains("Pouco efetivo."));
        assertEquals(2, r.get("A").estadoAtual().numeroRodada());
    }

    @Test
    void trocaTemPrioridadeEAtaqueAtingeNovaCriatura() {
        MotorBatalha motor = oficial(new int[]{1, 7, 3}, new int[]{4, 5, 6});
        motor.resolver(1, Map.of("A", new Acao(TipoAcao.TROCAR, 0), "B", ATAQUE));
        EstadoBatalha a = motor.estado("A", 30);
        assertEquals(new EstadoCriaturaEmBatalha(7, 48), a.minhaAtiva());
        assertEquals(new EstadoCriaturaEmBatalha(1, 80), a.minhasReservas().getFirst());
        assertEquals(85, a.ativaAdversario().hpAtual());
    }

    @Test
    void duasTrocasNaoAtacamEPreservamIndicesDasReservas() {
        MotorBatalha motor = oficial(new int[]{1, 2, 3}, new int[]{4, 5, 6});
        motor.resolver(1, Map.of("A", new Acao(TipoAcao.TROCAR, 1), "B", new Acao(TipoAcao.TROCAR, 0)));
        assertEquals(new EstadoCriaturaEmBatalha(3, 90), motor.estado("A", 30).minhaAtiva());
        assertEquals(new EstadoCriaturaEmBatalha(5, 70), motor.estado("B", 30).minhaAtiva());
        assertEquals(List.of(1, 2), motor.estado("A", 30).minhasReservas().stream()
                .map(EstadoCriaturaEmBatalha::idCriatura).toList());
    }

    @Test
    void nocauteImpedeSegundoAtaqueEPromovePrimeiraReservaViva() throws Exception {
        MotorBatalha motor = new MotorBatalha("A", "A", List.of(criatura(1, Tipo.FOGO, 20, 10, 90),
                criatura(2, Tipo.FOGO, 100, 15, 90), criatura(3, Tipo.FOGO, 100, 16, 90)),
                "B", "B", List.of(criatura(4, Tipo.FOGO, 200, 90, 90),
                criatura(5, Tipo.FOGO, 100, 80, 90), criatura(6, Tipo.FOGO, 100, 70, 90)));
        var r = motor.resolver(1, Map.of("A", ATAQUE, "B", ATAQUE));
        assertEquals(new EstadoCriaturaEmBatalha(2, 100), motor.estado("A", 30).minhaAtiva());
        assertEquals(0, motor.estado("A", 30).minhasReservas().getFirst().hpAtual());
        assertEquals(200, motor.estado("B", 30).minhaAtiva().hpAtual());
        assertEquals(1, motor.estado("B", 30).reservasRestantesAdversario());
        assertTrue(r.get("A").eventos().stream().anyMatch(e -> e.contains("perdeu a ação")));
        assertThrows(AcaoInvalidaException.class,
                () -> motor.validarAcao("A", new Acao(TipoAcao.TROCAR, 0)));
        motor.validarAcao("A", new Acao(TipoAcao.TROCAR, 1));
    }

    @Test
    void eliminarTresCriaturasEncerraEPreservaVisoesDosResultados() {
        MotorBatalha motor = new MotorBatalha("A", "Alice", List.of(criatura(1, Tipo.FOGO, 200, 90, 90),
                criatura(2, Tipo.FOGO, 200, 80, 90), criatura(3, Tipo.FOGO, 200, 70, 90)),
                "B", "Bruno", List.of(criatura(4, Tipo.FOGO, 20, 30, 90),
                criatura(5, Tipo.FOGO, 20, 20, 90), criatura(6, Tipo.FOGO, 20, 10, 90)));
        for (int n = 1; n <= 3; n++) {
            motor.resolver(n, Map.of("A", ATAQUE, "B", ATAQUE));
            assertEquals(n == 3, motor.encerrada());
        }
        assertEquals("Alice", motor.vencedor());
        ResultadoPartida a = motor.resultado("A", motor.vencedor(), false);
        ResultadoPartida b = motor.resultado("B", motor.vencedor(), false);
        assertEquals(3, a.rodadasJogadas());
        assertTrue(a.timeAdversario().stream().allMatch(EstadoCriaturaEmBatalha::derrubada));
        assertEquals(a.meuTime(), b.timeAdversario());
        assertEquals(a.timeAdversario(), b.meuTime());
        assertThrows(IllegalArgumentException.class, () -> motor.resolver(4, Map.of("A", ATAQUE, "B", ATAQUE)));
    }

    @Test
    void acoesInvalidasNaoAlteramEstadoNemConsomemRodada() {
        MotorBatalha motor = oficial(new int[]{1, 2, 3}, new int[]{4, 5, 6});
        var inicial = motor.estado("A", 30);
        for (Acao invalida : List.of(new Acao(null, 0), new Acao(TipoAcao.ATACAR, -1),
                new Acao(TipoAcao.ATACAR, 4), new Acao(TipoAcao.TROCAR, 2))) {
            assertThrows(AcaoInvalidaException.class, () -> motor.validarAcao("A", invalida));
        }
        assertThrows(AcaoInvalidaException.class, () -> motor.validarAcao("A", null));
        assertThrows(IllegalArgumentException.class, () -> motor.resolver(1,
                Map.of("A", new Acao(TipoAcao.TROCAR, 0), "B", new Acao(TipoAcao.ATACAR, 4))));
        assertEquals(inicial, motor.estado("A", 30));
        motor.resolver(1, Map.of("A", ATAQUE, "B", ATAQUE));
        assertThrows(IllegalArgumentException.class, () -> motor.resolver(1, Map.of("A", ATAQUE, "B", ATAQUE)));
    }

    @Test
    void trocarEVoltarMantemHpEEstadosAnterioresSaoImutaveis() {
        MotorBatalha motor = oficial(new int[]{1, 2, 3}, new int[]{4, 5, 6});
        var inicial = motor.estado("A", 30);
        motor.resolver(1, Map.of("A", ATAQUE, "B", ATAQUE));
        int hp = motor.estado("A", 30).minhaAtiva().hpAtual();
        motor.resolver(2, Map.of("A", new Acao(TipoAcao.TROCAR, 0), "B", new Acao(TipoAcao.TROCAR, 0)));
        motor.resolver(3, Map.of("A", new Acao(TipoAcao.TROCAR, 0), "B", new Acao(TipoAcao.TROCAR, 0)));
        assertEquals(hp, motor.estado("A", 30).minhaAtiva().hpAtual());
        assertEquals(80, inicial.minhaAtiva().hpAtual());
        assertThrows(UnsupportedOperationException.class, () -> inicial.minhasReservas().clear());
    }
}
