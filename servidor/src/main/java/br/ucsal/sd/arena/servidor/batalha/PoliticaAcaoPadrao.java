package br.ucsal.sd.arena.servidor.batalha;

import br.ucsal.sd.arena.compartilhado.dto.Acao;
import br.ucsal.sd.arena.compartilhado.dto.TipoAcao;

/** Ponto de encaixe do timer (card 20): ação de quem não jogou até o fim do tempo. */
public interface PoliticaAcaoPadrao {

    /** Ataque básico para jogadores que não enviaram uma ação no prazo. */
    PoliticaAcaoPadrao ATAQUE_BASICO = token -> new Acao(TipoAcao.ATACAR, 0);

    Acao acaoPadrao(String token);
}
