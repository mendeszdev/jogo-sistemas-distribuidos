# Task 17 — tela de draft

`ClienteApp` é o responsável pela navegação da sessão. O lobby chama
`mostrarDraft` em `partidaEncontrada`, passando a mesma `ConexaoServidor`.
O token não é recriado. O callback RMI continua registrado no lobby, que
encaminha os eventos de draft, batalha e encerramento na fila do JavaFX.
Isso preserva a ordem entre abrir a tela e receber o primeiro estado.

`TelaDraft.atualizar(EstadoDraft)` aceita também chamadas de outras threads
e estados manuais para testes. Nenhuma escolha é antecipada na interface:
somente os IDs recebidos do servidor determinam as marcações. O botão
enviado fica temporariamente bloqueado para evitar cliques duplicados.
O relógio local é apenas visual; um novo valor do servidor substitui o local,
inclusive se for maior. Chegar a zero não decide o fim da fase.

As dez artes do card 09 estão em `cliente/src/main/resources/br/ucsal/sd/arena/cliente/criaturas/`,
nomeadas pelo ID do catálogo oficial (1 a 10). São cópias dos ícones fornecidos,
portanto não dependem da pasta Desktop no computador do jogador.

Ao receber `batalhaIniciada`, `ClienteApp.mostrarBatalha(EstadoBatalha)` encerra
os timers do draft e troca para uma mensagem provisória de conclusão. Esse é
o ponto de integração da futura tela de batalha. Nesta revisão, `Partida`
ainda não emite esse callback: apenas muda para BATALHA. Enquanto isso, o
estado com ambos os times completos mostra “Draft concluído” sem inferir uma
transição pelo relógio local. Encerramento por W.O. também substitui o draft.

## Validação

Executar na raiz com JDK 21 e Maven:

```shell
mvn test
```

Os testes do cliente usam JavaFX real, exigindo uma sessão gráfica disponível.
`TelaDraftTest` cobre visões do servidor, limite, chamada lenta sem bloquear a
interface, ausência de marcação otimista, mensagens temporárias, sincronização
do relógio e ordem de encaminhamento dos callbacks.

`DraftRmiTest` usa registry em porta livre, servidor e duas conexões RMI reais
no mesmo processo. Disputa a mesma criatura pelos botões, verifica um único
vencedor, a marcação adversária e o aviso do perdedor, e espera os 30 segundos
para verificar o preenchimento automático e a conclusão dos dois times.
Também gera `cliente/target/draft-preview.png` para inspeção da tela renderizada.
A dependência do cliente no módulo servidor existe somente no escopo de teste.
