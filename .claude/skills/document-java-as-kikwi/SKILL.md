---
name: document-java-as-kikwi
description: >
  Reads any Java project and produces a .kikwi file (Kikwiflow process JSON) documenting the business flow
  implemented in the code — steps, decisions, external calls, error handling, waits, and parallelism — meant
  to be visualized as a diagram. Documentation-only: the generated file is never deployed or executed against
  a real Kikwiflow engine. Trigger this skill when asked to "document", "map", or "generate a diagram/.kikwi"
  for the flow of an existing Java project.
---

# Skill: Document a Java project as `.kikwi`

## What Kikwiflow is (context needed before you start)

Kikwiflow is a process orchestration engine (workflow/BPM) built in Java, with one central difference from
traditional engines (Camunda, Activiti, jBPM): **it is not based on BPMN XML and does not use an expression
language** (no embedded SpEL/JUEL/FEEL). A Kikwiflow process is a **graph of nodes described in JSON** — the
`.kikwi` file — and the logic of each node (decision, task) is, in a real application, a plain Java class
registered as a bean and referenced by name (`executor`, `providerBean`).

In practice, a `.kikwi` file visually represents what a business flowchart would: a starting point, a sequence
of steps, decision points, waits for something external (a human, a system, a deadline), error handling, and
one or more end points — just structured as a specific JSON schema, with its own validity rules (which node
types exist, what each can/cannot do, how connections between nodes work). This schema is what this skill uses
as a **diagramming language**, not as something meant to actually run.

There is also an **official Kikwiflow visual editor** that renders these files as a canvas of connected cards
(used as the sizing/layout reference in the "Real node sizes" section below) — the `.kikwi` produced by this
skill should be ready for that kind of visualization, even though it is never deployed.

## When to use this skill

Use it when asked to **document, map, or diagram the business flow of an existing Java project** in the
Kikwiflow format — typical requests look like "document this service as `.kikwi`", "generate a diagram of this
project's order flow", "I want to visualize how this module works as a process". **Do not** use this skill to
generate processes meant to actually be deployed/executed on a real Kikwiflow engine, and do not create new
`TaskHandler`s/beans in the analyzed project — that is out of scope (see the golden rule below).

## Objective

Whenever triggered, this skill reads the given Java project and produces **a single `.kikwi` file** (JSON)
that documents, in Kikwiflow's process format, the business flow that code implements — its steps, decisions,
external calls, error handling, and waits. The input project can be any Java codebase (Spring or not, monolith
or service) — nothing below is specific to any one project.

**Golden rule: this is documentation, not deployment.** The resulting file is never going to be deployed onto
a real Kikwiflow engine. It exists so a human (or another agent) can visualize/understand the business flow by
looking at a structured graph, instead of reconstructing it by reading class after class. Because of this:

- **Do not create** `TaskHandler` classes, Spring beans, or any new code in the project.
- **Do not try** to run, compile against, or validate the file against a real Kikwiflow engine.
- The `executor`/`providerBean` values in the `.kikwi` **do not need to correspond to a Spring bean that
  actually exists** — they are descriptive names pointing back to the originating Java class/method (see
  Step 4).
- If the project doesn't use Spring, or has no "beans" in the sense Kikwiflow uses, that's fine — treat
  `executor`/`providerBean` as a free-text identifier, not a requirement that a matching bean exists.

The final file needs to be **structurally valid** (well-formed JSON, every reference between nodes resolves,
gateway rules respected — see the checklist in Step 5) even though it's never executed, because that's what
lets the file open cleanly in a Kikwiflow modeling/visualization tool.

---

## Step 1 — Read the project and identify the process

Before writing any JSON, mentally (or on a scratch pad) assemble the list of business-flow steps. Look for:

