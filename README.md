# Arena de Elementais

Trabalho de Sistemas Distribuídos — jogo multiplayer usando Java RMI.

## Stack

- Java 21
- Maven multi-módulo
- Comunicação: Java RMI
- Frontend: JavaFX

## Estrutura

```
arena-elementais/
├── pom.xml            # POM pai (versões e módulos)
├── compartilhado/     # Contrato comum: interfaces remotas e DTOs
├── servidor/          # Lógica do jogo e registry RMI
└── cliente/           # Interface JavaFX (FXML, CSS, controllers)
```

`servidor` e `cliente` dependem de `compartilhado`.

## Build

```bash
mvn clean install
```

## Executar o cliente

```bash
mvn -pl cliente javafx:run
```

Antes, ajuste a propriedade `cliente.main.class` em `cliente/pom.xml` para a classe `Application` real.

## Regras de uso de IA

- **Proibido** gerar, completar, revisar ou sugerir código Java em `servidor` e `compartilhado` (interfaces remotas, DTOs, lógica do jogo, concorrência, sincronização, registry, callbacks).
- **Permitido** no módulo `cliente` apenas a parte visual (telas JavaFX, FXML, CSS, layout).
- **Permitido** configurar build (`pom.xml`), estrutura de pastas, `.gitignore` e README.
