# Tutorial — Executando e validando os processos de exemplo

Este módulo sobe uma aplicação Spring Boot real com o motor Kikwiflow embutido e processos `.kikwi`
auto-deployados na inicialização (`src/main/resources/processes/*.kikwi`, path configurado por
`kikwiflow.auto-deploy.path`, default `classpath*:processes/**/*.kikwi`):

- **`onboarding-scatter-gather`** (`onboarding-scatter-gather.kikwi`) — processo novo, escrito para exercitar
  de ponta a ponta os elementos cobertos pelos testes de motor mais recentes (`kikwi-core-tests`): gateway
  paralelo com join, gateways exclusivos (provider `BEAN` e `VARIABLE`), boundary timer interruptivo (SLA),
  boundary error handler (erro de negócio), retry com backoff exponencial e linear, incidentes
  (`FAILED_JOB` vs `UNHANDLED_BUSINESS_ERROR`), e `commitAfter` forçando continuação assíncrona.
- **`kyc-emissao-parecer`** (`kyc-emissao-parecer.kikwi`) — processo pré-existente. Durante a validação deste
  tutorial encontramos e corrigimos um bug real nele: o boundary timer de `solicitar-dados-task` estava
  declarado com a chave `"boundaryEvents": [...]` embutida no próprio nó — essa chave **não existe** no
  schema real (`ExternalTaskDefinition` só reconhece `boundaryEventIds: string[]`, referenciando nós
  separados no mapa `flowNodes`). O Jackson simplesmente ignorava a chave desconhecida: o processo fazia
  deploy sem erro, mas o timer nunca era criado. Corrigido para o formato correto (nó
  `interruptive-timer-317a0ef1-...` promovido a entrada própria de `flowNodes`, referenciado via
  `boundaryEventIds`).
- **`onboarding-ativacao-produtos-sequencial`** / **`onboarding-ativacao-produtos-paralelo`**
  (`onboarding-ativacao-produtos-*.kikwi`) — par de processos novos demonstrando `CALL_ACTIVITY_COORDINATOR`
  em cada um dos dois modos de `iterationMode` (§3). Ambos chamam o mesmo processo filho,
  **`product-activation-process`** (`product-activation-process.kikwi`) — um por produto da lista `produtos`,
  cada um uma `ProcessInstance` própria e isolada.

## 1. Pré-requisitos e subida

JDK 21 e `mvn` no PATH (ver `CLAUDE.md` na raiz do repo). `application.yml` já aponta para um cluster MongoDB
Atlas de desenvolvimento compartilhado — não precisa subir Mongo localmente.

```bash
mvn -pl sample-onboarding-process -am install -DskipTests -Dgpg.skip=true   # primeira vez, para instalar os módulos no repo local
mvn -pl sample-onboarding-process spring-boot:run
```

Ao subir, o log mostra os processos deployados:

```
 ✓ Processo deployado: kyc-emissao-parecer (v2)
 ✓ Processo deployado: onboarding-scatter-gather (v1)
 ✓ Processo deployado: product-activation-process (v1)
 ✓ Processo deployado: onboarding-ativacao-produtos-sequencial (v1)
 ✓ Processo deployado: onboarding-ativacao-produtos-paralelo (v1)
 ✓ AutoDeploy concluído:  (5) processos
```

A API REST fica em `http://localhost:8081/kikwiflow/api/v1` (prefixo configurado em
`kikwiflow.rest.base-path`, default `/kikwiflow/api/v1` — **não** confundir com o `/api/v1` sem prefixo
citado solto por aí). Swagger UI: `http://localhost:8081/swagger-ui/index.html`.

**Header de tenant**: os endpoints de `IdentityContext` (tenant/actor) lêem o tenant do header
`X-Tenant-Id` (ver `KikwiflowSecurityAutoConfiguration.anonymousIdentityResolver`), não de um campo no corpo
da requisição — mande sempre esse header nas chamadas de `complete`/`retry`/`setVariables`, com o mesmo valor
usado no `tenant` do start, ou a chamada falha com `SecurityException: Tenant mismatch`.