| What to look for in the code | Becomes, in the `.kikwi`... |
|---|---|
| Entry point (REST controller, queue listener, `main`, scheduled job) | The `DEFAULT_START_EVENT` node |
| A sequence of method/service calls, one after another | A chain of `EXECUTABLE_TASK` |
| `if`/`else`, `switch`, Strategy Pattern, business rule that picks a path | `EXCLUSIVE_GATEWAY` |
| Call to an external system that doesn't return right away (async queue, expected webhook, human approval, callback) | `EXTERNAL_TASK` |
| Firing a correlated event outward (publishing a message another system will react to, without waiting for a response) | `EVENT_THROWER` |
| Waiting for a message/callback identified by a business key (e.g. a payment-approved webhook) | `EVENT_CATCHER` |
| `try/catch` of a known business exception (validation failed, a rule rejected the case) — **not** a bug/infra failure | `BOUNDARY_ERROR_HANDLER` |
| Explicit timeout/SLA/deadline in the code (e.g. cancel if no response within X minutes) | `BOUNDARY_INTERRUPTIVE_TIMER` (cancels the wait) or `BOUNDARY_NON_INTERRUPTIVE_TIMER` (only notifies, doesn't cancel) |
| A deadline wait that is itself the next step of the flow (not attached to another task) — e.g. "wait 24h before the next reminder" | `TIMER_TASK` |
| Parallel execution of N independent tasks, all needing to finish before continuing | `PARALLEL_GATEWAY` (opens the branches) + `JOIN_GATEWAY` (synchronizes) |
| Call to another module/service that is itself a complete business process (or batch processing over a list of items) | `CALL_ACTIVITY_COORDINATOR` |
| `return`, end of a path, unhandled exception that terminates the flow | `DEFAULT_END_EVENT` |

Don't model at the code level (a trivial null-check `if` doesn't need to become a gateway) — model at the
**business process** level: the steps someone from the business side would recognize as "steps" if you
narrated the flow out loud.

---

## Step 2 — File structure

A `.kikwi` file is a single JSON object with these top-level fields:

```json
{
  "key": "stable-process-identifier",
  "name": "Readable Process Name",
  "description": "What this process represents, in 1-2 sentences.",
  "extensionProperties": {},
  "sla": "",
  "flowNodes": { "...": "..." },
  "defaultStartPoint": "START_NODE_ID"
}
```

- `key`: `kebab-case`, short, stable (e.g. `order-processing`).
- `flowNodes`: a map of `node id → node object`. **The map key and the `"id"` field inside the node object
  must be identical.** This isn't just style — the real engine uses the map key to resolve references and the
  internal `id` field for other things; a mismatch between the two is a whole class of silent bug (even with
  the file never being executed, keep them equal so the file serves as a correct reference).
- `defaultStartPoint`: the `id` of the `DEFAULT_START_EVENT` node. Required.

Every node, regardless of type, carries these common fields:

```json
{
  "id": "SAME_VALUE_AS_THE_MAP_KEY",
  "name": "Short, readable name",
  "description": "What this step does, in business language.",
  "type": "NODE_TYPE",
  "commitBefore": false,
  "commitAfter": false,
  "outgoing": [ /* list of connections, see below */ ],
  "layout": { "x": 0, "y": 0 },
  "extensionProperties": {}
}
```

- `commitBefore`/`commitAfter`: for documentation purposes, use these as semantic flags — `true` on an
  `EXECUTABLE_TASK` suggests "this is an operation that can take time/fail/does external I/O" (reflect what
  the real code does, e.g. an HTTP call gets `commitBefore: true`; an in-memory data transformation stays
  `false`). It has no effect since nothing is actually executed, but it documents intent.
- `layout`: coordinates for visualization (`{ "x": number, "y": number }`, top-left of the node). Every node
  carries this field — including boundary events — but not every node **takes up its own space in the
  diagram**. See the table below before computing `x`/`y`, or you'll end up with exactly the "pile of
  overlapping nodes" this guide exists to prevent.

