# Motor de batalha e integração das rodadas

## Regras

`MotorBatalha` mantém HP e posição ativa das três criaturas de cada jogador.
O primeiro slot do draft inicia ativo; os demais compõem a reserva, na ordem
do time. As reservas próprias incluem as criaturas derrotadas, com HP zero,
para preservar o significado dos índices recebidos pelo cliente.

1. As duas ações são validadas antes de alterar qualquer estado.
2. Trocas acontecem antes de todos os ataques; o ataque adversário atinge a nova ativa.
3. Ataques acontecem em ordem decrescente de velocidade. O catálogo não tem
   velocidades repetidas; em um catálogo alternativo com empate, A age primeiro.
4. O dano é `Math.round(poder * 0.40 * multiplicador)`: vantagem 2, mesmo tipo 1,
   desvantagem 0,5. O HP fica limitado a zero e o log registra a perda efetiva.
5. Uma criatura derrubada perde seu ataque. A primeira reserva viva é promovida
   após os ataques, antes do início da próxima rodada.
6. A eliminação das três criaturas encerra a partida. O log relata ataques,
   efetividade, trocas, nocautes, substituições e vencedor.

Exemplos: Jato de poder 40 contra Fogo causa 32 HP; Brasa viva de poder 60
contra Água causa 12 HP. Não há ataque adicional da criatura que substitui uma
ativa derrubada na mesma rodada.

## Fluxo integrado

- `ServicoJogoImpl.enviarAcao` autentica a sessão e delega à partida.
- `Partida` valida a fase, o jogador, o número da rodada e a legalidade da ação.
- `RodadaSimultanea` recebe as decisões e fecha uma única vez por segunda ação
  ou timeout. A conferência do prazo monotônico rejeita ações vencidas mesmo se
  a thread do timer estiver ocupada.
- `MotorBatalha` aplica as regras sob o monitor da partida. As chamadas de rede
  continuam no executor do notificador, sem reter o monitor da sala.
- A rodada seguinte inicia com 30 segundos e os clientes recebem
  `rodadaResolvida`, com suas visões individuais. A reserva adversária expõe
  apenas a quantidade de criaturas vivas, nunca nomes ou HP.
- Ao terminar, os dois clientes recebem resultados com os times completos em
  suas respectivas perspectivas. O timer da sala é fechado e o serviço libera
  as sessões no lobby. O W.O. preserva o HP e a quantidade de rodadas já jogadas.
- `TelaBatalha` atualiza o relógio, ações, HP e log; `TelaResultado` apresenta
  vitória/derrota, rodadas, abates e equipes, com retorno ao lobby e nova busca.

O monitor da barreira decide o fechamento e é liberado antes de chamar o motor.
O serviço libera os participantes sem segurar o monitor da partida ao entrar no
lock do lobby. Uma resolução que perde a disputa para o W.O. não altera mais o
estado nem publica outra rodada.

## Contrato de envio

O cliente envia `new Acao(tipo, indice, numeroRodada)`, usando o número recebido
no estado autoritativo. O servidor rejeita números antigos, futuros ou ausentes
com `ForaDaRodadaException`. O construtor de dois argumentos permanece para uso
local do motor e do fallback. A versão de serialização de `Acao` passou a 2:
recompile e reinicie servidor e todos os clientes juntos.

## Validação

```powershell
mvn test
```

Os testes cobrem os multiplicadores e a escala de dano, prioridade de troca,
velocidade, perda de ação por nocaute, promoção da reserva, eliminação total,
preservação de HP, ações inválidas, mensagens atrasadas, timeouts reais, W.O.,
liberação das sessões e nova partida. O teste `BatalhaRmiTest` usa dois clientes
JavaFX e um servidor RMI real, envia ações pelos botões e acompanha uma partida
de seis rodadas até os resultados. Ele gera capturas em
`cliente/target/batalha-integrada-preview.png` e
`cliente/target/resultado-integrado-preview.png`.

## Teste manual

1. Execute `mvn install` na raiz para compilar os três módulos e disponibilizar
   as dependências locais do cliente.
2. Em outro terminal na raiz, execute o servidor:

   ```powershell
   java -cp "servidor/target/servidor-1.0-SNAPSHOT.jar;compartilhado/target/compartilhado-1.0-SNAPSHOT.jar" br.ucsal.sd.arena.servidor.ServidorMain
   ```

3. Abra dois terminais e execute `mvn -pl cliente javafx:run` em cada um.
4. Conecte dois nomes diferentes ao mesmo endereço e porta. Busque uma partida
   nos dois clientes e selecione três criaturas por jogador.
5. Confira o relógio de 30 segundos e envie ataque/troca em cada cliente. Ao
   enviar apenas uma ação, o cliente deve aguardar; ao enviar a segunda, ambos
   recebem o resultado. Deixe o prazo expirar para verificar o ataque básico.
6. Jogue até eliminar as três criaturas adversárias. Confira os times e o total
   de rodadas no resultado. Volte ao lobby ou busque outra partida sem reconectar.

Se `mvn` não estiver no PATH, use o executável da instalação local do Maven.
Esta tarefa utiliza a base da task 20; a integração de branches deve preservar
também essa implementação. A política existente de heartbeat e W.O. é
preservada; recuperação de sessão por reconexão fica fora do escopo deste motor.