**Como saber que um processo concluiu, via REST**: uma `ProcessInstance` concluída é **removida** da coleção
ativa, não fica com `status: COMPLETED` consultável indefinidamente (ver o comentário de
`AssertableKikwiEngine.assertThatProcessInstanceIsCompleted`, `kikwi-core-testing`: "uma instância completa é
removida da coleção ativa"). Na prática: `GET /process-instances/{id}` retorna a instância normalmente
(`status: ACTIVE`) enquanto ela roda, e passa a devolver **404** assim que ela conclui — não um corpo com
`status: COMPLETED`. Em todo este tutorial, "confira `GET .../process-instances/$IID` até ver `COMPLETED`"
deve ser lido como "até a chamada começar a devolver 404".

## 2. `onboarding-scatter-gather` — passo a passo

```
START ──▶ PARALLEL_SPLIT ──┬─▶ API_BUREAU (async) ─────────┐
                            └─▶ UI_UPLOAD (external) ───────┴─▶ JOIN_SYNC ──▶ CALC_RISK ──▶ DECISION_RISK
                                                                                                 │
                                                    ┌────────────────────────────────────────────┤
                                             APPROVED│                                    HIGH_RISK / default
                                                     ▼                                            ▼
                                            CREATE_ACCOUNT (async, retry)             MANUAL_DESK (external, SLA=2min)
                                          [erro doc → END_EVENT_REJECTED]                         │
                                                     │                              APPROVED│  │default/timeout
                                                     ▼                                      ▼  ▼
                                            DISPARAR_PRODUTOS ──▶ AGUARDAR_PRODUTOS   CREATE_ACCOUNT   END_EVENT_REJECTED
                                                              [timeout=3min]                (mesmo nó acima)
                                                                    │
                                                          ┌─────────┴─────────┐
                                                     completo             timeout
                                                          ▼                    ▼
                                                 END_EVENT_SUCCESS   END_EVENT_PARTIAL_SUCCESS
```

O roteamento inteiro é dirigido por um único variável de entrada, `taxId`, lida pelos handlers em
`sample-onboarding-process/.../process/executors/`:

| taxId | O que acontece | Elemento demonstrado |
|---|---|---|
| `1` | aprovado direto (`bureauScore=90`) | happy path completo |
| `2` | vai para `MANUAL_DESK` (`bureauScore=15`) | gateway `HIGH_RISK` + external task humana |
| `6` | `CREATE_ACCOUNT` lança erro de negócio `DOCUMENTO_INVALIDO` | `BOUNDARY_ERROR_HANDLER` capturando e desviando **sem** consumir retries |
| `7` | `API_BUREAU` lança `RuntimeException` (falha técnica) | retry `EXPONENTIAL_BACKOFF` → incidente `FAILED_JOB` |
| `8` | `CREATE_ACCOUNT` falha uma vez e sucede na retentativa | retry `LINEAR` se autocurando |
| `9` | `API_BUREAU` lança erro de negócio sem handler que capture | incidente `UNHANDLED_BUSINESS_ERROR` imediato, **sem** gastar orçamento de retry |
| outro | `bureauScore=55` → aprovado (borda) | fallback default |

### 2.1 Happy path (taxId=1)

```bash
curl -s -X POST http://localhost:8081/kikwiflow/api/v1/process-instances \
  -H "Content-Type: application/json" \
  -d '{
    "processDefinitionKey": "onboarding-scatter-gather",
    "businessKey": "BK-DEMO-1",
    "tenant": "demo",
    "variables": { "taxId": {"name":"taxId","isTransient":false,"value":"1"} }
  }'
```

Guarde o `id` da resposta (`$IID`). O acquirer roda a cada 1s (`kikwiflow.execution.task-acquisition-interval-millis`),
então em ~1-2s a ramificação `API_BUREAU` já roda sozinha. Confira o estado e a external task da outra
ramificação:

```bash
curl -s "http://localhost:8081/kikwiflow/api/v1/process-instances/$IID"
curl -s "http://localhost:8081/kikwiflow/api/v1/external-tasks?process-instance-id=$IID"
```

Complete `UI_UPLOAD` (pegue o `id` da task retornada acima):

```bash
curl -s -X POST "http://localhost:8081/kikwiflow/api/v1/external-tasks/$TASK_ID/complete" \
  -H "Content-Type: application/json" -H "X-Tenant-Id: demo" -d '{"variables": {}}'
```

O join libera, `CALC_RISK`/`DECISION_RISK` rodam inline, `CREATE_ACCOUNT` fica `PENDING` (é `commitBefore:
true`) e é pego pelo acquirer em ~1s, `DISPARAR_PRODUTOS` roda e por ter `commitAfter: true` no nó anterior
força `AGUARDAR_PRODUTOS` a nascer como `PENDING` em vez de rodar inline no mesmo ciclo. Complete-a do mesmo
jeito que `UI_UPLOAD` (mesmo endpoint, outro `id`) para chegar em `END_EVENT_SUCCESS` — `GET
.../process-instances/$IID` passa a devolver 404 (ver "Como saber que um processo concluiu", §1).

### 2.2 Erro de negócio capturado pelo boundary handler (taxId=6)

Repita o start com `taxId=6` e complete `UI_UPLOAD`. `CREATE_ACCOUNT` lança
`ProcessErrorException("DOCUMENTO_INVALIDO")`; o `BOUNDARY_ERROR_HANDLER` `ERROR_DOCUMENTO_INVALIDO` casa o
`errorCode` e desvia **sincronamente** para `END_EVENT_REJECTED` — o processo termina
(`COMPLETED`, na prática "rejeitado") sem nenhum incidente e sem gastar o orçamento de `RetryPolicy` do nó.

### 2.3 Incidente técnico com retry (taxId=7) vs. incidente de negócio sem retry (taxId=9)

Ambos os taxIds falham dentro de `API_BUREAU` — **dentro** da ramificação paralela. Isso é proposital: veja a
seção 2.5 sobre por que um `BOUNDARY_ERROR_HANDLER` não é usado em `API_BUREAU`.

```bash
# taxId=7: RuntimeException comum -> reagenda com EXPONENTIAL_BACKOFF (PT5S, PT10S) -> após esgotar, incidente FAILED_JOB
# taxId=9: ProcessErrorException sem handler que capture -> incidente UNHANDLED_BUSINESS_ERROR imediato (pula o retry)
curl -s "http://localhost:8081/kikwiflow/api/v1/process-instances/$IID/incidents"
```

Para os dois casos, corrija a causa e retome exatamente como um operador faria:

```bash
curl -s -X PUT "http://localhost:8081/kikwiflow/api/v1/process-instances/$IID/variables" \
  -H "Content-Type: application/json" -H "X-Tenant-Id: demo" \
  -d '{"variables": {"taxId": {"name":"taxId","isTransient":false,"value":"1"}}}'

curl -s -X PUT "http://localhost:8081/kikwiflow/api/v1/incidents/$INCIDENT_ID/retry" -H "X-Tenant-Id: demo"
```

`API_BUREAU` roda de novo com sucesso e a ramificação segue para `JOIN_SYNC` normalmente.

### 2.4 Retry que se autocura (taxId=8)

`CREATE_ACCOUNT` falha na primeira execução e sucede sozinho na segunda (o handler rastreia a tentativa em
memória, por `processInstanceId` — ver Javadoc de `CriarContaCoreBankingTaskHandler` para o porquê de não usar
uma variável de processo para isso). Nenhuma ação manual é necessária: em ~5s o acquirer tenta de novo e o
processo segue sozinho até `END_EVENT_SUCCESS`.

### 2.5 Um desvio de erro dentro de um branch paralelo nunca libera o join sozinho

Ao validar este tutorial rodando a aplicação de verdade, a primeira versão de `onboarding-scatter-gather.kikwi`
anexava um `BOUNDARY_ERROR_HANDLER` a `API_BUREAU` desviando para um `END_EVENT_REJECTED` dedicado. O deploy
era válido e o processo rodava — mas a ramificação `API_BUREAU`, ao desviar para um nó que **não é** o
`JOIN_GATEWAY` de destino (`targetJoinId`/`sourceSplitId`), nunca é contabilizada como concluída para aquele
join. A outra ramificação (`UI_UPLOAD`) conclui normalmente, mas `JOIN_SYNC` fica esperando para sempre uma
ramificação que já "terminou" em outro lugar — a instância trava, ativa, sem incidente e sem qualquer sinal de
erro. Isso não apareceu em nenhum teste existente porque nenhum teste de motor combina
`PARALLEL_GATEWAY`/`JOIN_GATEWAY` com um `BOUNDARY_ERROR_HANDLER` numa das ramificações.

A correção aplicada aqui foi de **modelagem**, não do motor: `API_BUREAU` não tem nenhum
`BOUNDARY_ERROR_HANDLER`. Uma falha nele (técnica ou de negócio) vira incidente normalmente — a ramificação só
é considerada concluída quando o próprio nó reexecuta com sucesso e segue pelo seu `outgoing` normal até
`JOIN_SYNC` (seção 2.3). Quem precisar de um desvio de erro que reescreve o fluxo (não apenas retry) deve
colocá-lo **fora** de qualquer ramificação paralela, como `ERROR_DOCUMENTO_INVALIDO` em `CREATE_ACCOUNT`
(seção 2.2, já depois do `JOIN_SYNC`).

Se seu processo realmente precisa que uma ramificação paralela termine cedo em caso de erro, esta seria uma
lacuna real de cobertura do motor para investigar depois (não coberta por `ParallelGatewayJoinTest` nem por
`BoundaryErrorHandlerTest` hoje) — não uma limitação documentada/intencional.

### 2.6 SLA por timer (taxId=2, sem completar `MANUAL_DESK`)

Rode o cenário do taxId=2 (seção da tabela acima) e **não** complete a external task `MANUAL_DESK`. Depois de
2 minutos (`TIMER_SLA_TIMEOUT`, `PT2M` — encurtado de propósito para este tutorial; em produção seria algo como
`PT24H`), o acquirer dispara o timer sozinho e o processo termina em `END_EVENT_REJECTED` — sem nenhuma ação
manual seguinte. O mesmo padrão existe em `AGUARDAR_PRODUTOS`/`TIMER_PRODUTOS_TIMEOUT` (`PT3M`), levando a
`END_EVENT_PARTIAL_SUCCESS`.

## 3. `CALL_ACTIVITY_COORDINATOR` — Ativação de Produtos (Sequencial vs Paralelo)

Dois processos-pai (`onboarding-ativacao-produtos-sequencial.kikwi` / `onboarding-ativacao-produtos-paralelo.kikwi`)
chamam o **mesmo** processo filho, `product-activation-process.kikwi`, um por elemento da variável `produtos`
— só o campo `iterationMode` do nó `CALL_ACTIVITY_COORDINATOR` (`CALL_ATIVAR_PRODUTOS`) muda entre os dois:

```
                                          CALL_ATIVAR_PRODUTOS
START ──▶ (CALL_ACTIVITY_COORDINATOR, calledElement=product-activation-process,
           collectionVariable=produtos, elementVariable=produto,
           iterationMode=SEQUENTIAL|PARALLEL) ──▶ AFTER_ATIVACAO ──▶ END_EVENT_SUCCESS
                    │
                    │ boundary timeout (PT2M)
                    ▼
              END_EVENT_TIMEOUT

Por produto (product-activation-process.kikwi), uma ProcessInstance própria:
START ──▶ CRIAR_PRODUTO (commitBefore, retry LINEAR) ──▶ NOTIFICAR_CLIENTE ──▶ END_EVENT
```

- **`onboarding-ativacao-produtos-sequencial`**: só a iniciadora do produto 0 é criada no fan-out inicial; a
  próxima só nasce depois que a `ProcessInstance` filha anterior **conclui**. Narrativa: produtos com
  dependência regulatória de ordem (ex.: a conta-corrente precisa existir antes do cartão vinculado a ela ser
  emitido).
- **`onboarding-ativacao-produtos-paralelo`**: as N iniciadoras nascem todas juntas, no mesmo commit do
  fan-out — os N filhos disparam ao mesmo tempo. Narrativa: ativações independentes entre si, onde throughput
  importa mais que ordem.

A variável `produtos` é uma lista de nomes; o terceiro item, `"seguro-vida"`, é tratado de propósito por
`CriarProdutoTaskHandler` como uma instabilidade transiente — falha na primeira tentativa e sucede sozinho na
segunda (`retryPolicy` LINEAR, `PT5S`, do nó `CRIAR_PRODUTO` em `product-activation-process.kikwi`). Em modo
sequencial isso também demonstra que uma falha travando um elemento **atrasa** o próximo (só avança depois que
o anterior conclui, mesmo com retry no meio) — em paralelo, os outros dois produtos seguem intactos enquanto
`seguro-vida` retenta.

**Preferir automatizado a manual?** `ProductActivationCallActivityTest`
(`src/test/java/io/kikwiflow/sample/onboarding/process/`) roda os dois cenários abaixo de ponta a ponta contra
o Mongo real configurado em `application.yml` — mesmo contexto Spring Boot completo, sem HTTP (chama
`KikwiflowEngine` diretamente em Java). É lento (~1-2min, depende do acquirer real) mas cobre exatamente o que
§3.1/§3.2 pedem pra fazer manualmente, incluindo a autocura do retry de `seguro-vida`:

```bash
mvn -pl sample-onboarding-process test -Dtest=ProductActivationCallActivityTest
```

### 3.1 Rodando o cenário sequencial

```bash
curl -s -X POST http://localhost:8081/kikwiflow/api/v1/process-instances \
  -H "Content-Type: application/json" \
  -d '{
    "processDefinitionKey": "onboarding-ativacao-produtos-sequencial",
    "businessKey": "BK-PROD-SEQ-1",
    "tenant": "demo",
    "variables": {
      "produtos": {"name":"produtos","isTransient":false,"value":["conta-corrente","cartao-credito","seguro-vida"]}
    }
  }'
```

Guarde o `id` da resposta (`$IID`, o **pai**). Acompanhe o estado dele:

```bash
curl -s "http://localhost:8081/kikwiflow/api/v1/process-instances/$IID"
```

Cada filho tem `businessKey` determinístico — `<businessKey do pai>#<índice>` (ver
`KikwiflowEngine.executeCallActivityStarter`, `kikwi-core`) — então dá pra localizá-los sem precisar de um
índice pai→filhos (que ainda não existe, ver nota abaixo):

```bash
curl -s -X POST http://localhost:8081/kikwiflow/api/v1/process-instances/search \
  -H "Content-Type: application/json" \
  -d '{"businessKeys": ["BK-PROD-SEQ-1#0", "BK-PROD-SEQ-1#1", "BK-PROD-SEQ-1#2"]}'
```

Rodando essa busca repetidamente (a cada ~2-3s) em modo **sequencial**, você deve ver **no máximo um** filho
`ACTIVE` por vez — o segundo só aparece na busca depois que o primeiro já concluiu e sumiu da coleção ativa
(seção "Como saber que um processo concluiu", §1). Em ~15-20s (3 produtos, incluindo o retry de
`seguro-vida`) `GET .../process-instances/$IID` passa a devolver 404 — o pai concluiu.

### 3.2 Rodando o cenário paralelo

Mesmo corpo, trocando só `processDefinitionKey` e `businessKey`:

```bash
curl -s -X POST http://localhost:8081/kikwiflow/api/v1/process-instances \
  -H "Content-Type: application/json" \
  -d '{
    "processDefinitionKey": "onboarding-ativacao-produtos-paralelo",
    "businessKey": "BK-PROD-PAR-1",
    "tenant": "demo",
    "variables": {
      "produtos": {"name":"produtos","isTransient":false,"value":["conta-corrente","cartao-credito","seguro-vida"]}
    }
  }'
```

Repita a busca por `businessKeys` (agora `BK-PROD-PAR-1#0/#1/#2`) logo após o start: os **três** filhos já
aparecem `ACTIVE` juntos — contraste direto com o §3.1. O pai conclui mais rápido (`GET` passa a 404 em
~5-10s), já que `seguro-vida` retenta em paralelo às outras duas ramificações, não em série.

### 3.3 Timeout no meio da ativação

Ambos os coordenadores têm um boundary timer (`TIMER_ATIVACAO_TIMEOUT`, `PT2M`, encurtado para o tutorial).
Não há como forçar isso via REST (nenhum produto do cenário padrão trava indefinidamente) — para observar,
adicione um produto inexistente à lista (`calledElement` não resolve → incidente isolado na iniciadora, sem
travar o timer) ou aumente `PT2M` para um valor bem curto (`PT10S`) direto no `.kikwi` antes de subir a
aplicação, e não complete nenhum produto a tempo. O timeout apaga a coordenadora + qualquer iniciadora ainda
pendente; filhos já iniciados continuam rodando sozinhos (mesmo comportamento validado em
`CallActivityCoordinatorTest$SequentialIteration#boundaryTimeoutMidSequenceDiscardsRemainingElements`,
`kikwi-core-tests`).

## 4. O que não dá pra validar por aqui ainda

- **`EVENT_CATCHER`/`EVENT_THROWER` (correlação de mensagens)** — cobertos a nível de motor
  (`EventCatcherStandaloneTest`, `EventCatcherGroupTest`, `EventThrowerTest` em `kikwi-core-tests`) e **já têm**
  endpoint REST (`POST /events/correlate/{correlationKey}`, `kikwi-management-rest`), mas nenhum dos dois
  processos deste módulo usa esses nós hoje — nenhum `.kikwi` aqui declara `EVENT_CATCHER`/`EVENT_THROWER`
  para demonstrar a correlação de ponta a ponta via HTTP. Ficou fora do escopo deste tutorial.
- **Índice pai→filhos de `CALL_ACTIVITY_COORDINATOR`** — não existe um `GET` que liste "quais são as
  instâncias filhas desta instância" (`findProcessInstancesByParentInstanceId` ainda não existe —
  `parentInstanceId` já é persistido em cada filho e aparece nos eventos de outbox, mas não é consultável via
  `ProcessInstanceQuery` hoje). §3 acima contorna isso calculando os `businessKey`s dos filhos manualmente
  (determinísticos, `<businessKey do pai>#<índice>`) em vez de descobri-los via API.

## 5. Aviso de segurança

`application.yml` deste módulo tem a connection string do MongoDB Atlas (usuário/senha) em texto plano,
versionada. Isso é uma exposição de credencial real — vale rotacionar a senha e mover para uma variável de
ambiente/secret manager antes de deixar este arquivo público, independente deste tutorial.
