# Instruções: gerar um `.kikwi` de documentação a partir de um projeto Java

## Objetivo

Você vai ler um projeto Java existente e produzir **um único arquivo `.kikwi`** (JSON) que documenta, no
formato de processo do Kikwiflow, o fluxo de negócio que esse código implementa — suas etapas, decisões,
chamadas externas, tratamento de erro e esperas.

**Regra de ouro: isto é documentação, não implantação.** O arquivo resultante nunca vai ser deployado num
motor Kikwiflow real. Ele existe para que um humano (ou outro agente) consiga visualizar/entender o fluxo de
negócio olhando um grafo estruturado, em vez de reconstruí-lo lendo classe por classe. Por causa disso:

- **Não crie** classes `TaskHandler`, beans Spring, nem qualquer código novo no projeto.
- **Não tente** rodar, compilar contra, ou validar o arquivo contra um motor Kikwiflow real.
- Os valores de `executor`/`providerBean` no `.kikwi` **não precisam corresponder a um bean Spring que
  realmente existe** — eles são nomes descritivos que apontam para a classe/método Java de origem (ver §4).
- Se o projeto não usa Spring, ou não tem "beans" no sentido usado pelo Kikwiflow, tudo bem — trate
  `executor`/`providerBean` como um identificador textual livre, não como uma exigência de que aquele bean
  exista de fato.

O arquivo final precisa ser **estruturalmente válido** (JSON bem formado, todas as referências entre nós
resolvem, regras de gateway respeitadas — ver checklist na §5) mesmo sem nunca ser executado, porque é isso
que permite abrir o arquivo numa ferramenta de modelagem/visualização do Kikwiflow sem erro.

---

## Passo 1 — Ler o projeto e identificar o processo

Antes de escrever qualquer JSON, monte mentalmente (ou em rascunho) a lista de etapas do fluxo de negócio.
Procure por:

| O que procurar no código | Vira, no `.kikwi`... |
|---|---|
| Ponto de entrada (controller REST, listener de fila, `main`, job agendado) | O nó `DEFAULT_START_EVENT` |
| Uma sequência de chamadas de método/serviço, uma depois da outra | Uma cadeia de `EXECUTABLE_TASK` |
| `if`/`else`, `switch`, Strategy Pattern, regra de negócio que escolhe um caminho | `EXCLUSIVE_GATEWAY` |
| Chamada a um sistema externo que não retorna na hora (fila assíncrona, webhook esperado, aprovação humana, callback) | `EXTERNAL_TASK` |
| Disparo de um evento correlacionado para fora (publicar mensagem que outro sistema vai reagir, sem esperar resposta) | `EVENT_THROWER` |
| Espera por uma mensagem/callback identificado por uma chave de negócio (ex.: webhook de pagamento aprovado) | `EVENT_CATCHER` |
| `try/catch` de uma exceção de negócio conhecida (validação falhou, regra rejeitou o caso) — **não** bug/infra | `BOUNDARY_ERROR_HANDLER` |
| Timeout/SLA/deadline explícito no código (ex.: cancelar se não responder em X minutos) | `BOUNDARY_INTERRUPTIVE_TIMER` (cancela a espera) ou `BOUNDARY_NON_INTERRUPTIVE_TIMER` (só notifica, não cancela) |
| Uma espera por prazo que é o próprio próximo passo do fluxo (não anexada a outra tarefa) — ex.: "aguardar 24h antes do lembrete seguinte" | `TIMER_TASK` |
| Execução em paralelo de N tarefas independentes, todas precisando terminar antes de seguir | `PARALLEL_GATEWAY` (abre os ramos) + `JOIN_GATEWAY` (sincroniza) |
| Chamada a outro módulo/serviço que é, ele mesmo, um processo de negócio completo (ou processamento em lote sobre uma lista de itens) | `CALL_ACTIVITY_COORDINATOR` |
| `return`, fim de um caminho, exceção não tratada que encerra o fluxo | `DEFAULT_END_EVENT` |

Não modele nível de código (cada `if` trivial de validação de nulo não precisa virar gateway) — modele nível de
**processo de negócio**: os passos que alguém de negócio reconheceria como "etapas" se você narrasse o fluxo em
voz alta.

---

## Passo 2 — Estrutura do arquivo

Um `.kikwi` é um único objeto JSON com estes campos de topo:

```json
{
  "key": "identificador-estavel-do-processo",
  "name": "Nome Legível do Processo",
  "description": "O que este processo representa, em 1-2 frases.",
  "extensionProperties": {},
  "sla": "",
  "flowNodes": { "...": "..." },
  "defaultStartPoint": "ID_DO_NO_INICIAL"
}
```

