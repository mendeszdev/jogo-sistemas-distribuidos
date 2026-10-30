# Arena de Elementais

Trabalho de Sistemas Distribuídos — jogo multiplayer usando Java RMI.

## Requisitos

- JDK 21
- Maven 3.9+

## Estrutura

```
arena-elementais/
├── pom.xml            # POM pai (versões, plugins e lista de módulos)
├── compartilhado/     # Contrato comum: interfaces remotas e DTOs
├── servidor/          # Lógica do jogo e registry RMI
└── cliente/           # Interface JavaFX
```

`servidor` e `cliente` dependem de `compartilhado`.

## Compilar

Cada módulo separadamente (o `-am` compila também as dependências):

```bash
mvn -pl compartilhado -am package
mvn -pl servidor -am package
mvn -pl cliente -am package
```

Todos de uma vez:

```bash
mvn clean package
```

## Executar

Cliente (JavaFX):

```bash
mvn -pl cliente -am javafx:run
```

Servidor (depois de existir a classe `ServidorMain`):

```bash
java -cp "servidor/target/servidor-1.0-SNAPSHOT.jar;compartilhado/target/compartilhado-1.0-SNAPSHOT.jar" br.ucsal.sd.arena.servidor.ServidorMain
```

No Linux/macOS, troque `;` por `:` no classpath.