### Real node sizes (extracted from the Kikwiflow visual editor — avoids overlap)

The official Kikwiflow editor/monitor (`packages/flow-nodes`, React components) renders every node type at a
real, predictable size. Use these values to compute `x`/`y` — they are not a guess, they were read straight
from each component's CSS (Tailwind):

| Node family | Types | Width | Base height (no extras) | What increases the height |
|---|---|---|---|---|
| Event (circle) | `DEFAULT_START_EVENT`, `DEFAULT_END_EVENT` | ~140px (the circle itself is 40px, but the label below it is what defines the occupied width) | ~70px (circle + label) | Nothing — fixed size |
| Gateway (diamond) | `EXCLUSIVE_GATEWAY`, `PARALLEL_GATEWAY`, `JOIN_GATEWAY` | ~140px (same reasoning as events) | ~80px (56px diamond + label) | Nothing — fixed size |
| Task (rectangle) | `EXECUTABLE_TASK`, `EXTERNAL_TASK`, `TIMER_TASK`, `EVENT_CATCHER`, `CALL_ACTIVITY_COORDINATOR` | **300px, always fixed** | `EXECUTABLE_TASK`: ~150px (the retry footer always renders). All others: ~120px | Each boundary event **listed inside the card** (non-interruptive timer, error handler, catch event) adds **+~35px**. An **interruptive** timer attached (SLA) adds **+~30px** (timeout footer), except on `EXECUTABLE_TASK`, which already counts that footer in its base. `EVENT_CATCHER` with `catchType: GROUP` or `CALL_ACTIVITY_COORDINATOR` with `collectionVariable` add **+~28px** (progress bar) |

:::tip The single most important finding for avoiding overlap
**Boundary events (`BOUNDARY_INTERRUPTIVE_TIMER`, `BOUNDARY_NON_INTERRUPTIVE_TIMER`, `BOUNDARY_ERROR_HANDLER`,
`BOUNDARY_INTERRUPTIVE_CATCH_EVENT`) do not get their own box in the diagram.** The editor draws them
**inside** the parent node's card (as a row/badge), not as a separate node floating next to it. In practice
this means: you don't need to reserve `x`/`y` real estate for them — you can even leave them at the same
`x`/`y` as the parent. Their only real layout effect is **increasing the parent card's height** (see the table
above), which pushes down whatever node is placed right below it in the same column.
:::

**Practical spacing rules (`x`/`y`) to avoid overlap:**

- **Between two "task"-family nodes in sequence** (the most common case): increment `x` by **at least
  480–500px** — 300px of card width + 180–200px of margin so the connecting line isn't crammed.
