package br.ucsal.sd.arena.servidor.batalha;

import br.ucsal.sd.arena.compartilhado.dto.Acao;

/** Ponto de encaixe do timer (card 20): ação de quem não jogou até o fim do tempo. */
public interface PoliticaAcaoPadrao {

    Acao acaoPadrao(String token);
}
