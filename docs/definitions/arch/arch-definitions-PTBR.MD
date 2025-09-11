# Kikwiflow - Manifesto da Arquitetura

Este documento fornece uma visão geral da arquitetura e da filosofia de design do Kikwiflow. Ele é destinado a desenvolvedores e arquitetos que desejam entender o "porquê" por trás de nossas decisões técnicas.

## 1. Filosofia e Princípios Fundamentais

A arquitetura do Kikwiflow é guiada por princípios que visam resolver as dores crônicas das plataformas de BPM tradicionais:

*   **Segurança e Governança por Design:** Segurança não é um "add-on", é a nossa fundação. Abandonamos padrões inseguros em favor de mecanismos que promovem código seguro, testável e governado.
*   **Performance Nativa para a Nuvem:** Construído sobre Java 21+ e Virtual Threads, o Kikwiflow foi projetado para alta concorrência e baixa latência, ideal para arquiteturas de microserviços.
*   **Developer Experience (DX):** Acreditamos que um framework poderoso deve ser um prazer de usar. Nossa arquitetura modular e baseada em Spring Boot visa maximizar a produtividade do desenvolvedor.
*   **Desacoplamento Rigoroso (SOLID na Prática):** Uma separação clara de responsabilidades entre módulos (`api`, `core`, `autoconfigure`, `starter`) garante um sistema coeso, testável e extensível.

## 2. Arquitetura Modular

O sistema é dividido em módulos Maven distintos, cada um com uma responsabilidade clara, organizados em camadas lógicas:

*   **Contratos (APIs):** Módulos leves (`kikwi-model`, `kikwi-delegate-api`, `kikwi-rule-api`, `kikwi-runtime-persistence-api`) que definem as interfaces e modelos de dados públicos e estáveis.
*   **Core (A Engine):** O `kikwi-core` contém a lógica de execução, o parser de BPMN e os serviços internos. Ele é agnóstico a frameworks.
*   **Integração (Spring):** Módulos `-autoconfigure` e `-starter` que integram o Kikwiflow de forma transparente ao ecossistema Spring Boot.
*   **Implementações (Addons):** Módulos "plugáveis" que fornecem implementações concretas, como o `kikwi-in-memory-addons`.

## 3. Componentes e Padrões Chave

A execução de um processo é orquestrada por um conjunto de componentes centrais:

1.  **`KikwiflowEngine`**: A fachada principal para comandos (`startProcess`, `completeExternalTask`).
2.  **`ProcessExecutionManager`**: Orquestra a execução síncrona de um segmento do processo, iterando sobre o grafo de nós.
3.  **`ContinuationService`**: Responsável por lidar com o resultado de uma execução. Ele interpreta a "continuação", cria as tarefas pendentes (`ExternalTask`, `ExecutableTask`) e orquestra a persistência através da `UnitOfWork`.
4.  **`JobAcquirer`**: Um worker de background, rodando em uma Virtual Thread, que busca e executa `ExecutableTask`s pendentes (jobs), como timers e continuidades assíncronas.

## 4. Decisões de Design Críticas

Nossas escolhas arquiteturais visam maximizar a segurança, clareza e manutenibilidade.

### 4.1. Lógica de Decisão: O Padrão "Rule Directory"
**O Problema:** Linguagens de expressão (SPEL/JUEL) embutidas no BPMN são inseguras, difíceis de manter e ilegíveis para analistas de negócio.

**A Solução Kikwiflow:** Abandonamos completamente as expressões. A lógica de decisão em gateways é definida através de `DecisionRule`s, que são classes Java puras, seguras e testáveis. O BPMN apenas referencia a regra por uma chave, desacoplando o fluxo da implementação.

### 4.2. Privacidade de Dados: Role Based Access Control (RBAC) Nativo
**O Problema:** Variáveis de processo frequentemente contêm dados sensíveis (LGPD/GDPR), e as plataformas tradicionais não oferecem controle de acesso granular.

**A Solução Kikwiflow:** Nosso `ProcessVariable` permite que cada variável tenha um conjunto de `visibleToRoles`. A camada de consulta (`kikwi-query-api`) é responsável por filtrar as variáveis com base nas permissões do usuário, garantindo a privacidade por design.

