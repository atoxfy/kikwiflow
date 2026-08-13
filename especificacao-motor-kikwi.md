---
id: especificacao-motor-kikwi
title: Especificação do Motor Kikwiflow para Geração de Projetos
sidebar_position: 1
---

# Especificação do Motor Kikwiflow para Geração de Projetos

> **Objetivo deste documento**: servir de referência técnica para a construção de um **exportador** que lê um
> arquivo de definição de processo `.kikwi` (JSON) e gera um projeto Spring Boot / Java 21 capaz de executá-lo
> usando o motor Kikwiflow.
>
> Este documento foi produzido a partir da leitura direta do código-fonte do motor (`kikwi-*`) e do projeto de
> referência `sample-onboarding-process`. Onde a documentação em `docs/` diverge do código, o código prevalece —
> essa divergência é sinalizada explicitamente nas seções relevantes.

## 1. Visão geral do modelo de execução

Um processo Kikwiflow **não é BPMN-XML**. Ele é um grafo de nós (`flowNodes`) descrito em JSON, onde cada nó tem
um `type` que é resolvido polimorficamente pelo parser Jackson (`FlowNodeDefinitionMixin`) para uma classe Java
concreta em `kikwi-model`. A lógica de negócio de cada nó — quando existe — não é escrita em uma expression
language (SPEL/JUEL), mas em **classes Java simples registradas como beans Spring**, resolvidas em tempo de
execução pelo nome do bean.

Isso significa que o exportador tem duas responsabilidades centrais:

1. Gerar/posicionar o **arquivo `.kikwi` do processo** dentro do classpath do projeto gerado (para autodeploy).
2. Gerar **stubs de classes Java** (`TaskHandler`, `AnswerProvider`, `DueDateProvider`) para cada `executor` /
   `providerBean` referenciado no processo, anotados corretamente para serem descobertos pelo Spring.

## 2. Anatomia de um projeto gerado

Baseado na estrutura real de `sample-onboarding-process`:

```
meu-processo-app/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/empresa/processo/
        │       ├── Application.java                     # @SpringBootApplication
        │       └── executors/
        │           ├── MinhaTarefaTaskHandler.java       # implements TaskHandler
        │           └── decision/
        │               └── MinhaDecisaoAnswerProvider.java  # implements AnswerProvider
        └── resources/
            ├── application.yml                           # config do motor (ver seção 5)
            └── processes/                                 # ⚠ path do autodeploy, ver nota abaixo
                └── meu-processo.kikwi                      # a definição .kikwi exportada
```


Não há módulo `-web`/frontend nesse projeto — a aplicação gerada é um serviço Spring Boot backend puro que expõe
REST via `kikwi-management-rest-spring-boot-starter` (API de consulta/gestão de instâncias) quando esse starter
é incluído.

## 3. Dependências Maven necessárias

Todos os módulos do motor são publicados sob `groupId io.kikwiflow`, com BOM implícito herdado do
`kikwiflow-parent` (versão observada: `0.1.65`, Spring Boot `3.5.4`, Java 21). Um projeto gerado deve declarar:

```xml

<properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>

<dependencies>
    <!-- Web básico -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Núcleo do motor: engine + autoconfigure + auto-deploy -->
    <dependency>
        <groupId>io.kikwiflow</groupId>
        <artifactId>kikwi-spring-boot-starter</artifactId>
        <version>0.1.65</version>
    </dependency>

    <!-- Persistência: implementação MongoDB do KikwiEngineRepository -->
    <dependency>
        <groupId>io.kikwiflow</groupId>
        <artifactId>kikwi-runtime-persistence-mongodb-spring-boot-starter</artifactId>
        <version>0.1.65</version>
    </dependency>

    <!-- Lado de query (ExternalTaskQueryService) -->
    <dependency>
        <groupId>io.kikwiflow</groupId>
        <artifactId>kikwi-runtime-query-spring-boot-starter</artifactId>
        <version>0.1.65</version>
    </dependency>

    <!-- Opcional: API REST de gestão (process-instances/search, external-tasks, incidents, stats/SSE) -->
    <dependency>
        <groupId>io.kikwiflow</groupId>
        <artifactId>kikwi-management-rest-spring-boot-starter</artifactId>
        <version>0.1.65</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

`kikwi-spring-boot-starter` é o único módulo estritamente obrigatório para o motor funcionar (traz
`kikwi-spring-boot-autoconfigure`, que registra `KikwiflowAutoConfiguration`). O `KikwiflowEngine` exige uma
implementação de `KikwiEngineRepository` — hoje há duas opções: `kikwi-runtime-persistence-mongodb-spring-boot-starter`
 e `kikwi-in-memory-spring-boot-starter`. O exportador deve escolher o
MongoDB starter por padrão para projetos "de verdade".

## 4. Classe de aplicação obrigatória

```java
@SpringBootApplication
@EnableMongoRepositories(basePackages = {"com.empresa.processo", "io.kikwiflow"})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

