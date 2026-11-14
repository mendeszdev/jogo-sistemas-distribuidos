# Task 24 — Tela de batalha

A transição `batalhaIniciada` abre `TelaBatalha`, usando o catálogo recebido no draft para os nomes, imagens, HP máximo e ataques. A tela apresenta as duas criaturas ativas, barras e valores de HP, reservas próprias, quantidade de reservas adversárias, rodada, relógio e log.

Ataques enviam `Acao(ATACAR, indiceDoAtaque)` e trocas enviam `Acao(TROCAR, indiceNaListaDeReservas)`. Reservas derrotadas ficam desabilitadas. A chamada RMI roda fora da thread JavaFX; durante o envio os botões ficam bloqueados e, após confirmação, a tela mostra “Aguardando adversário...”. HP e criaturas nunca são alterados por cliques locais.

`rodadaResolvida` passa pelo lobby na thread JavaFX, registra os eventos e aplica `estadoAtual`. Uma rodada nova libera as ações; respostas de envios antigos são ignoradas. O relógio usa prazo monotônico e, ao zerar, bloqueia ações e aguarda o servidor. Sair da tela para os timers e descarta retornos tardios.

## Dependência do servidor

A task 18 conecta `Partida.enviarAcao`, a barreira de ações simultâneas e o timer da task 20. O estado inicial recebe o prazo da rodada e `rodadaResolvida` atualiza HP, ativas, reservas e relógio. O envio inclui `Acao.numeroRodada`, que permite rejeitar requisições atrasadas. Ao encerrar, `TelaResultado` apresenta as duas equipes e permite voltar ao lobby ou buscar outra partida. A interface não calcula resultados locais.

## Validação

`mvn test` executa os testes existentes e `TelaBatalhaTest`, que cobre HP, reservas derrotadas, log sem duplicação, bloqueio por tempo, ataque, troca, espera pelo adversário e chegada da próxima rodada durante uma chamada remota.
