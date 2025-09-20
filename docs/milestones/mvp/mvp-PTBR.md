# Milestone: MVP - The Foundation Engine

## Objetivo

O objetivo deste milestone é entregar a primeira versão funcional e de ponta a ponta do ecossistema **kikwiflow**. Ao final deste MVP, teremos uma engine de processos leve, performática e pronta para ser utilizada por desenvolvedores, validando as nossas principais decisões arquiteturais e demonstrando o nosso valor disruptivo em relação às soluções tradicionais.

### Funcionalidades Principais do MVP

Este milestone foca-se na entrega das seguintes capacidades essenciais:

-   **Execução de Tarefas de Serviço:** Suporte completo para `serviceTask` com compatibilidade para `JavaDelegate`, permitindo a execução de lógica de negócio em Java.

-   **Ciclo de Vida de Tarefas Humanas:** Suporte para `userTask`, incluindo a criação de tarefas pendentes (`WaitState`), e a capacidade de as consultar e completar através de uma API.

-   **Roteamento Condicional:** Implementação do `exclusiveGateway` para permitir a criação de fluxos de trabalho com lógica condicional.

-   **Persistência com MongoDB:** Entrega da primeira implementação oficial da camada de persistência, `kikwi-persistence-mongodb`.

-   **Cockpit v0.1 (`kikwi-pulse`):** Uma primeira versão do nosso cockpit, com um BFF (Backend-for-Frontend) e uma UI (Interface de Utilizador) mínima, capaz de listar tarefas humanas pendentes e permitir a sua conclusão.

### Entregas Detalhadas do MVP

A seguir, a lista detalhada de objetivos técnicos e entregas para este milestone.

-   **Core & Execução:**

    -   Disponibilizar interface para deploy de definições que não seja "amarrada" a BPMN e permita extensão futura para outras notações/parsers.
    -   Realizar o parse de BPMN para um grafo direcional
    -   Interpretar e executar tarefas automatizadas (ServiceTasks) utilizando `JavaDelegate`.
    -   Interpretar, criar e concluir tarefas externas à engine (HumanTasks).
    -   Interpretar gateways exclusivos.
    -   Implementar a navegação no processo.
    -   Viabilizar a precificação de instâncias de processo.

-   **Arquitetura & Módulos:**

    -   Disponibilizar a biblioteca com os domínios comuns (`kikwi-model`)
    -   Definir a estratégia que permite segregar dados em execução de dados históricos.
    -   Definir a metodologia para garantir que a persistência de dados (eventos/histórico) possa ser feita sem prejudicar a performance da engine.
    -   Disponibilizar a possibilidade de ser embarcada numa aplicação Spring Boot (`kikwi-spring-boot-starter`).

-   **Persistência:**

    -   Disponibilizar mecanismo para execução 100% em memória (`kikwi-in-memory-addons`).
    -   Disponibilizar a API de persistência de dados runtime (`kikwi-runtime-persistence-api`).
    -   Disponibilizar a implementação da API de persistência para MongoDB (`kikwi-persistence-mongodb`).

-   **Gestão & Observabilidade:**

    -   Disponibilizar um addon para observabilidade nativa (métricas).
    -   Disponibilizar as APIs para gestão da engine (`kikwi-management-api`).
    -   Lançar o `kikwiflow-pulse` v1.0.0 com uma visão gerencial básica (listar e completar tarefas).

-   **Testes & Qualidade:**

    -   Disponibilizar addons para testes unitários, de integração e E2E (`kikwi-testing`).
    -   Realizar a primeira versão dos testes de performance.
    -   Implementar testes E2E para os principais fluxos.