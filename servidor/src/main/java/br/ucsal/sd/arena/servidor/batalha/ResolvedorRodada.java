package br.ucsal.sd.arena.servidor.batalha;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoRodada;

import java.util.Map;

/** Ponto de encaixe do motor de batalha (card 18): aplica as duas ações de uma rodada. */
public interface ResolvedorRodada {

    /**
     * @param numeroRodada  número da rodada sendo resolvida
     * @param acoesPorToken exatamente uma ação por jogador da rodada
     * @return um {@link ResultadoRodada} por token, já com a visão daquele jogador
     */
    Map<String, ResultadoRodada> resolver(int numeroRodada, Map<String, Acao> acoesPorToken);
}