- `key`: `kebab-case`, curto, estável (ex.: `processamento-pedido`).
- `flowNodes`: mapa de `id do nó → objeto do nó`. **A chave do mapa e o campo `"id"` dentro do objeto do nó
  precisam ser idênticos.** Isso não é só estilo — o motor real usa a chave do mapa para resolver referências
  e o campo `id` interno para outras coisas; divergência entre os dois é uma classe inteira de bug silencioso
  (mesmo com o arquivo nunca sendo executado, mantenha os dois iguais para que o arquivo sirva como referência
  correta).
- `defaultStartPoint`: o `id` do nó `DEFAULT_START_EVENT`. Obrigatório.

Cada nó, independente do tipo, carrega estes campos comuns:

```json
{
  "id": "MESMO_VALOR_DA_CHAVE_NO_MAPA",
  "name": "Nome curto e legível",
  "description": "O que este passo faz, em linguagem de negócio.",
  "type": "TIPO_DO_NO",
  "commitBefore": false,
  "commitAfter": false,
  "outgoing": [ /* lista de conexões, ver abaixo */ ],
  "layout": { "x": 0, "y": 0 },
  "extensionProperties": {}
}
```

- `commitBefore`/`commitAfter`: em documentação, use como sinalização semântica — `true` num
  `EXECUTABLE_TASK` sugere "isto é uma operação que pode demorar/falhar/faz I/O externo" (reflita o que o
  código real faz, ex.: uma chamada HTTP ganha `commitBefore: true`; uma transformação de dados em memória
  fica `false`). Não afeta nada por não haver execução real, mas documenta a intenção.
- `layout`: coordenadas para visualização. Incremente `x` em ~250 a cada nó adiante no fluxo; use `y`
  diferente para ramos paralelos/alternativos, para que o grafo fique legível se aberto num editor visual.

Cada conexão de saída (`outgoing`) é:

```json
{
  "id": "flow-<uuid-ou-slug-descritivo>",
  "name": "",
  "targetNodeId": "ID_DO_PROXIMO_NO",
  "transitionType": "automated",
  "isDefault": false,
  "expectedAnswer": null,
  "handlesNull": false,
  "extensionProperties": {}
}
```

- `targetNodeId` **precisa** existir como chave em `flowNodes`.
- `expectedAnswer`/`isDefault`/`handlesNull` só têm efeito quando o nó de origem é `EXCLUSIVE_GATEWAY` (ver
  §3). Em qualquer outro tipo de nó, deixe `isDefault: false`, sem `expectedAnswer`/`handlesNull`, e declare
  **no máximo uma** entrada em `outgoing` (nós comuns só têm uma saída lógica).

---

## Passo 3 — Catálogo completo de tipos de nó

Use exatamente estes 16 valores no campo `"type"` — não invente tipos novos.

| `type` | Quando usar | Campos extras relevantes |
|---|---|---|
| `DEFAULT_START_EVENT` | Um por processo, aponta pra ele via `defaultStartPoint` | — |
| `DEFAULT_END_EVENT` | Todo caminho terminal. `outgoing` sempre vazio | — |
| `EXECUTABLE_TASK` | Lógica síncrona in-process (cálculo, validação, transformação, chamada rápida) | `executor` (nome descritivo, ver §4) |
| `EXTERNAL_TASK` | Espera algo fora do controle direto do fluxo (humano, worker externo, fila) | — (sem `executor`) |
| `EXCLUSIVE_GATEWAY` | Decisão — segue **um** caminho entre vários | `providerType` (`BEAN`/`VARIABLE`) + `providerBean` ou `providerVariable` |
| `PARALLEL_GATEWAY` | Abre múltiplos ramos simultâneos | `targetJoinId` (aponta para o `JOIN_GATEWAY` correspondente) |
| `JOIN_GATEWAY` | Fecha os ramos abertos pelo `PARALLEL_GATEWAY` correspondente | `sourceSplitId` (informativo) |
| `BOUNDARY_INTERRUPTIVE_TIMER` | Prazo que, ao vencer, cancela a espera do nó pai e desvia o fluxo | `attachedToRef`, `providerType` (`STATIC`/`VARIABLE`/`BEAN`) + `staticValue`/`providerVariable`/`providerBean` |
| `BOUNDARY_NON_INTERRUPTIVE_TIMER` | Prazo recorrente que só notifica, sem cancelar o nó pai | `attachedToRef`, `schedulePolicy` |
| `BOUNDARY_ERROR_HANDLER` | Captura um erro de negócio esperado do nó pai | `attachedToRef`, `errorCode` (opcional — sem ele, é curinga) |
| `BOUNDARY_INTERRUPTIVE_CATCH_EVENT` | Cancelamento do nó pai por uma correlação externa (não por prazo) | `attachedToRef`, mesmos campos de correlação do `EVENT_CATCHER` |
| `TIMER_TASK` | Espera por prazo como próprio passo do fluxo principal (não anexado a outro nó) | `providerType` + `staticValue`/`providerVariable`/`providerBean` |
| `EVENT_CATCHER` | Espera reativa por uma chave de correlação externa (webhook, callback) | `catchType` (`STANDALONE`/`GROUP`), `providerType`, `matchPolicy` (só em `GROUP`) |
| `EVENT_THROWER` | Dispara/publica uma chave de correlação para fora (contraparte de emissão do `EVENT_CATCHER`) | mesmos campos de correlação do `EVENT_CATCHER`, sem `catchType`/`matchPolicy` |
| `CALL_ACTIVITY_COORDINATOR` | Chama outro processo/módulo como uma unidade de negócio própria; ou processa uma lista em lote | `calledElement`, `collectionVariable`/`elementVariable` (opcionais, só para lote) |

