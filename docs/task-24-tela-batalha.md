# Task 24 — Tela de batalha

A transição `batalhaIniciada` abre `TelaBatalha`, usando o catálogo recebido no draft para os nomes, imagens, HP máximo e ataques. A tela apresenta as duas criaturas ativas, barras e valores de HP, reservas próprias, quantidade de reservas adversárias, rodada, relógio e log.

Ataques enviam `Acao(ATACAR, indiceDoAtaque)` e trocas enviam `Acao(TROCAR, indiceNaListaDeReservas)`. Reservas derrotadas ficam desabilitadas. A chamada RMI roda fora da thread JavaFX; durante o envio os botões ficam bloqueados e, após confirmação, a tela mostra “Aguardando adversário...”. HP e criaturas nunca são alterados por cliques locais.

`rodadaResolvida` passa pelo lobby na thread JavaFX, registra os eventos e aplica `estadoAtual`. Uma rodada nova libera as ações; respostas de envios antigos são ignoradas. O relógio usa prazo monotônico e, ao zerar, bloqueia ações e aguarda o servidor. Sair da tela para os timers e descarta retornos tardios.

## Dependência do servidor

Na main usada nesta implementação, `Partida.enviarAcao` ainda lança `UnsupportedOperationException("TODO card 19")` e o estado inicial tem zero segundos. Assim, a tela real abre ao concluir o draft, mas aguarda o servidor com ações desabilitadas. O card 19 precisa fornecer o prazo da rodada, processar ações e emitir `rodadaResolvida` para uma batalha completa. A interface não inventa um timer ou resultado local.

## Validação

`mvn test` executa os testes existentes e `TelaBatalhaTest`, que cobre HP, reservas derrotadas, log sem duplicação, bloqueio por tempo, ataque, troca, espera pelo adversário e chegada da próxima rodada durante uma chamada remota.