- **Between a (compact) event/gateway and a task**, in either order: incrementing `x` by **at least
  300–350px** is enough (the combined width of the two is already well under 480, but keep that margin for
  the event/gateway's label, which is usually wider than the circle/diamond itself).
- **Between two compact events/gateways in sequence**: **~250px** is already safe.
- **Parallel/alternative branches (different rows of the same flow)**: separate `y` by **at least 220–260px**
  per row when task cards are involved — this leaves room for a card to grow (boundary events, timeout footer)
  without invading the neighboring row. For rows with only compact events/gateways, 120–140px is enough.
- When deciding a specific row's height, use the **largest estimated height among that row's nodes** (base
  from the table + boundary-event/progress-bar increments) as the reference for how far below it the next row
  needs to be.
:::

:::info `EVENT_THROWER` has no dedicated component in the visual editor yet
Same finding as the one from the `docs/engine` sweep (see
`docs/engine/22-pendencias-lancamento-v1-community.md`), the `flow-nodes` package also has no dedicated card
for `EVENT_THROWER` — it doesn't appear in the editor's node registry (`nodeRegistry.ts`). If the process
you're documenting has this node type, treat it as a "task"-family node (300px wide, ~120px base height) for
layout purposes — that's the safest assumption until the editor gets its own component for it.
:::

Each outgoing connection (`outgoing`) is:

```json
{
  "id": "flow-<uuid-or-descriptive-slug>",
  "name": "",
  "targetNodeId": "NEXT_NODE_ID",
  "transitionType": "automated",
  "isDefault": false,
  "expectedAnswer": null,
  "handlesNull": false,
  "extensionProperties": {}
}
```

- `targetNodeId` **must** exist as a key in `flowNodes`.
- `expectedAnswer`/`isDefault`/`handlesNull` only have an effect when the source node is `EXCLUSIVE_GATEWAY`
  (see Step 3). For any other node type, leave `isDefault: false`, no `expectedAnswer`/`handlesNull`, and
  declare **at most one** entry in `outgoing` (regular nodes only have one logical exit).

---

## Step 3 — Complete node type catalog

Use exactly these 16 values in the `"type"` field — don't invent new types.

| `type` | When to use it | Relevant extra fields |
|---|---|---|
| `DEFAULT_START_EVENT` | One per process, pointed to via `defaultStartPoint` | — |
| `DEFAULT_END_EVENT` | Every terminal path. `outgoing` is always empty | — |
| `EXECUTABLE_TASK` | Synchronous in-process logic (calculation, validation, transformation, quick call) | `executor` (descriptive name, see Step 4) |
| `EXTERNAL_TASK` | Waits on something outside the flow's direct control (a human, an external worker, a queue) | — (no `executor`) |
| `EXCLUSIVE_GATEWAY` | Decision — follows **one** path among several | `providerType` (`BEAN`/`VARIABLE`) + `providerBean` or `providerVariable` |
| `PARALLEL_GATEWAY` | Opens multiple simultaneous branches | `targetJoinId` (points to the matching `JOIN_GATEWAY`) |
| `JOIN_GATEWAY` | Closes the branches opened by the matching `PARALLEL_GATEWAY` | `sourceSplitId` (informational) |
| `BOUNDARY_INTERRUPTIVE_TIMER` | A deadline that, when it expires, cancels the parent node's wait and reroutes the flow | `attachedToRef`, `providerType` (`STATIC`/`VARIABLE`/`BEAN`) + `staticValue`/`providerVariable`/`providerBean` |
| `BOUNDARY_NON_INTERRUPTIVE_TIMER` | A recurring deadline that only notifies, without canceling the parent node | `attachedToRef`, `schedulePolicy` |
| `BOUNDARY_ERROR_HANDLER` | Catches an expected business error from the parent node | `attachedToRef`, `errorCode` (optional — without it, it's a wildcard) |
| `BOUNDARY_INTERRUPTIVE_CATCH_EVENT` | Cancels the parent node via an external correlation (not a deadline) | `attachedToRef`, same correlation fields as `EVENT_CATCHER` |
| `TIMER_TASK` | Waits on a deadline as the main flow's own next step (not attached to another node) | `providerType` + `staticValue`/`providerVariable`/`providerBean` |
| `EVENT_CATCHER` | Reactive wait for an external correlation key (webhook, callback) | `catchType` (`STANDALONE`/`GROUP`), `providerType`, `matchPolicy` (only in `GROUP`) |
| `EVENT_THROWER` | Fires/publishes a correlation key outward (the emitting counterpart of `EVENT_CATCHER`) | same correlation fields as `EVENT_CATCHER`, no `catchType`/`matchPolicy` |
| `CALL_ACTIVITY_COORDINATOR` | Calls another process/module as its own business unit; or processes a list in batch | `calledElement`, `collectionVariable`/`elementVariable` (optional, batch only) |

### Where each boundary event (`boundaryEventIds`) can be attached

Nodes that accept boundary events declare `"boundaryEventIds": ["EVENT_ID_1", "EVENT_ID_2"]`, and each boundary
event references back via `"attachedToRef": "PARENT_NODE_ID"`. Not every combination is valid:

| Parent node | Interruptive timer | Non-interruptive timer | Error handler | Correlation catch event |
|---|---|---|---|---|
| `EXECUTABLE_TASK` | ❌ Not allowed | ✅ | ✅ | ❌ Not allowed |
| `EXTERNAL_TASK` | ✅ | ✅ | ❌ | ✅ |
| `TIMER_TASK` | ✅ | ✅ | ❌ | ✅ |
| `EVENT_CATCHER` | ✅ | ✅ | ❌ | ❌ |
| `CALL_ACTIVITY_COORDINATOR` | ✅ (timers only) | ✅ (timers only) | ❌ | ❌ |

Practical reason (document this if the real process has this shape): an `EXECUTABLE_TASK` runs its handler
synchronously — there's no way to "cancel from outside" mid-call without risking a side effect having already
happened. A business error on it is always `try/catch` (`BOUNDARY_ERROR_HANDLER`), never an asynchronous
interruption.

### `EXCLUSIVE_GATEWAY`: routing rules

A decision gateway's routing follows this priority order — reflect the real Java logic faithfully when
deciding each edge's `expectedAnswer`/`isDefault`/`handlesNull`:

1. If the resolved decision is `null` → follow the edge with `handlesNull: true` (at most one per gateway).
2. Otherwise → follow the first edge whose `expectedAnswer` matches the decision exactly (string comparison).
3. If none match → follow the edge with `isDefault: true` (at most one per gateway).

---

## Step 4 — Traceability: linking each node back to the real source code

This is the part that gives the file its documentation value. For **every node** that represents a real step
of code (`EXECUTABLE_TASK`, `EXCLUSIVE_GATEWAY`, `EXTERNAL_TASK`, etc.), fill `extensionProperties` with the
origin:

```json
"extensionProperties": {
  "source.class": "com.company.orders.OrderService",
  "source.method": "processOrder(Order order)",
  "source.file": "src/main/java/com/company/orders/OrderService.java:42"
}
```

- For `EXECUTABLE_TASK`, give the `executor` field a descriptive name derived from the real method (e.g. a
  `calculateShipping` method on a `ShippingService` class → `"executor": "shippingServiceCalculateShipping"`),
  and use `extensionProperties` for the exact path. **Do not claim that name corresponds to a registered
  Spring bean** — it's just a readable label.
- For `EXCLUSIVE_GATEWAY`, point `providerBean`/`providerVariable` at the real condition (e.g. the
  field/method the business logic tests), and explain the decision rule in plain language in `description`.
- For `EXTERNAL_TASK`/`EVENT_CATCHER`/`EVENT_THROWER`, point at the real integration (queue, endpoint, topic)
  instead of a class — e.g. `"source.integration": "SQS queue order-paid"`.
- If a node doesn't map to a specific piece of code (e.g. a `DEFAULT_START_EVENT` representing "the HTTP
  request arrives"), describe that in `description` even without a code `extensionProperties`.

---

## Step 5 — Structural validity checklist before delivering

Check every item before considering the file done — a `.kikwi` that violates any of these is structurally
invalid, even though it's never executed:

- [ ] Syntactically valid JSON (no trailing commas, correct quoting, UTF-8).
- [ ] `defaultStartPoint` points to a node that exists in `flowNodes` and is of type `DEFAULT_START_EVENT`.
- [ ] Every `flowNodes` entry's key is **identical** to the `"id"` field inside that node's own object.
- [ ] Every `targetNodeId` in any `outgoing` points to a key that exists in `flowNodes`.
- [ ] Every `DEFAULT_END_EVENT` has an empty `outgoing`.
- [ ] Every node that isn't `EXCLUSIVE_GATEWAY`/`PARALLEL_GATEWAY` has **at most one** entry in `outgoing`.
- [ ] Every `EXCLUSIVE_GATEWAY` declares `providerType` (`BEAN` or `VARIABLE`) and has at least one exit.
- [ ] No `EXCLUSIVE_GATEWAY` has more than one `isDefault: true` edge, nor more than one `handlesNull: true`.
- [ ] Two outgoing edges of the same `EXCLUSIVE_GATEWAY` never repeat the same `expectedAnswer`.
- [ ] Every `PARALLEL_GATEWAY` declares `targetJoinId` pointing to a node that exists **and** is of type
      `JOIN_GATEWAY`.
- [ ] Every node referenced in `boundaryEventIds` exists in `flowNodes`, and its type is compatible with the
      parent node (see the table in Step 3).
- [ ] Every boundary event has `attachedToRef` pointing back to the correct parent node `id`.
- [ ] `CALL_ACTIVITY_COORDINATOR` has `calledElement` filled in; if `elementVariable` is present,
      `collectionVariable` is too.
- [ ] Every node is reachable from `defaultStartPoint` (no "loose" nodes in `flowNodes` that nothing points
      to) — if a code step isn't reached by any path, don't include it, or document why outside the JSON
      (e.g. in the process's `description`).

---

## Step 6 — What to deliver

1. A `<process-name>.kikwi` file, valid JSON, following everything above.
2. A short paragraph (outside the JSON, in the chat/PR/wherever it's delivered) summarizing: how many nodes,
   which types were used, and any simplification you made (e.g. "3 trivial null-checks in the code were
   merged into a single `EXECUTABLE_TASK` 'Validate Order' to avoid cluttering the diagram").
3. If some part of the real flow has no obvious matching node type in the Step 3 catalog, **don't force a
   wrong fit** — describe the gap in the summary paragraph instead of modeling something misleading.

Note: node `name`/`description` content in the generated `.kikwi` should reflect whatever language is natural
for the analyzed project's business domain (e.g. Portuguese node names for a Brazilian codebase whose domain
vocabulary is in Portuguese) — this skill's own instructions are in English, but the output should read
naturally to that project's team, not be force-translated.

---

## Full example (reference)

Fictional "Process Order" flow: validates the order, decides between upfront or installment payment, calls an
external payment gateway (asynchronous), cancels the wait if the gateway sends a decline webhook
(`BOUNDARY_INTERRUPTIVE_CATCH_EVENT` — **not** `BOUNDARY_ERROR_HANDLER`, which is only valid on
`EXECUTABLE_TASK`, see the table in Step 3), and finishes. The `layout` coordinates already follow the spacing
rules from the previous section — notice that no 300px card overlaps another.

```json
{
  "key": "process-order",
  "name": "Process Order",
  "description": "Documents the flow implemented in com.company.orders.OrderController/OrderService.",
  "extensionProperties": {},
  "sla": "",
  "defaultStartPoint": "START",
  "flowNodes": {
    "START": {
      "id": "START",
      "name": "Order Received",
      "description": "HTTP POST /orders request arrives at OrderController.",
      "type": "DEFAULT_START_EVENT",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [
        { "id": "flow-1", "name": "", "targetNodeId": "VALIDATE_ORDER", "transitionType": "automated", "isDefault": false, "extensionProperties": {} }
      ],
      "layout": { "x": 0, "y": 0 },
      "extensionProperties": {}
    },
    "VALIDATE_ORDER": {
      "id": "VALIDATE_ORDER",
      "name": "Validate Order",
      "description": "Validates items, stock, and customer data.",
      "type": "EXECUTABLE_TASK",
      "executor": "orderServiceValidateOrder",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [
        { "id": "flow-2", "name": "", "targetNodeId": "DECIDE_PAYMENT_METHOD", "transitionType": "automated", "isDefault": false, "extensionProperties": {} }
      ],
      "layout": { "x": 380, "y": 0 },
      "extensionProperties": {
        "source.class": "com.company.orders.OrderService",
        "source.method": "validateOrder(Order order)",
        "source.file": "src/main/java/com/company/orders/OrderService.java:30"
      }
    },
    "DECIDE_PAYMENT_METHOD": {
      "id": "DECIDE_PAYMENT_METHOD",
      "name": "Payment Method?",
      "description": "Decides between upfront (synchronous call to the gateway) and installments (async approval flow).",
      "type": "EXCLUSIVE_GATEWAY",
      "providerType": "VARIABLE",
      "providerVariable": "paymentMethod",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [
        { "id": "flow-3", "name": "", "targetNodeId": "CHARGE_PAYMENT_GATEWAY", "transitionType": "automated", "expectedAnswer": "UPFRONT", "isDefault": false, "extensionProperties": {} },
        { "id": "flow-4", "name": "", "targetNodeId": "CHARGE_PAYMENT_GATEWAY", "transitionType": "automated", "isDefault": true, "extensionProperties": {} }
      ],
      "layout": { "x": 740, "y": 0 },
      "extensionProperties": {
        "source.class": "com.company.orders.OrderService",
        "source.method": "if (order.getPaymentMethod() == PaymentMethod.UPFRONT) { ... }"
      }
    },
    "CHARGE_PAYMENT_GATEWAY": {
      "id": "CHARGE_PAYMENT_GATEWAY",
      "name": "Await Payment Gateway Confirmation",
      "description": "Sends the charge to the external gateway and waits for an asynchronous confirmation callback.",
      "type": "EXTERNAL_TASK",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [
        { "id": "flow-5", "name": "", "targetNodeId": "ORDER_CONFIRMED", "transitionType": "automated", "isDefault": false, "extensionProperties": {} }
      ],
      "boundaryEventIds": ["PAYMENT_DECLINED_CATCH"],
      "layout": { "x": 1100, "y": 0 },
      "extensionProperties": {
        "source.integration": "webhook PaymentGatewayCallbackController.onPaymentConfirmed"
      }
    },
    "PAYMENT_DECLINED_CATCH": {
      "id": "PAYMENT_DECLINED_CATCH",
      "name": "Payment Declined (Webhook)",
      "description": "Cancels the wait when the gateway sends a decline webhook, correlated by order id. Has no box of its own in the diagram — the editor draws this as a row inside the 'Await Payment Gateway Confirmation' card (see the sizing section above); the layout.x/y below only exists because the field is required by the schema, not because it occupies its own visual space.",
      "type": "BOUNDARY_INTERRUPTIVE_CATCH_EVENT",
      "attachedToRef": "CHARGE_PAYMENT_GATEWAY",
      "providerType": "VARIABLE",
      "providerVariable": "orderId",
      "keyPrefix": "PAYMENT_DECLINED_",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [
        { "id": "flow-6", "name": "", "targetNodeId": "ORDER_CANCELED", "transitionType": "automated", "isDefault": false, "extensionProperties": {} }
      ],
      "layout": { "x": 1100, "y": 240 },
      "extensionProperties": {
        "source.class": "com.company.orders.PaymentGatewayCallbackController",
        "source.method": "onPaymentDeclined(String orderId)"
      }
    },
    "ORDER_CONFIRMED": {
      "id": "ORDER_CONFIRMED",
      "name": "Order Confirmed",
      "type": "DEFAULT_END_EVENT",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [],
      "layout": { "x": 1500, "y": 0 },
      "extensionProperties": {}
    },
    "ORDER_CANCELED": {
      "id": "ORDER_CANCELED",
      "name": "Order Canceled",
      "type": "DEFAULT_END_EVENT",
      "commitBefore": false,
      "commitAfter": false,
      "outgoing": [],
      "layout": { "x": 1500, "y": 240 },
      "extensionProperties": {}
    }
  }
}
```

Use this example as a formatting template — not as content to copy. The flow, node names, traceability, and
`layout` coordinates must always come from actually reading the given Java project and applying the spacing
rules above, not from these fixed values.