`@EnableMongoRepositories` com o pacote `io.kikwiflow` incluído é necessário porque `MongoKikwiEngineRepository`
usa repositórios Spring Data MongoDB internos ao motor — se o scan de componentes/repositórios do projeto gerado
não alcançar `io.kikwiflow`, o contexto falha ao subir. Isso só se aplica quando o addon de persistência é
MongoDB.

## 5. `application.yml` — configuração do motor

Todas as chaves abaixo vêm de `KikwiflowProperties` (prefixo `kikwiflow`) — o exportador deve gerar um YAML
completo com valores sensatos, não confiar apenas nos defaults:

```yaml
server:
  port: ${SERVER_PORT:8081}

spring:
  application:
    name: meu-processo-service
  threads:
    virtual:
      enabled: true            # o motor usa Executors.newVirtualThreadPerTaskExecutor() internamente
  data:
    mongodb:
      uri: ${MONGODB_URI}      # NUNCA hardcode credenciais no YAML — usar variável de ambiente/secret
      database: meu_processo_db
      auto-index-creation: true

kikwiflow:
  process-definition:
    auto-deploy:
      enabled: true                                # default: true
      path: "classpath*:processes/**/*.kikwi"        # default; onde o exportador deve colocar o .kikwi
  security:
    deploy:
      enabled: true            # habilita DefaultDeploymentSecurityManager para permitir deploy
  execution:
    task-acquisition-interval-millis: 1000          # default: 5000
    task-acquisition-max-tasks: 12                  # default: 10
    max-concurrent-tasks: 200                        # default: 200
    shutdown-grace-period-seconds: 30                # default: 20
    lock-timeout-millis: 1200                        # default: 12 (⚠ default de fábrica parece baixo demais p/ produção)
  retry:
    default-retry-interval: PT1M
    fatal-exceptions:
      - java.lang.NullPointerException
      - java.lang.IllegalArgumentException
  stats:
    enabled: false
  outbox:
    events-enabled: false      # habilita publicação de eventos PROCESS_INSTANCE_FINISHED/FLOW_NODE_FINISHED no outbox
  rest:
    process-definition:
      deploy:
        enabled: false         # habilita endpoint REST de deploy manual de processos
    cors:
      allowed-origins: "http://localhost:3000"
  pulse:
    sse-endpoints:
      enabled: true
      interval: 5000
```

## 6. Mapeamento "De → Para": nó `.kikwi` → classe/interface Java

| `type` no JSON                    | Classe de modelo (`kikwi-model`)         | Contrato Java gerado / usado                        | Observações |
|------------------------------------|-------------------------------------------|-------------------------------------------------------|-------------|
| `DEFAULT_START_EVENT`              | `StartEventDefinition`                    | — (não gera código)                                    | Ponto de entrada; `defaultStartPoint` do processo aponta para o id deste nó. |
| `DEFAULT_END_EVENT`                | `EndEventDefinition`                      | — (não gera código)                                    | Nó terminal, sem `outgoing`. |
| `EXECUTABLE_TASK`                  | `ExecutableTaskDefinition`                | `TaskHandler` (bean com nome = campo `executor`)       | Executado in-process pelo `TaskExecutor`. Suporta `retryPolicy`/`backoffStrategy`. |
| `EXTERNAL_TASK`                    | `ExternalTaskDefinition`                  | **nenhum bean automático** — worker externo via `ExternalTaskQueryService`/API REST (fetch-and-lock) | Não referencia `executor`; fica em espera (`WaitState`) até ser completado externamente. Pode ter `sla`. |
| `EXCLUSIVE_GATEWAY`                | `ExclusiveGatewayDefinition`               | `AnswerProvider` (se `providerType: BEAN`, bean = `providerBean`) | Ver seção 6.1 sobre `providerType`. |
| `PARALLEL_GATEWAY`                 | `ParallelGatewayDefinition`                | — (não gera código)                                    | Fan-out: dispara todos os `outgoing` em paralelo. |
| `JOIN_GATEWAY`                     | `JoinGatewayDefinition`                    | — (não gera código)                                    | Fan-in: aguarda todos os ramos convergentes antes de prosseguir. |
| `BOUNDARY_INTERRUPTIVE_TIMER`      | `InterruptiveTimerEventDefinition`         | `DueDateProvider` (se `providerType: BEAN`), opcionalmente ligado a `executor` | Timer de borda interruptivo, `attachedToRef` aponta para o nó pai (ex.: um `EXTERNAL_TASK`). |
| `BOUNDARY_NON_INTERRUPTIVE_TIMER`  | `NonInterruptiveTimerEventDefinition`      | idem acima, sem interromper o nó pai                    | |
| `BOUNDARY_ERROR_HANDLER`           | `ErrorHandlerDefinition`                   | — (roteamento via `errorCode`/`attachedToRef`)          | Documentado no código-fonte como "Process Error Handler flow" (ver histórico de commits). |

