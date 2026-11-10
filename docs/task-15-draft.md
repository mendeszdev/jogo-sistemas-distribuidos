# Task 15 — draft concorrente (Samuel)

## Fluxo implementado

O matchmaking já chama `Partida.iniciar()`. Essa chamada é idempotente: agenda um
timeout de 30 segundos, enfileira `partidaEncontrada` e envia o estado inicial do
draft aos dois jogadores. O pool da sala contém as 10 criaturas oficiais do card
05, com seus atributos e os 40 ataques. Cada sala tem sua lista e seus conjuntos
de escolhas; apenas os DTOs imutáveis do catálogo são compartilhados.

`ServicoJogoImpl.escolherCriatura` valida o token, encontra a partida da sessão e
delega a escolha. Token inválido gera `SessaoInvalidaException`; jogador sem
partida recebe `FaseInvalidaException`.

## Como explicar a concorrência na apresentação

1. **Um lock por sala.** A thread RMI segura `Partida.lock` enquanto verifica a
   fase, a disponibilidade e o limite, nessa ordem, e registra o dono. Outra
   thread só entra depois: se pedir o mesmo ID, recebe
   `CriaturaIndisponivelException`. ID inexistente também é indisponível.
2. **Timer e escolha usam o mesmo lock.** O timeout embaralha os IDs livres e
   distribui sem reposição até cada jogador ter três. Se as escolhas manuais
   completarem os dois times antes, o timer é cancelado.
3. **Cancelamento sozinho não basta.** O timer pode já ter acordado. Ele passa
   uma época capturada no início; o fechamento verifica fase e época sob lock.
   `transitar` altera a fase e incrementa a época, invalidando chamadas antigas.
   W.O. usa esse mesmo mecanismo e cancela o timer.
4. **Callbacks não fazem rede no trecho crítico.** Sob lock, o servidor captura
   dois `EstadoDraft` imutáveis e os enfileira no `NotificadorCallbacks`. Isso
   conserva a ordem das escolhas. As threads do notificador executam os métodos
   remotos fora do lock; cada jogador recebe sua própria perspectiva.

Os segundos restantes usam `System.nanoTime()`, um relógio monotônico, e são
arredondados para cima. Cada escolha manual ou automática gera um estado para
cada jogador, além do estado inicial. O estado final informa zero segundos.

## Limite do card

O término muda a fase para `BATALHA`. A criação do `EstadoBatalha`, a escolha de
criatura ativa e o envio de `batalhaIniciada` pertencem ao card 19.
Os arquivos gráficos foram consultados como referência de identidade; esta
task altera o servidor e não inclui mudanças na interface JavaFX.

## Verificação

Na raiz do projeto, com Java 21 e Maven:

```sh
mvn -pl servidor -am test
```

`PartidaDraftTest` cria sessões falsas sem depender do matchmaking para os
cenários de draft. Verifica 1.000 disputas simultâneas pelo mesmo ID e 1.000
corridas entre última escolha e timeout, usando `CountDownLatch` e propagando
falhas das threads pelos `Future`s. Também cobre limite e precedência das
exceções, timer real sem escolhas, snapshots personalizados, isolamento entre
salas, W.O., catálogo e integração com o serviço.

O construtor de teste permite injetar scheduler e duração curta; produção mantém
30 segundos. O executor de produção é compartilhado, usa thread daemon e remove
timers cancelados da fila. As salas continuam com locks e estados independentes.
