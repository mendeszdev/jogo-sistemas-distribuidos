# Teste de estresse do draft

`DraftEstresseTest` executa quatro cenários com 32 threads de plataforma e
1.000 iterações cada. Em cada iteração, todas as threads sinalizam prontidão
antes de uma única largada por `CountDownLatch`. As chamadas usam os dois
jogadores de uma sala: várias threads representam requisições concorrentes de
um mesmo jogador, não jogadores extras.

| Cenário | Verificação por iteração |
| --- | --- |
| Mesma criatura | 32 pedidos para o mesmo ID, alternando os jogadores; uma escolha aceita e 31 recusas por indisponibilidade. |
| Limite por jogador | 32 pedidos para os dez IDs do pool pelo mesmo jogador; exatamente três escolhas e recusas por limite, mantendo o adversário sem escolhas. |
| Pool compartilhado | Ambos disputam os dez IDs; exatamente três criaturas por time, seis IDs distintos e transição para batalha. |
| Escolhas contra timeout | 31 pedidos competem com uma chamada ao fechamento por timeout, cuja posição entre as tarefas alterna a cada iteração; ambos terminam com três criaturas distintas e um timeout tardio não altera o estado. |

As invariantes são verificadas também em snapshots capturados pelas threads
durante a disputa: limite de três, ausência de repetição no time e ausência de
uma criatura nos dois times. Após reunir todos os `Future`s, o teste compara as
duas perspectivas e os totais esperados. Exceções inesperadas e falhas nas
threads propagam para o JUnit; não são contadas como recusas normais.

Ao todo são 128.000 tarefas sincronizadas, sendo 127.000 pedidos de escolha e
1.000 fechamentos por timeout, distribuídos em 4.000 salas sequenciais. Cada
sala é encerrada ao fim da iteração e o teste verifica que não restam prazos
de draft na fila. Cada cenário possui um limite global de
120 segundos para não deixar a suíte bloqueada.

## Executar e guardar evidência

Na raiz do projeto, execute no PowerShell:

```powershell
./scripts/testar-estresse-draft.ps1
```

O script usa o Maven do PATH ou procura uma instalação no cache local do Maven
Wrapper. Também aceita um caminho explícito:

```powershell
./scripts/testar-estresse-draft.ps1 -Maven 'C:/ferramentas/apache-maven/bin/mvn.cmd'
```

Ele executa apenas os quatro testes da task 29 e salva um resumo real em
[`evidencias/task-29-estresse-draft.txt`](evidencias/task-29-estresse-draft.txt),
com data UTC, comando, carga, contadores, duração e resultado do Maven. Um teste
com falha ou sem os quatro cenários produz status `FALHOU` e erro no script.
O arquivo de evidência é substituído a cada execução.

O mesmo teste faz parte de `mvn -pl servidor -am test`. Para execução isolada:

```powershell
mvn -pl servidor -am test '-Dtest=DraftEstresseTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

## Leitura da evidência

- `aceitas`: chamadas de escolha concluídas sem exceção.
- `indisponiveis`: criatura já escolhida por uma chamada anterior.
- `limite`: jogador já possui três criaturas.
- `fora_da_fase`: o draft terminou antes de a chamada obter o monitor da sala.
- `timeouts`: fechamentos acionados pelo próprio cenário de disputa com timeout.
- `automaticas`: escolhas que o fechamento por timeout precisou completar, calculadas pela diferença entre o time final e as escolhas manuais aceitas.
- `maior_time`: maior tamanho observado nos times finais das iterações.
- `duplicacoes=0` e `violacoes=0`: a linha só é emitida após todas as asserções
  do cenário passarem; falhas não geram uma linha de aprovação.

Os contadores de recusa e tempos podem variar conforme o escalonamento. Não é
exigida alternância de vencedor: a propriedade testada é exclusividade, não
justiça do escalonador.

Este é um teste de concorrência no estado autoritativo do servidor, sem RMI.
O fechamento por timeout é chamado diretamente para provocar a disputa sem
esperar o prazo real. Os testes existentes de draft e de clientes RMI cobrem
também expiração real e integração pela rede. A evidência demonstra o resultado
da carga executada, sem garantir a ausência de todo entrelaçamento possível.
