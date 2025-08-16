# Kikwiflow - Visão Geral da Arquitetura Técnica

Este documento fornece uma visão geral técnica do motor Kikwiflow, destinado a um público com formação em arquitetura ou engenharia de software.

## 1. Princípios Fundamentais

A arquitetura do Kikwiflow é guiada por vários princípios fundamentais:

*   **Desacoplamento:** Um design altamente modular para garantir a separação de responsabilidades e a manutenibilidade.
*   **Performance:** Aproveitamento de recursos modernos do Java (Virtual Threads) para execução de alto rendimento e sem bloqueio.
*   **Confiabilidade:** Garantia de integridade transacional e entrega de eventos críticos através dos padrões *Unit of Work* e *Outbox*.
*   **Testabilidade:** Um design que facilita testes unitários e de ponta a ponta robustos.

## 2. Arquitetura Modular

O sistema é dividido em módulos Maven distintos, cada um com uma responsabilidade clara:

*   `kikwi-model`: Contém o modelo de domínio (DTOs/records como `ProcessDefinitionSnapshot`, `ProcessInstanceSnapshot`) que representa o estado de um processo. Estes são imutáveis e usados para comunicação.
*   `kikwi-runtime-persistence-api`: Define os contratos (`KikwiEngineRepository`, `UnitOfWork`) para a camada de persistência. Também contém as classes de entidade mutáveis (`ProcessInstanceEntity`).
*   `kikwi-core`: O coração do motor. Contém a lógica de execução (`FlowNodeExecutor`), orquestração (`KikwiflowEngine`) e parsing de BPMN (`DefaultBpmnParser`). Depende dos módulos de API, mas não de nenhuma implementação específica.
*   `kikwi-in-memory-addons`: Uma implementação em memória da API de persistência, principalmente para testes e implantações simples.
*   `kikwi-core-testing`: Fornece *test harnesses* e utilitários (`AssertableKikwiEngine`) para simplificar a escrita de testes de ponta a ponta limpos e legíveis.
*   `kikwi-lightweight-events-api` & `kikwi-critical-event-relay-api`: Definem os contratos para o subsistema de eventos.

## 3. Fluxo de Execução e Componentes Chave

A execução de um processo é orquestrada por um conjunto de componentes centrais:

1.  **`KikwiflowEngine`**: O ponto de entrada principal e orquestrador. Lida com solicitações para implantar, iniciar e completar processos. É o componente responsável por commitar a `UnitOfWork`.
2.  **`ProcessExecutionManager`**: Gere o ciclo de vida de uma única execução de processo. Delega a execução passo a passo para o `FlowNodeExecutor`.
3.  **`FlowNodeExecutor`**: O "cavalo de batalha" do motor. Itera sobre o grafo do processo, executando nós um por um num loop (`runWhileNotFindAStopPoint`).
4.  **Lógica `isWaitState()`**: O `FlowNodeExecutor` interrompe de forma inteligente o seu loop de execução síncrona quando encontra um "estado de espera" (*wait state*). Este é um conceito crucial e inclui:
    *   **`ExternalTask`**: Nós que requerem um gatilho externo para continuar (ex: `HumanTask`, `ReceiveTask`).
    *   **`ExecutableTask` (com continuação assíncrona)**: Nós que são executados por um executor de jobs interno (ex: uma `ServiceTask` assíncrona).
5.  **`TaskExecutor`**: Responsável por executar a lógica dentro de uma tarefa específica, como invocar um `JavaDelegate`.

## 4. Persistência e Gestão de Transações

A integridade transacional é primordial e é alcançada através do padrão **Unit of Work**.

*   **`UnitOfWork` Record**: Um transportador de dados simples e imutável que agrupa todas as alterações de estado destinadas a uma única transação atómica. Isto inclui:
    *   Instância a ser atualizada.
    *   Instância a ser eliminada.
    *   Novas tarefas (executáveis ou externas) a serem criadas.
    *   Eventos críticos a serem adicionados ao *outbox*.
*   **`KikwiEngineRepository.commitWork(UnitOfWork)`**: Este é o único método que escreve na base de dados. O orquestrador do motor (`KikwiflowEngine`) constrói a `UnitOfWork` durante a execução e chama `commitWork` no final de um bloco síncrono ou num estado de espera. Isto garante que, por exemplo, a atualização do estado de uma instância de processo e a criação de uma nova tarefa ocorram atomicamente.

## 5. Arquitetura de Eventos (Modelo Híbrido)

O Kikwiflow emprega uma estratégia de eventos híbrida para equilibrar performance e fiabilidade:

*   **Eventos Críticos (Padrão Outbox)**: Eventos que devem ser entregues de forma fiável (ex: `ProcessInstanceFinished`, `FlowNodeExecuted` para auditoria) não são publicados diretamente. Em vez disso, são persistidos como parte da transação de negócio principal dentro da `UnitOfWork`. Um processo separado (o "Event Relay") lê desta tabela *outbox* e garante a sua entrega a um *message broker*, lidando com novas tentativas e falhas. Isto previne a perda de dados se o *message broker* estiver temporariamente indisponível.
*   **Eventos Leves (Fire-and-Forget)**: Eventos usados para fins não críticos, como métricas em tempo real ou logging, podem ser publicados diretamente para uma fila em memória para tratamento assíncrono. Isto é mais rápido, mas não oferece as mesmas garantias de entrega que o padrão *outbox*.

## 6. Estratégia de Concorrência

O motor foi construído de raiz para tirar partido das **Virtual Threads do Java 21+**. Todas as operações assíncronas, como *event listeners* e futuros *job executors*, usarão um `ExecutorService` baseado em *virtual threads*. Isto permite que o motor lide com um número muito grande de processos concorrentes com uma sobrecarga mínima de threads do sistema operativo, resultando numa excelente escalabilidade e eficiência de recursos.





**Arquitetura de Módulos**: Definição da estrutura de módulos desacoplada (core, model, events-api, runtime-persistence-api, management-api, etc.).

**Arquitetura CQRS**: Separação formal entre Comandos (responsabilidade do core) e Queries (responsabilidade da camada de gestão), permitindo a separação de cargas de trabalho.

**Arquitetura de Eventos Híbrida**: Design do sistema de eventos com dois canais (fire-and-forget para métricas e outbox para eventos críticos), dando ao utilizador o controlo sobre o trade-off entre performance e fiabilidade.

**Padrão Unit of Work**: Definição do contrato UnitOfWork como o mecanismo central para garantir commits atómicos na camada de persistência.

**Estratégia de Concorrência**: Decisão de utilizar Threads Virtuais (Java 21+) como a base para a nossa performance e escalabilidade.

**Estratégia de Testes**: Definição da abordagem de testes, incluindo a criação de módulos de utilitários (testing, in-memory-addons) e o uso de wrappers Assertable para testes limpos.