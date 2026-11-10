package br.ucsal.sd.arena.servidor;

import br.ucsal.sd.arena.compartilhado.dto.Criatura;
import br.ucsal.sd.arena.compartilhado.dto.Ataque;
import br.ucsal.sd.arena.compartilhado.dto.Tipo;
import java.util.List;

/** Catálogo oficial do card 05. DTOs imutáveis podem ser compartilhados entre salas. */
public final class CatalogoCriaturas {
    private CatalogoCriaturas() { }

    private static final List<Criatura> CRIATURAS = List.of(
            new Criatura(1, "Brasalto", Tipo.FOGO, 80, 55, List.of(new Ataque("Chama curta", Tipo.FOGO, 40), new Ataque("Brasa viva", Tipo.FOGO, 60), new Ataque("Fumaça", Tipo.FOGO, 30), new Ataque("Explosão", Tipo.FOGO, 90))),
            new Criatura(2, "Cinderix", Tipo.FOGO, 65, 80, List.of(new Ataque("Centelha veloz", Tipo.FOGO, 40), new Ataque("Rastro de cinzas", Tipo.FOGO, 60), new Ataque("Fagulha", Tipo.FOGO, 30), new Ataque("Erupção solar", Tipo.FOGO, 90))),
            new Criatura(3, "Pirolho", Tipo.FOGO, 90, 40, List.of(new Ataque("Olhar ardente", Tipo.FOGO, 40), new Ataque("Rocha incandescente", Tipo.FOGO, 60), new Ataque("Fuligem", Tipo.FOGO, 30), new Ataque("Caldeira vulcânica", Tipo.FOGO, 90))),
            new Criatura(4, "Folhargo", Tipo.PLANTA, 85, 50, List.of(new Ataque("Folha cortante", Tipo.PLANTA, 40), new Ataque("Cipó rasteiro", Tipo.PLANTA, 60), new Ataque("Esporos", Tipo.PLANTA, 30), new Ataque("Tempestade floral", Tipo.PLANTA, 90))),
            new Criatura(5, "Espinhela", Tipo.PLANTA, 70, 75, List.of(new Ataque("Dardo de espinho", Tipo.PLANTA, 40), new Ataque("Sarça afiada", Tipo.PLANTA, 60), new Ataque("Urtiga", Tipo.PLANTA, 30), new Ataque("Lança silvestre", Tipo.PLANTA, 90))),
            new Criatura(6, "Musgolento", Tipo.PLANTA, 95, 35, List.of(new Ataque("Raiz pesada", Tipo.PLANTA, 40), new Ataque("Esmagamento arbóreo", Tipo.PLANTA, 60), new Ataque("Limo denso", Tipo.PLANTA, 30), new Ataque("Fúria da floresta", Tipo.PLANTA, 90))),
            new Criatura(7, "Marélio", Tipo.AGUA, 80, 60, List.of(new Ataque("Jato", Tipo.AGUA, 40), new Ataque("Corrente marinha", Tipo.AGUA, 60), new Ataque("Borrifo", Tipo.AGUA, 30), new Ataque("Maremoto", Tipo.AGUA, 90))),
            new Criatura(8, "Gotaluz", Tipo.AGUA, 60, 90, List.of(new Ataque("Raio prismático", Tipo.AGUA, 40), new Ataque("Torrente luminosa", Tipo.AGUA, 60), new Ataque("Orvalho", Tipo.AGUA, 30), new Ataque("Dilúvio boreal", Tipo.AGUA, 90))),
            new Criatura(9, "Tidalon", Tipo.AGUA, 100, 30, List.of(new Ataque("Onda de impacto", Tipo.AGUA, 40), new Ataque("Pressão abissal", Tipo.AGUA, 60), new Ataque("Espuma salgada", Tipo.AGUA, 30), new Ataque("Tsunami ancestral", Tipo.AGUA, 90))),
            new Criatura(10, "Cascadus", Tipo.AGUA, 75, 65, List.of(new Ataque("Golpe de concha", Tipo.AGUA, 40), new Ataque("Turbilhão de casca", Tipo.AGUA, 60), new Ataque("Maré baixa", Tipo.AGUA, 30), new Ataque("Queda d'água", Tipo.AGUA, 90))));

    public static List<Criatura> listar() {
        return CRIATURAS;
    }
}