### 6.1. `providerType` em gateways e timers

`ExclusiveGatewayDefinition` e os timers de borda (`InterruptiveTimerEventDefinition`/
`NonInterruptiveTimerEventDefinition`) usam um enum `providerType` para decidir **de onde vem a resposta/data**:

- `BEAN` → resolve via `providerBean` (nome do bean Spring que implementa `AnswerProvider` ou `DueDateProvider`).
- `VARIABLE` → resolve lendo diretamente uma variável de processo (`providerVariable`), sem chamar código Java.
- `STATIC` (apenas timers) → usa um valor fixo ISO-8601 em `staticValue` (ex.: `"PT1M"`), sem bean nem variável.

Exemplo real (`kyc-ext-exec-gtw-async.json`):

```json
{
  "type": "EXCLUSIVE_GATEWAY",
  "providerType": "BEAN",
  "providerBean": "customerRiskStrategy",
  "outgoing": [
    { "targetNodeId": "emitir-parecer-external-task", "isDefault": true },
    { "targetNodeId": "solicitar-dados-task", "handlesNull": true },
    { "targetNodeId": "avaliar-fraude", "expectedAnswer": "FRAUDE" }
  ]
}
```

```json
{
  "type": "EXCLUSIVE_GATEWAY",
  "providerType": "VARIABLE",
  "providerVariable": "acaoResultadoAnaliseFraude",
  "outgoing": [
    { "targetNodeId": "finished", "expectedAnswer": "FINALIZAR" },
    { "targetNodeId": "CALCULATE_CUSTOMER_RISK_ST", "expectedAnswer": "RECALCULAR" }
  ]
}
```

**Regra de roteamento**: cada `outgoing` pode ter `expectedAnswer` (casa com o retorno do `AnswerProvider`/valor
da variável), `isDefault: true` (fallback, um por gateway) ou `handlesNull: true` (rota tomada quando a resposta
é `null`). O exportador deve validar que existe no máximo um `isDefault` por gateway e, idealmente, uma rota
`handlesNull` sempre que o provider puder retornar `null` (`AnswerProvider.resolve` documenta explicitamente que
retornar `null` é permitido).

### 6.2. `executor` → nome do bean `TaskHandler`

O campo `executor` de um `EXECUTABLE_TASK` é o **nome do bean Spring**, resolvido em runtime por
`SpringTaskHandlerResolver.resolve(beanName)` via `applicationContext.getBean(beanName, TaskHandler.class)`. Isso
implica duas estratégias válidas de nomeação, ambas observadas no projeto de exemplo:

1. Nome de bean **implícito** (default do `@Component`, camelCase do nome da classe): `executor:
   "enrichCustomerProfileTaskHandler"` → `@Component public class EnrichCustomerProfileTaskHandler`.
2. Nome de bean **explícito**: `executor: "sendCustomerInvite"` → `@Component("sendCustomerInvite") public class
   SendCustomerInviteTaskHandler`.

O exportador deve **sempre gerar `@Component("<valor exato do executor>")`** explicitamente — não depender da
convenção de nomeação implícita do Spring — para garantir que o bean gerado bata com o `executor` do JSON
independentemente de eventuais renomeações de classe.

O mesmo padrão vale para `providerBean` em gateways (→ `AnswerProvider`) e para `providerBean` em timers com
`providerType: BEAN` (→ `DueDateProvider`).

### 6.3. Retry / backoff (`EXECUTABLE_TASK`)

`ExecutableTaskDefinition.retryPolicy` é um `RetryPolicy` (`strategy`, `maxRetries`, `initialInterval`,
`multiplier`, `maxInterval`, `intervals`). No JSON de exemplo aparece também um atalho de string,
`"backoffStrategy": "R3/1S"` (formato ISO-8601 de intervalos repetidos: 3 repetições de 1 segundo). Se nenhuma
política é definida no nó, vale o fallback global `kikwiflow.retry.default-retry-interval`, e exceções listadas
em `kikwiflow.retry.fatal-exceptions` pulam retry e vão direto para incidente (`DefaultRetryPolicyEvaluator`).

## 7. Contratos fundamentais (`kikwi-execution-api`)

