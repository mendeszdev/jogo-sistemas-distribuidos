# Timer de turno

`TimerTurno` mantém um executor com uma thread exclusiva por sala. Cada rodada
agenda um prazo independente, por padrão de 30 segundos. A sala deve reutilizar
o timer e fechá-lo ao encerrar, inclusive por desconexão.

```java
TimerTurno timer = new TimerTurno(idSala);
RodadaSimultanea rodada = new RodadaSimultanea(numero, tokenA, tokenB,
        resolvedor, PoliticaAcaoPadrao.ATAQUE_BASICO, aoResolver,
        timer, TimerTurno.DURACAO_PADRAO);
// Ao encerrar a sala:
rodada.cancelar();
timer.close();
```

O timeout preenche somente as ações ausentes com ataque de índice zero. O motor
pode fornecer outra `PoliticaAcaoPadrao`, conforme as ações válidas para o estado
da batalha. A disputa entre a segunda ação e o timeout é decidida pelo monitor
da própria `RodadaSimultanea`: quem fecha primeiro captura as ações, e somente
essa execução chama o resolvedor. Uma ação que encontra a rodada fechada recebe
`ForaDaRodadaException`. A resolução e os callbacks ocorrem fora do monitor.

O prazo é cancelado quando ambas as ações chegam. Cada tarefa referencia sua
própria rodada, portanto um timeout antigo nunca fecha a rodada seguinte.
`cancelar()` invalida uma rodada pendente sem resolver; uma resolução que já
começou não é revertida. `close()` cancela os prazos pendentes e permite concluir
uma resolução em execução, sem interrompê-la.

## Integração

A barreira do card 19 aceita o timer no novo construtor. O construtor anterior
continua disponível para chamadas sem temporização. A task 18 conecta esse
construtor ao ciclo de vida de `Partida`, implementa `MotorBatalha` como resolvedor
e cancela a rodada e o timer no encerramento. A conferência monotônica do prazo
na própria barreira também impede aceitar ações vencidas quando a thread do
timer estiver ocupada. O timer não implementa regras de dano, trocas ou seleção
de vencedor.

## Validação

`mvn -pl servidor -am test` cobre expiração real, preenchimento das ações
ausentes, cancelamento, reutilização entre rodadas, isolamento entre salas e
a disputa de segunda ação contra timeout em mil execuções concorrentes.
