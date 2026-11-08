package br.ucsal.sd.arena.cliente.rede;

import br.ucsal.sd.arena.compartilhado.dto.EstadoBatalha;
import br.ucsal.sd.arena.compartilhado.dto.EstadoDraft;
import br.ucsal.sd.arena.compartilhado.dto.PartidaInfo;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoPartida;
import br.ucsal.sd.arena.compartilhado.dto.ResultadoRodada;

import java.util.List;

/** Ouvinte de depuração: imprime cada evento recebido no console. */
public class OuvinteConsole implements OuvinteEventosJogo {

    private final String prefixo;

    public OuvinteConsole(String nome) {
        this.prefixo = "[" + nome + "] ";
    }

    private void imprimir(String evento, Object dados) {
        System.out.println(prefixo + "EVENTO " + evento + ": " + dados);
    }

    @Override
    public void partidaEncontrada(PartidaInfo info) {
        imprimir("partidaEncontrada", info);
    }

    @Override
    public void draftAtualizado(EstadoDraft estado) {
        imprimir("draftAtualizado", estado);
    }

    @Override
    public void batalhaIniciada(EstadoBatalha estado) {
        imprimir("batalhaIniciada", estado);
    }

    @Override
    public void rodadaResolvida(ResultadoRodada resultado) {
        imprimir("rodadaResolvida", resultado);
    }

    @Override
    public void partidaEncerrada(ResultadoPartida resultado) {
        imprimir("partidaEncerrada", resultado);
    }

    @Override
    public void adversarioDesconectado(int segundosTolerancia) {
        imprimir("adversarioDesconectado", segundosTolerancia + "s de tolerância");
    }

    @Override
    public void jogadoresOnlineAtualizados(List<String> nomes) {
        imprimir("jogadoresOnlineAtualizados", nomes);
    }
}