### Onde cada evento de borda (`boundaryEventIds`) pode ser anexado

Nós que aceitam eventos de borda declaram `"boundaryEventIds": ["ID_DO_EVENTO_1", "ID_DO_EVENTO_2"]`, e cada
evento de borda referencia de volta via `"attachedToRef": "ID_DO_NO_PAI"`. Nem toda combinação é válida:

| Nó pai | Timer interruptivo | Timer não-interruptivo | Error handler | Catch event de correlação |
|---|---|---|---|---|
| `EXECUTABLE_TASK` | ❌ Não permitido | ✅ | ✅ | ❌ Não permitido |
| `EXTERNAL_TASK` | ✅ | ✅ | ❌ | ✅ |
| `TIMER_TASK` | ✅ | ✅ | ❌ | ✅ |
| `EVENT_CATCHER` | ✅ | ✅ | ❌ | ❌ |
| `CALL_ACTIVITY_COORDINATOR` | ✅ (só timers) | ✅ (só timers) | ❌ | ❌ |

Motivo prático (documente isso se o processo real tiver essa forma): um `EXECUTABLE_TASK` roda o handler de
forma síncrona — não há como "cancelar de fora" no meio de uma chamada Java sem risco de um efeito colateral
já ter acontecido. Erro de negócio nele é sempre `try/catch` (`BOUNDARY_ERROR_HANDLER`), nunca uma interrupção
assíncrona.

### `EXCLUSIVE_GATEWAY`: regras de roteamento

O roteamento de um gateway de decisão segue esta ordem de prioridade — reflita fielmente a lógica real do
código Java ao decidir `expectedAnswer`/`isDefault`/`handlesNull` de cada aresta:

1. Se a decisão resolvida for `null` → segue a aresta com `handlesNull: true` (no máximo uma por gateway).
2. Senão → segue a primeira aresta cujo `expectedAnswer` bate exatamente com a decisão (comparação de string).
3. Se nenhuma bater → segue a aresta com `isDefault: true` (no máximo uma por gateway).

---

## Passo 4 — Rastreabilidade: ligando cada nó ao código-fonte real

Esta é a parte que dá valor de documentação ao arquivo. Para **cada nó** que representa um passo real de
código (`EXECUTABLE_TASK`, `EXCLUSIVE_GATEWAY`, `EXTERNAL_TASK`, etc.), preencha `extensionProperties` com a
origem:

```json
"extensionProperties": {
  "source.class": "com.empresa.pedidos.OrderService",
  "source.method": "processOrder(Order pedido)",
  "source.file": "src/main/java/com/empresa/pedidos/OrderService.java:42"
}
```

- Para `EXECUTABLE_TASK`, dê ao campo `executor` um nome descritivo derivado do método real (ex.: método
  `calcularFrete` na classe `FreteService` → `"executor": "freteServiceCalcularFrete"`), e use
  `extensionProperties` para o caminho exato. **Não afirme que esse nome corresponde a um bean Spring
  registrado** — ele é só um rótulo legível.
- Para `EXCLUSIVE_GATEWAY`, aponte `providerBean`/`providerVariable` para a condição real (ex.: o campo/método
  que a lógica de negócio testa), e explique em `description` a regra de decisão em português claro.
