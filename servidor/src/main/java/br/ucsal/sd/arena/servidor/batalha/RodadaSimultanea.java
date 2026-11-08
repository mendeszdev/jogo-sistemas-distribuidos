package br.ucsal.sd.arena.servidor.batalha;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoRodada;
import br.ucsal.sd.arena.compartilhado.excecao.ForaDaRodadaException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Barreira de uma rodada simultânea: espera a ação dos dois jogadores (ou o timeout) e resolve uma única vez.
 *
 * <p>Concorrência: um único monitor ({@code synchronized}) protege {@code acoes} e {@code fechada}. Dentro dele
 * só há operações curtas e locais: validar, registrar e decidir QUEM fecha. A thread que faz a transição
 * {@code fechada = false -> true} (sob o lock) é a única dona do fechamento; ela sai do lock e só então chama o
 * resolvedor e o {@code aoResolver}, que disparam callbacks remotos (lentos) e nunca devem segurar o lock.
 * Como a decisão é atômica e o mapa de ações é copiado antes de sair do lock, não há estado intermediário.
 */
public final class RodadaSimultanea {

    private final int numero;
    private final String tokenA;
    private final String tokenB;
    private final ResolvedorRodada resolvedor;
    private final PoliticaAcaoPadrao politica;
    private final Consumer<Map<String, ResultadoRodada>> aoResolver;

    private final Object lock = new Object();
    private final Map<String, Acao> acoes = new LinkedHashMap<>(); // guardado por lock
    private boolean fechada;                                       // guardado por lock

    public RodadaSimultanea(int numero, String tokenA, String tokenB, ResolvedorRodada resolvedor,
                            PoliticaAcaoPadrao politica, Consumer<Map<String, ResultadoRodada>> aoResolver) {
        this.numero = numero;
        this.tokenA = Objects.requireNonNull(tokenA);
        this.tokenB = Objects.requireNonNull(tokenB);
        if (tokenA.equals(tokenB)) {
            throw new IllegalArgumentException("os dois tokens devem ser diferentes");
        }
        this.resolvedor = Objects.requireNonNull(resolvedor);
        this.politica = Objects.requireNonNull(politica);
        this.aoResolver = Objects.requireNonNull(aoResolver);
    }

    public int getNumero() {
        return numero;
    }

    public boolean estaFechada() {
        synchronized (lock) {
            return fechada;
        }
    }

    /** Registra a ação; se for a segunda, esta thread fecha a rodada e a resolve. */
    public void registrarAcao(String token, Acao acao) throws ForaDaRodadaException {
        Objects.requireNonNull(acao, "acao");
        Map<String, Acao> paraResolver = null;
        synchronized (lock) {
            if (fechada) {
                throw new ForaDaRodadaException("Rodada " + numero + " já encerrada");
            }
            if (!tokenA.equals(token) && !tokenB.equals(token)) {
                throw new ForaDaRodadaException("Jogador não pertence à rodada " + numero);
            }
            if (acoes.containsKey(token)) {
                throw new ForaDaRodadaException("Ação já enviada nesta rodada");
            }
            acoes.put(token, acao);
            if (acoes.size() == 2) {
                fechada = true;
                paraResolver = new LinkedHashMap<>(acoes);
            }
        }
        if (paraResolver != null) {
            resolverFora(paraResolver);
        }
    }

    /** Chamado pela thread do timer: completa as ações faltantes com a política padrão e fecha. */
    public void fecharPorTimeout() {
        Map<String, Acao> paraResolver;
        synchronized (lock) {
            if (fechada) {
                return; // já fechada pelas ações: o timeout chegou tarde e não faz nada
            }
            acoes.computeIfAbsent(tokenA, politica::acaoPadrao);
            acoes.computeIfAbsent(tokenB, politica::acaoPadrao);
            fechada = true;
            paraResolver = new LinkedHashMap<>(acoes);
        }
        resolverFora(paraResolver);
    }

    /** Executado SEM lock: o resolvedor e o aoResolver podem demorar e disparar callbacks remotos. */
    private void resolverFora(Map<String, Acao> acoesFinais) {
        Map<String, ResultadoRodada> resultados = resolvedor.resolver(numero, Map.copyOf(acoesFinais));
        aoResolver.accept(resultados);
    }
}
