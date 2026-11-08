package br.ucsal.sd.arena.cliente.rede;

import br.ucsal.sd.arena.compartilhado.dto.EstadoBatalha;
import br.ucsal.sd.arena.compartilhado.dto.EstadoDraft;
import br.ucsal.sd.arena.compartilhado.dto.PartidaInfo;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoPartida;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoRodada;

import java.util.List;

/**
 * Eventos empurrados pelo servidor, já desacoplados do RMI. Os métodos são chamados em threads
 * do RMI, não na thread da interface gráfica (a GUI deve usar Platform.runLater).
 */
public interface OuvinteEventosJogo {

    void partidaEncontrada(PartidaInfo info);

    void draftAtualizado(EstadoDraft estado);

    void batalhaIniciada(EstadoBatalha estado);

    void rodadaResolvida(ResultadoRodada resultado);

    void partidaEncerrada(ResultadoPartida resultado);

    void adversarioDesconectado(int segundosTolerancia);

    void jogadoresOnlineAtualizados(List<String> nomes);
}
