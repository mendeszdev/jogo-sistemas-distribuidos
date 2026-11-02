package br.ucsal.sd.arena.compartilhado.dto;

/** Elemento de uma criatura ou ataque. FOGO > PLANTA > AGUA > FOGO. */
public enum Tipo {
    FOGO, AGUA, PLANTA;

    /** Multiplicador de dano de um ataque deste tipo contra um defensor do tipo informado. */
    public double multiplicadorContra(Tipo defensor) {
        if (this == defensor) {
            return 1.0;
        }
        return switch (this) {
            case FOGO -> defensor == PLANTA ? 2.0 : 0.5;
            case AGUA -> defensor == FOGO ? 2.0 : 0.5;
            case PLANTA -> defensor == AGUA ? 2.0 : 0.5;
        };
    }
}