### 4.3. Governança: Validação em Tempo de Deploy
**O Problema:** É comum implantar processos com referências quebradas a classes Java ou regras, causando erros em tempo de execução.

**A Solução Kikwiflow:** Implementamos um `DeployValidator` que é executado **antes** de um processo ser persistido. Ele verifica se todas as referências a `JavaDelegate`s e `DecisionRule`s são válidas e resolvíveis, impedindo o deploy de processos "quebrados".

### 4.4. Persistência e Transações: O Padrão Unit of Work
**O Problema:** Garantir que múltiplas alterações no estado de um processo (atualizar a instância, criar uma tarefa, deletar outra) sejam salvas atomicamente.

**A Solução Kikwiflow:** Usamos o padrão `Unit of Work`. Todas as operações de escrita são agrupadas em um objeto `UnitOfWork`, que é então comitado em uma única transação pelo método `KikwiEngineRepository.commitWork()`. Isso garante a consistência total do estado.

### 4.5. Concorrência: Virtual Threads
**O Problema:** Motores de processo precisam lidar com milhares de instâncias concorrentes, muitas delas em estados de espera (I/O). Threads de plataforma tradicionais são um recurso caro e limitante.

**A Solução Kikwiflow:** O Kikwiflow é construído sobre **Java 21+ e Virtual Threads**. Nosso `TaskAcquirer` e outros componentes assíncronos usam um `ExecutorService` baseado em threads virtuais, permitindo uma escalabilidade massiva com uma pegada de recursos mínima.

### 4.6. CQRS Nativo: Performance e Escalabilidade
**O Problema:** Em sistemas transacionais, as mesmas estruturas de dados usadas para escrita (otimizadas para consistência) são frequentemente usadas para leitura, resultando em consultas lentas e complexas que podem degradar a performance do sistema como um todo.

**A Solução Kikwiflow:** Implementamos o padrão **Command Query Responsibility Segregation (CQRS)** de forma nativa e pragmática:
*   **O Lado do Comando (Command):** A `KikwiflowEngine` é a única porta de entrada para alterar o estado do sistema. Suas operações (`startProcess`, `completeExternalTask`) são otimizadas para a execução e consistência transacional, culminando em um `UnitOfWork` atômico.
*   **O Lado da Consulta (Query):** A `kikwi-query-api` (`ExternalTaskQueryService`) oferece uma fachada de leitura completamente separada. Ela é projetada para ser rápida, eficiente e é a camada responsável por aplicar lógicas de leitura, como a filtragem de variáveis baseada em RBAC.
*   **O Benefício:** Esta separação nos permite escalar os lados de leitura e escrita de forma independente e garante que consultas complexas para dashboards ou UIs nunca degradem a performance da orquestração dos seus processos críticos.


## 5. Funcionalidades Atuais da Engine

A versão atual do Kikwiflow suporta um conjunto robusto de funcionalidades para a orquestração de processos:

*   **Deploy de Processos:** Implantação de definições de processo a partir de arquivos `.bpmn`.
*   **Elementos BPMN Suportados:**
    *   Eventos: `startEvent`, `endEvent`.
    *   Tarefas: `serviceTask` (automação), `userTask` / `manualTask` (trabalho humano/externo).
    *   Gateways: `exclusiveGateway` (baseado em `DecisionRule`s).
    *   Eventos de Borda: `boundaryEvent` com `timerEventDefinition` (timers interruptivos).
*   **Continuidades Assíncronas:** Suporte completo para os atributos `camunda:asyncBefore` e `camunda:asyncAfter` para definir limites de transação.
*   **Execução de Lógica Customizada:**
    *   **Automações:** Padrão `JavaDelegate` para executar código Java em `serviceTask`s.
    *   **Decisões:** Padrão `DecisionRule` para encapsular a lógica de gateways.
*   **API de Gerenciamento (CQRS):**
    *   **Comandos:** Iniciar processos, completar tarefas externas.
    *   **Consultas:** Buscar tarefas e instâncias de processo com segurança (RBAC).
*   **Timers Interruptivos:** Capacidade de modelar e executar lógicas de timeout e SLA em qualquer atividade que represente um ponto de espera.