### 7.1. `TaskHandler`

```java
package io.kikwiflow.execution.api.handler;

import io.kikwiflow.execution.api.context.ExecutionContext;

public interface TaskHandler {
    void handle(ExecutionContext execution);
}
```

`ExecutionContext` (mutável, usado dentro do handler) expõe:

```java
public interface ExecutionContext {
    void setVariable(String variableName, ProcessVariable value);
    void removeVariable(String variableName);
    ProcessVariable getVariable(String variableName);
    boolean hasVariable(String variableName);
    String getProcessInstanceId();
    FlowNodeDefinition getFlowNode();
}
```

### 7.2. `AnswerProvider`

```java
package io.kikwiflow.execution.api.provider;

import io.kikwiflow.execution.api.context.EvaluationContext;

@FunctionalInterface
public interface AnswerProvider {
    String resolve(EvaluationContext context); // null é permitido; deve haver rota handlesNull no gateway
}
```

`EvaluationContext` (imutável, usado em decisões) expõe `getProcessInstanceId()`, `getVariableValue(String)` →
`Optional<Object>`, e `getVariables()` → `Map<String, Object>` somente-leitura.

### 7.3. `DueDateProvider`

```java
package io.kikwiflow.execution.api.provider;

import io.kikwiflow.execution.api.context.EvaluationContext;

public interface DueDateProvider {
    /** ISO-8601: duração ("PT1H") ou data absoluta ("2026-12-25T20:00:00Z") */
    String resolve(EvaluationContext execution);
}
```

Usado por timers de borda com `providerType: BEAN`.

## 8. Exemplos completos de stub gerado

### 8.1. `TaskHandler`

```java
package com.empresa.processo.executors;

import io.kikwiflow.execution.api.context.ExecutionContext;
import io.kikwiflow.execution.api.handler.TaskHandler;
import io.kikwiflow.model.execution.ProcessVariable;
import org.springframework.stereotype.Component;

@Component("calculateCustomerRiskTaskHandler") // nome deve bater com "executor" no .kikwi
public class CalculateCustomerRiskTaskHandler implements TaskHandler {

    @Override
    public void handle(ExecutionContext execution) {
        String taxId = execution.hasVariable("taxId")
                ? execution.getVariable("taxId").value().toString()
                : null;

        double riskScore = calcularRisco(taxId);
        execution.setVariable("riskScore", new ProcessVariable("riskScore", riskScore));
    }

    private double calcularRisco(String taxId) {
        // TODO: implementar regra de negócio
        return 50.0;
    }
}
```

### 8.2. `AnswerProvider`

```java
package com.empresa.processo.decision;

import io.kikwiflow.execution.api.context.EvaluationContext;
import io.kikwiflow.execution.api.provider.AnswerProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component("customerRiskStrategy") // nome deve bater com "providerBean" no gateway do .kikwi
public class CustomerRiskStrategy implements AnswerProvider {

    @Override
    public String resolve(EvaluationContext context) {
        return context.getVariableValue("riskScore")
                .filter(Objects::nonNull)
                .map(riskScore -> (Double) riskScore < 50 ? "FRAUDE" : "APROVADO")
                .orElse(null); // rota handlesNull deve existir no gateway correspondente
    }
}
```

## 9. Checklist do exportador

Ao converter um `.kikwi` em projeto:

1. Gerar `pom.xml` com os starters da seção 3 (MongoDB como padrão de persistência).
2. Gerar `Application.java` com `@SpringBootApplication` + `@EnableMongoRepositories(basePackages = {..., "io.kikwiflow"})`.
3. Copiar o `.kikwi` do processo para `src/main/resources/processes/` .
4. Gerar `application.yml` com todas as chaves da seção 5, nunca com credenciais em texto plano.
5. Para cada nó `EXECUTABLE_TASK`: gerar uma classe `implements TaskHandler`, anotada
   `@Component("<executor>")`.
6. Para cada `EXCLUSIVE_GATEWAY`/timer com `providerType: BEAN`: gerar uma classe `implements AnswerProvider` ou
   `implements DueDateProvider`, anotada `@Component("<providerBean>")`.
7. Para gateways com `providerType: VARIABLE` ou `STATIC`: **não** gerar bean nenhum — apenas validar que a
   variável/valor referenciado é plausível.
8. Validar que todo gateway tem no máximo um `isDefault: true`, e uma rota `handlesNull: true` sempre que o
   `AnswerProvider` gerado possa retornar `null`.
9. Não gerar código para `EXTERNAL_TASK` — esses nós são consumidos por workers externos via
   `kikwi-runtime-query-api`/API REST, fora do processo Java gerado.