- Para `EXTERNAL_TASK`/`EVENT_CATCHER`/`EVENT_THROWER`, aponte para a integração real (fila, endpoint, tópico)
  em vez de uma classe — ex.: `"source.integration": "fila SQS pedido-pago"`.
- Se um nó não mapeia para um trecho específico de código (ex.: um `DEFAULT_START_EVENT` que representa "a
  requisição HTTP chega"), descreva isso em `description` mesmo sem `extensionProperties` de código.

---

## Passo 5 — Checklist de validade estrutural antes de entregar

Confira cada item antes de considerar o arquivo pronto — um `.kikwi` que viola qualquer um destes é
estruturalmente inválido, mesmo que nunca seja executado:

- [ ] JSON sintaticamente válido (sem vírgula sobrando, aspas corretas, UTF-8).
- [ ] `defaultStartPoint` aponta para um nó que existe em `flowNodes` e é do tipo `DEFAULT_START_EVENT`.
- [ ] A chave de cada entrada em `flowNodes` é **idêntica** ao campo `"id"` dentro do próprio objeto do nó.
- [ ] Todo `targetNodeId` em qualquer `outgoing` aponta para uma chave que existe em `flowNodes`.
- [ ] Todo `DEFAULT_END_EVENT` tem `outgoing` vazio.
- [ ] Todo nó que não seja `EXCLUSIVE_GATEWAY`/`PARALLEL_GATEWAY` tem **no máximo uma** entrada em `outgoing`.
- [ ] Todo `EXCLUSIVE_GATEWAY` declara `providerType` (`BEAN` ou `VARIABLE`) e tem pelo menos uma saída.
- [ ] Nenhum `EXCLUSIVE_GATEWAY` tem mais de uma aresta `isDefault: true`, nem mais de uma `handlesNull: true`.
- [ ] Duas arestas de saída do mesmo `EXCLUSIVE_GATEWAY` nunca repetem o mesmo `expectedAnswer`.
- [ ] Todo `PARALLEL_GATEWAY` declara `targetJoinId` apontando para um nó que existe **e** é do tipo
  `JOIN_GATEWAY`.
- [ ] Todo nó referenciado em `boundaryEventIds` existe em `flowNodes`, e o tipo dele é compatível com o nó
  pai (ver tabela da §3).
- [ ] Todo evento de borda tem `attachedToRef` apontando de volta para o `id` correto do nó pai.
- [ ] `CALL_ACTIVITY_COORDINATOR` tem `calledElement` preenchido; se `elementVariable` estiver presente,
  `collectionVariable` também está.
- [ ] Todo nó alcançável a partir de `defaultStartPoint` (sem nós "soltos" no `flowNodes` que nada aponta) —
  se um passo do código não é alcançado por nenhum caminho, não o inclua, ou documente por quê num
  comentário fora do JSON (ex.: no `description` do processo).

---

## Passo 6 — O que entregar

1. Um arquivo `<nome-do-processo>.kikwi`, JSON válido, seguindo tudo acima.
2. Um parágrafo curto (fora do JSON, no chat/PR/onde for entregue) resumindo: quantos nós, que tipos foram
   usados, e qualquer simplificação que você fez (ex.: "3 validações triviais de nulo no código foram
   fundidas num único `EXECUTABLE_TASK` 'Validar Pedido' para não poluir o diagrama").
3. Se alguma parte do fluxo real não tiver um tipo de nó correspondente óbvio no catálogo da §3, **não force um
   encaixe errado** — descreva a lacuna no parágrafo de resumo em vez de modelar algo enganoso.

---

## Exemplo completo (referência)

Processo fictício "Processar Pedido": valida pedido, decide entre pagamento à vista ou parcelado, chama um
gateway de pagamento externo (assíncrono), trata falha de pagamento recusado como erro de negócio, e finaliza.

```json
{
  "key": "processar-pedido",
  "name": "Processar Pedido",
  "description": "Documenta o fluxo implementado em com.empresa.pedidos.OrderController/OrderService.",
  "extensionProperties": {},
  "sla": "",
  "defaultStartPoint": "START",
  "flowNodes": {
    "START": {
      "id": "START",
      "name": "Pedido Recebido",
      "description": "Requisição HTTP POST /pedidos chega no OrderController.",
      "type": "DEFAULT_START_EVENT",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [
        { "id": "flow-1", "name": "", "targetNodeId": "VALIDAR_PEDIDO", "transitionType": "automated", "isDefault": false, "extensionProperties": {} }
      ],
      "layout": { "x": 0, "y": 0 },
      "extensionProperties": {}
    },
    "VALIDAR_PEDIDO": {
      "id": "VALIDAR_PEDIDO",
      "name": "Validar Pedido",
      "description": "Valida itens, estoque e dados do cliente.",
      "type": "EXECUTABLE_TASK",
      "executor": "orderServiceValidarPedido",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [
        { "id": "flow-2", "name": "", "targetNodeId": "DECIDIR_FORMA_PAGAMENTO", "transitionType": "automated", "isDefault": false, "extensionProperties": {} }
      ],
      "layout": { "x": 260, "y": 0 },
      "extensionProperties": {
        "source.class": "com.empresa.pedidos.OrderService",
        "source.method": "validarPedido(Pedido pedido)",
        "source.file": "src/main/java/com/empresa/pedidos/OrderService.java:30"
      }
    },
    "DECIDIR_FORMA_PAGAMENTO": {
      "id": "DECIDIR_FORMA_PAGAMENTO",
      "name": "Forma de Pagamento?",
      "description": "Decide entre à vista (chamada síncrona ao gateway) e parcelado (fluxo assíncrono de aprovação).",
      "type": "EXCLUSIVE_GATEWAY",
      "providerType": "VARIABLE",
      "providerVariable": "formaPagamento",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [
        { "id": "flow-3", "name": "", "targetNodeId": "COBRAR_GATEWAY_PAGAMENTO", "transitionType": "automated", "expectedAnswer": "A_VISTA", "isDefault": false, "extensionProperties": {} },
        { "id": "flow-4", "name": "", "targetNodeId": "COBRAR_GATEWAY_PAGAMENTO", "transitionType": "automated", "isDefault": true, "extensionProperties": {} }
      ],
      "layout": { "x": 520, "y": 0 },
      "extensionProperties": {
        "source.class": "com.empresa.pedidos.OrderService",
        "source.method": "if (pedido.getFormaPagamento() == FormaPagamento.A_VISTA) { ... }"
      }
    },
    "COBRAR_GATEWAY_PAGAMENTO": {
      "id": "COBRAR_GATEWAY_PAGAMENTO",
      "name": "Aguardar Confirmação do Gateway de Pagamento",
      "description": "Envia cobrança ao gateway externo e aguarda callback assíncrono de confirmação.",
      "type": "EXTERNAL_TASK",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [
        { "id": "flow-5", "name": "", "targetNodeId": "PEDIDO_CONFIRMADO", "transitionType": "automated", "isDefault": false, "extensionProperties": {} }
      ],
      "boundaryEventIds": ["ERRO_PAGAMENTO_RECUSADO"],
      "layout": { "x": 780, "y": 0 },
      "extensionProperties": {
        "source.integration": "webhook PaymentGatewayCallbackController.onPaymentConfirmed"
      }
    },
    "ERRO_PAGAMENTO_RECUSADO": {
      "id": "ERRO_PAGAMENTO_RECUSADO",
      "name": "Pagamento Recusado",
      "description": "Captura PaymentDeclinedException lançada pelo callback do gateway.",
      "type": "BOUNDARY_ERROR_HANDLER",
      "attachedToRef": "COBRAR_GATEWAY_PAGAMENTO",
      "errorCode": "PAGAMENTO_RECUSADO",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [
        { "id": "flow-6", "name": "", "targetNodeId": "PEDIDO_CANCELADO", "transitionType": "automated", "isDefault": false, "extensionProperties": {} }
      ],
      "layout": { "x": 780, "y": 200 },
      "extensionProperties": {
        "source.class": "com.empresa.pedidos.PaymentGatewayCallbackController",
        "source.method": "catch (PaymentDeclinedException e)"
      }
    },
    "PEDIDO_CONFIRMADO": {
      "id": "PEDIDO_CONFIRMADO",
      "name": "Pedido Confirmado",
      "type": "DEFAULT_END_EVENT",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [],
      "layout": { "x": 1040, "y": 0 },
      "extensionProperties": {}
    },
    "PEDIDO_CANCELADO": {
      "id": "PEDIDO_CANCELADO",
      "name": "Pedido Cancelado",
      "type": "DEFAULT_END_EVENT",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [],
      "layout": { "x": 1040, "y": 200 },
      "extensionProperties": {}
    }
  }
}
```

Use este exemplo como molde de formatação — não como conteúdo a copiar. O fluxo, os nomes de nó e a
rastreabilidade devem vir sempre da leitura real do projeto Java fornecido.
