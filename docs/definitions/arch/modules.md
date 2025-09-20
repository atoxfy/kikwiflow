# Arquitetura de Módulos do Kikwiflow

O Kikwiflow é projetado com uma arquitetura modular e desacoplada, seguindo as melhores práticas do ecossistema Spring Boot e os princípios de design SOLID. Cada módulo tem uma responsabilidade única e bem definida, o que garante clareza, manutenibilidade e extensibilidade.

Os módulos são organizados nas seguintes camadas lógicas:

---

## 1. Camada de Contratos (APIs)

Estes módulos definem as interfaces e os modelos de dados que formam o "contrato público" do Kikwiflow. Eles são leves e contêm o mínimo de lógica, servindo como a fundação para todo o ecossistema.

*   **`kikwi-model`**
    *   **Propósito:** O coração do nosso domínio. Contém os modelos de dados imutáveis (records) que representam os conceitos do processo, como `ProcessInstance`, `ExternalTask`, `ExecutableTask` e o `ProcessVariable` com suporte a RBAC.

*   **`kikwi-runtime-persistence-api`**
    *   **Propósito:** Define o contrato para a camada de persistência. Contém as interfaces `KikwiEngineRepository`, `CommandRepository` e `QueryRepository`, garantindo que a engine seja agnóstica ao banco de dados utilizado.

*   **`kikwi-runtime-query-api`**
    *   **Propósito:** Define a API pública para consultas de alto nível. Contém a interface `ExternalTaskQueryService`, que serve como a fachada segura para a camada de leitura (CQRS), respeitando as regras de visibilidade de variáveis.

*   **`kikwi-delegate-api`**
    *   **Propósito:** Define o ponto de extensão para automações. Contém a interface `JavaDelegate`, que permite aos desenvolvedores implementar a lógica de negócio para `ServiceTasks`.

*   **`kikwi-rule-api`**
    *   **Propósito:** Define o ponto de extensão para a lógica de decisão. Contém a interface `DecisionRule`, que é a base do nosso padrão "Rule Directory" para gateways, eliminando a necessidade de linguagens de expressão inseguras.

---

## 2. Camada de Execução (Core)

Esta camada contém a "inteligência" e a lógica de execução do motor de processos.

*   **`kikwi-core`**
    *   **Propósito:** O cérebro do Kikwiflow. Contém a `KikwiflowEngine`, o `ProcessExecutionManager` que executa o grafo do processo, o `TaskAcquirer` que gerencia o trabalho assíncrono em Virtual Threads, e o `ContinuationService` que orquestra a persistência do estado.

---

## 3. Camada de Integração (Spring Boot)

Estes módulos são a "cola" que conecta o Kikwiflow ao ecossistema Spring Boot, tornando a integração transparente e automática.

### Auto-Configurações

*   **`kikwi-spring-boot-autoconfigure`**
    *   **Propósito:** Contém a auto-configuração principal. É responsável por criar e configurar os beans essenciais, como a `KikwiflowEngine` e o `DelegateResolver`.

*   **`kikwi-runtime-query-spring-boot-autoconfigure`**
    *   **Propósito:** Contém a auto-configuração para a camada de consulta. Cria o bean `ExternalTaskQueryService`.

*   **`kikwi-in-memory-spring-boot-autoconfigure`**
    *   **Propósito:** Contém a auto-configuração para a persistência em memória. É ativado condicionalmente se o addon correspondente estiver no classpath.

### Starters

*   **`kikwi-spring-boot-starter`**
    *   **Propósito:** O ponto de entrada principal para os usuários. É um "meta-módulo" que agrega o `kikwi-spring-boot-autoconfigure` e outras dependências essenciais. O usuário adiciona apenas este starter para obter a funcionalidade do motor.

*   **`kikwi-runtime-query-spring-boot-starter`**
    *   **Propósito:** O ponto de entrada para a funcionalidade de consulta. Agrega o `kikwi-runtime-query-spring-boot-autoconfigure`.

---

## 4. Camada de Implementações (Addons)

Estes módulos fornecem implementações concretas para as APIs definidas. Eles são opcionais e "plugáveis".

*   **`kikwi-in-memory-addons`**
    *   **Propósito:** Fornece uma implementação completa da `KikwiEngineRepository` que armazena todos os dados em memória. Ideal para testes, desenvolvimento rápido e cenários de curta duração.

*   **`kikwi-runtime-persistence-mongodb`** (Futuro)
    *   **Propósito:** Fornecerá uma implementação da `KikwiEngineRepository` otimizada para o MongoDB, servindo como a nossa primeira opção de persistência de produção.

---

## 5. Camada de Aplicação e Ferramentas

Estes módulos são consumidores do ecossistema Kikwiflow, servindo como exemplos ou ferramentas reutilizáveis.

*   **`kikwi-management-rest`**
    *   **Propósito:** Um addon opcional que expõe uma API REST completa para gerenciamento de processos. Ele consome os starters de `core` e `query` para orquestrar as operações de comando e consulta.

*   **`sample-linear-human-tasks-spring-boot`**
    *   **Propósito:** Uma aplicação de exemplo que demonstra como um usuário final pode consumir os starters do Kikwiflow para construir uma aplicação de negócio real.

---

## 6. Camada de Testes

*   **`kikwi-core-testing`**
    *   **Propósito:** Contém utilitários para facilitar os testes do Kikwiflow, como a `AssertableKikwiEngine`, que é uma implementação em memória do repositório com métodos de asserção adicionais.

*   **`kikwi-core-tests`**
    *   **Propósito:** Contém os testes de ponta a ponta (E2E) para o `kikwi-core`. Estes testes validam a funcionalidade completa da engine, desde o deploy até a execução de cenários complexos como timers interruptivos.