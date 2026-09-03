---
name: model-kikwi-process
description: >
  Turns a natural-language specification (a user story, a requirements doc, a description of a business
  process someone types out loud) into a Kikwiflow process definition — a `.kikwi` JSON file with the right
  node types, valid sequence flows, and non-overlapping layout coordinates, ready to be implemented and
  deployed. Trigger this skill when asked to "model", "design", or "create a process/flow" for Kikwiflow from a
  description of desired behavior. This is the mirror image of `document-java-as-kikwi` (which reads *existing
  code*, documentation-only, never meant to run) — this skill starts from *intent*, not code, and its output is
  meant to become real: `executor`/`providerBean` values should resolve to actual beans (existing or to be
  built), not just be readable labels, and the JSON must match the exact field names the engine deserializes
  (`ProcessDefinitionDeployRequest`/`FlowNodeDefinition` in `kikwi-model`), not an approximation of them.
---

# Skill: Model a Kikwiflow process from a natural-language spec

## What Kikwiflow is (context needed before you start)

Kikwiflow is a process orchestration engine (workflow/BPM) built in Java, with one central difference from
traditional engines (Camunda, Activiti, jBPM): **it is not based on BPMN XML and does not use an expression
language** (no embedded SpEL/JUEL/FEEL). A process is a **graph of nodes described in JSON** — the `.kikwi`
file — and the logic of each node (decision, task) is, in a real application, a plain Java class registered as
a Spring bean and referenced by name (`executor` on `EXECUTABLE_TASK`, `providerBean` on `EXCLUSIVE_GATEWAY`
with `providerType: BEAN`, etc.).

## When to use this skill, and how it differs from `document-java-as-kikwi`

Use this skill when the starting point is **a description of what should happen**, not code that already
exists — "model an onboarding process where...", "design the flow for handling a return request...", a
paragraph or ticket describing steps/decisions/waits. If a companion `document-java-as-kikwi` skill is present
in this project, note the asymmetry: that skill reads code and produces a diagram that is **never meant to run**
(bean names are just descriptive labels, and its JSON shape is a simplified approximation good enough for the
visual editor). This skill goes the other way and its output **is meant to become real**:

- `executor` / `providerBean` values should, wherever possible, be resolved against beans that **actually
  exist** in the target project (see Step 4). Where no matching bean exists, that's not a blocker — it's a
  concrete, named TODO you hand back at the end (Step 7), not something to gloss over.
- The JSON must use the **exact field names** the engine's Jackson mapping expects (Step 2/3 below are
  transcribed directly from `kikwi-model`'s `record`s, not paraphrased from prose docs) and pass real
  deploy-time validation (Step 6 is grounded in `kikwi-core`'s `DeployValidator` source, split explicitly
  between what actually blocks a deploy today and what doesn't but will still break the process at runtime).

## Step 1 — Read the spec and build the flow skeleton

Before writing any JSON, extract the step list from the spec. Map business-language cues to node types:

| What the spec says (business language) | Becomes |
|---|---|
| "the process starts when...", "a request arrives...", a trigger condition | `DEFAULT_START_EVENT` |
| "the system does X automatically", "calculates", "validates", "transforms" (fast, in-process, no waiting on anyone) | `EXECUTABLE_TASK` |
| "wait for approval from...", "an operator/analyst reviews...", "assigned to a team", "a human decides" | `EXTERNAL_TASK` |
| "if / depending on / when X, then... otherwise...", any branching business rule | `EXCLUSIVE_GATEWAY` |
| "at the same time...", "in parallel...", "simultaneously do X and Y, then proceed once both are done" | `PARALLEL_GATEWAY` (opens) + `JOIN_GATEWAY` (closes) |
| "within N hours/days, if there's no response, do Z" (and Z **replaces** the wait) | `BOUNDARY_INTERRUPTIVE_TIMER` attached to the task being waited on |
| "after N hours, send a reminder" (and the original wait **keeps going**) | `BOUNDARY_NON_INTERRUPTIVE_TIMER` |
| "if this fails/is rejected for reason Y, do Z" (an *expected* business outcome, not a bug) | `BOUNDARY_ERROR_HANDLER` attached to the failing task |
| "wait N days before the next step" as the flow's own next step (not attached to another wait) | `TIMER_TASK` |
| "wait for an external notification/webhook/callback identified by X" | `EVENT_CATCHER` |
| "notify/publish/emit event X to other systems" | `EVENT_THROWER` |
| "delegate to process Y as a sub-step", "for each item in the list, run process Y" | `CALL_ACTIVITY_COORDINATOR` |
| "the process ends", "request is completed/rejected/cancelled" (every distinct terminal outcome) | `DEFAULT_END_EVENT` |

Model at the **business step** level a domain expert would recognize if you narrated the flow out loud — don't
turn every trivial detail the spec mentions into its own node.

### Handle gaps explicitly — don't silently invent business rules

Natural-language specs are almost always incomplete somewhere. Before finalizing the model, check for
**load-bearing gaps** — ones that change what the process actually does:

- An `EXCLUSIVE_GATEWAY` where the spec describes some branches but not what happens on an unlisted answer, or
  doesn't say whether the deciding value can come back empty/unknown.
- A task the spec implies can fail (external call, human rejection) with no stated error/timeout handling.
- A decision variable, business key, or piece of data the spec references but never says where it comes from.

For these, **ask the target user a clarifying question** (or, if genuinely running as an autonomous agent step
with no one to ask, pick the most conservative default — e.g. an `isDefault` edge that routes to an explicit
"needs manual review" path rather than silently completing — and call it out prominently in the delivery
summary, Step 7). Do **not** guess a specific business outcome (e.g. inventing what happens when a compliance
check "fails") and present it as if the spec said so.

Gaps that are purely cosmetic (a node's display name, exact wording of a label) don't need this — use
reasonable, spec-consistent defaults.

---

## Step 2 — File structure

A `.kikwi` file deployed via `POST /process-definitions` is a `ProcessDefinitionDeployRequest`:

```json
{
  "key": "stable-process-identifier",
  "name": "Readable Process Name",
  "description": "What this process represents, in 1-2 sentences.",
  "sla": "",
  "defaultStartPoint": "START_NODE_ID",
  "flowNodes": { "...": "..." },
  "extensionProperties": {}
}
```

(`id`, `version`, `checksum` don't exist yet at this point — the engine fills them in on deploy, and reading
back a deployed `ProcessDefinition` will include them alongside these same fields.)

- `key`: `kebab-case`, short, stable — this is what `KikwiflowEngine.startProcess().byKey(...)` targets, and
  what a redeploy under the same value versions over (running instances stay on their original version).
- `flowNodes`: a map of `node id → node object`. **The map key and the `"id"` field inside the node object
  must be identical** — the engine resolves references by the map key, not by the inner `id`, and nothing in
  the parser reconciles a mismatch for you.
- `defaultStartPoint`: the `id` of the `DEFAULT_START_EVENT` node.

Every node carries these common fields (from `FlowNodeDefinition`):

```json
{
  "id": "SAME_VALUE_AS_THE_MAP_KEY",
  "name": "Short, readable name",
  "type": "NODE_TYPE",
  "description": "What this step does, in business language — tie it back to the spec sentence it came from.",
  "commitBefore": false,
  "commitAfter": false,
  "outgoing": [ /* see below */ ],
  "extensionProperties": {},
  "layout": { "x": 0.0, "y": 0.0 }
}
```

`commitBefore: true` means: the engine persists state **before** running this node and execution from that
point on becomes asynchronous (a background worker, not the same call that triggered the previous step) —
picked up automatically for `EXTERNAL_TASK`/waits, and worth setting explicitly on an `EXECUTABLE_TASK` that
does slow/failure-prone I/O (an external HTTP call, a write with retry semantics) so it survives an app
restart mid-flight. Leave `false` on fast in-memory logic.

Only some node types additionally carry `boundaryEventIds` (a `List<String>`) — `EXECUTABLE_TASK`,
`EXTERNAL_TASK`, `EVENT_CATCHER`, `TIMER_TASK`, `CALL_ACTIVITY_COORDINATOR`. The rest (gateways, events,
boundary nodes themselves) don't have the field at all.

### `outgoing` — the exact `SequenceFlowDefinition` shape

**This is the field set the engine actually deserializes — use exactly this, nothing more:**

```json
{
  "id": "flow-<uuid-or-descriptive-slug>",
  "name": "",
  "description": "",
  "targetNodeId": "NEXT_NODE_ID",
  "isDefault": false,
  "handlesNull": false,
  "expectedAnswer": null
}
```

There is **no `transitionType` field and no `extensionProperties` field on a sequence flow** — those exist on
`FlowNodeDefinition` (the node), not on `SequenceFlowDefinition` (the edge); including them is harmless if the
target project's Jackson config ignores unknown properties, but don't rely on that, and don't invent them from
memory. `isDefault`/`handlesNull` are plain `boolean` (default `false` if omitted); `expectedAnswer` only means
anything on an `EXCLUSIVE_GATEWAY`'s edges (Step 3). A `positionHandlers` field (waypoints for the connector
line's visual bend points) exists too — purely cosmetic, safe to omit. Every node type other than
`EXCLUSIVE_GATEWAY`/`PARALLEL_GATEWAY` should declare **at most one** entry in `outgoing`.

---

## Step 3 — Node type catalog and required fields

Field names below are transcribed from the actual `record`s in `kikwi-model` — not the prose docs, which can
drift.

| `type` | Fields beyond the common set | Notes |
|---|---|---|
| `DEFAULT_START_EVENT` | — | Exactly one `outgoing`. |
| `DEFAULT_END_EVENT` | — | `outgoing` always empty. One per distinct terminal outcome the spec names. |
| `EXECUTABLE_TASK` | `executor` (string), `retryPolicy` (optional, see below) | Synchronous in-process logic. `boundaryEventIds` allowed. |
| `EXTERNAL_TASK` | — (no `executor`) | Waits on something outside the engine's direct control. `boundaryEventIds` allowed. |
| `EXCLUSIVE_GATEWAY` | `providerType` (`BEAN`/`VARIABLE`), `providerBean`, `providerVariable`, `defaultFlow` (informational) | See routing rules below. No `boundaryEventIds`. |
| `PARALLEL_GATEWAY` | `targetJoinId` | Opens simultaneous branches. No `boundaryEventIds`. |
| `JOIN_GATEWAY` | `sourceSplitId` (informational) | Closes branches. Reached only via a `PARALLEL_GATEWAY`'s `targetJoinId`. |
| `BOUNDARY_INTERRUPTIVE_TIMER` | `attachedToRef`, `providerType` (`STATIC`/`VARIABLE`/`BEAN`), `staticValue`/`providerVariable`/`providerBean` matching that type | Cancels the parent's wait when it fires. |
| `BOUNDARY_NON_INTERRUPTIVE_TIMER` | `attachedToRef`, `schedulePolicy` (see below) | Only notifies, doesn't cancel. |
| `BOUNDARY_ERROR_HANDLER` | `attachedToRef`, `errorCode` (optional — omitted means wildcard) | Catches a business error from the parent. |
| `BOUNDARY_INTERRUPTIVE_CATCH_EVENT` | `attachedToRef`, correlation fields (see below) | Cancels the parent via external correlation, not a deadline. |
| `TIMER_TASK` | `providerType` (`STATIC`/`VARIABLE`/`BEAN`), `staticValue`/`providerVariable`/`providerBean` | A deadline as the flow's own next step. `boundaryEventIds` allowed. |
| `EVENT_CATCHER` | `catchType` (`STANDALONE`/`GROUP`), `matchPolicy` (`ALL`/`ANY`, only meaningful in `GROUP`), correlation fields (see below) | Reactive wait for a correlation key. `boundaryEventIds` allowed. |
| `EVENT_THROWER` | Correlation fields (see below) | Fires a correlation key outward. No `boundaryEventIds`. |
| `CALL_ACTIVITY_COORDINATOR` | `calledElement`, `collectionVariable`/`elementVariable` (optional, batch only), `iterationMode` (`PARALLEL`/`SEQUENTIAL`, `null` behaves as `PARALLEL`) | Delegates to another process. `boundaryEventIds` allowed. |

### Correlation fields (shared shape — `EVENT_CATCHER`, `EVENT_THROWER`, `BOUNDARY_INTERRUPTIVE_CATCH_EVENT`)

All three implement the same `providerType` contract, now with **four** options (one more than the timer
provider types):

| `providerType` | Required field(s) |
|---|---|
| `STATIC` | `staticKey` (a fixed string — not `staticValue`, that name is reserved for timer due-dates) |
| `VARIABLE` | `providerVariable` |
| `BEAN` | `providerBean` — must resolve to a registered `CorrelationKeysProvider` bean |
| `TEMPLATE` | `correlationTemplates` — a list of `{ keySegments, displayNameSegments }`, built for scenarios where the correlation key is assembled from multiple fixed/variable segments rather than one field |

`keyPrefix`/`keySuffix`/`displayNamePrefix`/`displayNameSuffix` are optional cosmetic/technical modifiers on
top of whichever provider type is chosen. `EVENT_CATCHER` in `catchType: GROUP` cannot use `providerType:
STATIC` — `STATIC` always resolves to exactly one key, which is incompatible with waiting on a group of keys
(use `VARIABLE`, `BEAN`, or `TEMPLATE` instead).

### Where boundary events can attach (`boundaryEventIds`)

Nodes that accept boundary events declare `"boundaryEventIds": ["ID_1", "ID_2"]`; each boundary node points
back via `"attachedToRef": "PARENT_ID"`. The allowed combination differs per host type — this table is the
real allowlist enforced by `kikwi-core`'s `DeployValidator` (see Step 6 for what "enforced" means precisely):

| Parent | Interruptive timer | Non-interruptive timer | Error handler | Interruptive catch event |
|---|---|---|---|---|
| `EXECUTABLE_TASK` | ❌ | ✅ | ✅ | ❌ |
| `EXTERNAL_TASK` | ✅ | ✅ | ❌ | ✅ |
| `CALL_ACTIVITY_COORDINATOR` | ✅ | ✅ | ❌ | ❌ |
| `EVENT_CATCHER` | ✅ | ✅ | ❌ | ❌ |
| `TIMER_TASK` | ✅ | ✅ | ❌ | ✅ |

Every row is now enforced by `DeployValidator` — `EXTERNAL_TASK` used to be the one exception (no allowlist
branch at all), but that gap was closed: attaching a `BOUNDARY_ERROR_HANDLER` to an `EXTERNAL_TASK` is now
rejected at deploy instead of only failing (`NotImplementedException`) the first time a real instance reaches
it.

The reasoning to keep in mind while modeling: a node that runs a synchronous handler with a real side effect
(`EXECUTABLE_TASK`) has no safe point to interrupt from outside mid-call, so its only escape hatch is
`try/catch` (`BOUNDARY_ERROR_HANDLER`). Everything else in this table is a pure wait with no handler of its own
to protect, so it can be cancelled from outside (timer or event) but has nothing to synchronously `catch` a
business exception from.

### `EXCLUSIVE_GATEWAY` routing rules

Priority order — get this right, it's the most common source of a subtly wrong model:

1. If the resolved decision is `null` → follow the edge with `handlesNull: true` (declare at most one).
2. Otherwise → follow the first edge whose `expectedAnswer` matches exactly (string comparison). Two edges of
   the same gateway sharing an `expectedAnswer` (or both setting `handlesNull: true`) are rejected at deploy —
   don't do it, the second one would just be unreachable dead code anyway.
3. If none match → follow the edge with `isDefault: true` (**at most one** — this one *is* enforced, see Step 6).

**Model both a default and a null-handling edge whenever the spec's decision logic isn't provably exhaustive**
— an unhandled decision value with no matching edge silently stalls the instance at that gateway.

### `retryPolicy` (optional, on `EXECUTABLE_TASK`)

```json
{ "strategy": "EXPONENTIAL_BACKOFF", "maxRetries": 3, "initialInterval": "PT10S", "multiplier": 2.0, "maxInterval": "PT5M", "intervals": [] }
```
`strategy` is `LINEAR` or `EXPONENTIAL_BACKOFF` — required and validated at deploy if `retryPolicy` is present
at all. For `LINEAR`, populate `intervals` (a list of duration strings, one per retry attempt). For
`EXPONENTIAL_BACKOFF`, populate `initialInterval` (required, validated at deploy) and optionally
`multiplier`/`maxInterval`. `maxRetries` is also required (>= 1) once `retryPolicy` is present — `maxRetries`
is a primitive `int` in the model, so omitting it doesn't inherit anything, it deserializes to `0` (zero
retries) — this is now rejected at deploy rather than silently opening an incident on the first failure. If
`retryPolicy` is omitted **entirely**, the node still falls back to the engine's built-in default (3 retries,
no config knob to change that default globally) — that fallback only applies to a fully-absent `retryPolicy`,
never to a present-but-incomplete one.

### `schedulePolicy` (required on `BOUNDARY_NON_INTERRUPTIVE_TIMER`)

```json
{ "type": "RATE_DURATION", "expression": "PT24H", "fixedDates": [], "maxOccurrences": null }
```
`type` is `RATE_DURATION` (recurring interval, needs `expression`) or `FIXED_DATES` (needs `fixedDates`, a list
of ISO timestamps) — there is no `CRON` value (removed from `ScheduleType`; it was never real cron parsing,
and the engine stays vendor-neutral by design). `maxOccurrences` is an optional 1-based cap on how many
times the reminder fires (`null` = fires indefinitely as long as the parent node is still waiting).

---

## Step 4 — Resolving `executor`/`providerBean` against real beans

This is what makes the output deployable instead of decorative. If you're running inside (or pointed at) the
target Spring Boot project:

1. Search for existing beans that already do what a step needs — `@Component("name") implements TaskHandler`
   for `EXECUTABLE_TASK.executor`, `implements AnswerProvider` for `EXCLUSIVE_GATEWAY.providerBean` (when
   `providerType: BEAN`), `implements DueDateProvider` for timer `providerBean`, `implements
   CorrelationKeysProvider` for a correlation `providerBean`. Reuse the exact registered name when a match
   exists — don't invent a new name for logic that already exists.
2. For steps with no matching bean, choose a name following the project's existing bean-naming convention
   (usually `camelCase`, often prefixed/suffixed consistently — check a couple of existing examples first) and
   record it as a **component to implement** (see Step 7) — do not silently leave a dangling reference and call
   the file done.
3. If there's no target project to check against (pure modeling exercise, greenfield), use descriptive
   `camelCase` names derived from the step's business meaning, and say explicitly in the delivery summary that
   none of them have been verified against real beans yet.

`EXTERNAL_TASK` never takes an `executor` — there's nothing to resolve there; the "who does it" is whatever
worker/human process claims and completes it via the engine's task API, not a bean reference in the definition.

**Important nuance for `EXECUTABLE_TASK`:** `DeployValidator` only checks bean resolution *if* `executor` is
present — an `EXECUTABLE_TASK` with a blank/missing `executor` deploys successfully today and only fails later,
at runtime, when the engine tries to execute it with nothing to call. Always fill it in; don't treat "the
deploy succeeded" as proof the model is correct.

---

## Step 5 — Layout: real node sizes and spacing (avoids overlap)

The official Kikwiflow visual editor renders every node type at a real, predictable size — use these to compute
`x`/`y` (top-left of the node) so the model opens without overlapping cards:

| Node family | Types | Width | Base height | What increases height |
|---|---|---|---|---|
| Event (circle) | `DEFAULT_START_EVENT`, `DEFAULT_END_EVENT` | ~140px | ~70px | Nothing — fixed |
| Gateway (diamond) | `EXCLUSIVE_GATEWAY`, `PARALLEL_GATEWAY`, `JOIN_GATEWAY` | ~140px | ~80px | Nothing — fixed |
| Task (rectangle) | `EXECUTABLE_TASK`, `EXTERNAL_TASK`, `TIMER_TASK`, `EVENT_CATCHER`, `CALL_ACTIVITY_COORDINATOR` | **300px, always** | `EXECUTABLE_TASK`: ~150px (retry footer always renders). Others: ~120px | Each boundary event listed inside the card adds **+~35px**. An interruptive timer (SLA) adds **+~30px**, except on `EXECUTABLE_TASK` (already counted). `EVENT_CATCHER` with `catchType: GROUP` or `CALL_ACTIVITY_COORDINATOR` with `collectionVariable` add **+~28px** (progress bar). |

**Boundary events don't get their own box.** The editor draws them *inside* the parent card as a row/badge —
don't reserve `x`/`y` space for them; their only layout effect is growing the parent's height (table above),
which pushes down whatever sits below it in the same column. `EVENT_THROWER` has no dedicated editor component
yet — treat it as a 300px/~120px task-family node for layout purposes.

**Spacing rules:**
- Two task-family nodes in sequence: increment `x` by **at least 480–500px** (300px card + margin).
- Compact (event/gateway) → task, either order: **at least 300–350px**.
- Two compact nodes in sequence: **~250px** is safe.
- Parallel/alternative branches (different rows): separate `y` by **at least 220–260px** when task cards are
  involved (120–140px if the row is only compact nodes) — use the tallest node in a row (base height + any
  boundary-event/progress-bar increments) to decide how far below it the next row needs to start.

---

## Step 6 — Validity checklist

Two tiers, deliberately separated. **A** is what `kikwi-core`'s `DeployValidator` actually checks today — get
any of these wrong and `POST /process-definitions` throws `InvalidProcessDefinitionException` (or, for some
gateway/deploy-flag cases, a raw `500` — see the target project's REST testing notes if one exists). **B** is
*not* checked at deploy time in the current engine version — a definition violating these deploys successfully
and then breaks, stalls, or silently misbehaves at runtime. Model to satisfy both; only tier A is what "the
deploy will actually reject" means.

### A — Enforced today (will fail deploy)

Substantially expanded in this revision — most of what used to be tier B (structural/cross-field checks) is
now actually enforced. Cross-referenced against `docs/engine/14-regras-de-processo-valido.md` (`KIKWI-NNN`
ids below) where a catalogued rule exists.

- [ ] Every `targetNodeId` (in any node's `outgoing`) resolves to an existing `flowNodes` key. (`KIKWI-001`)
- [ ] `defaultStartPoint` is non-blank and resolves to an existing `flowNodes` key. (`KIKWI-003`)
- [ ] `DEFAULT_START_EVENT` has **exactly one** `outgoing` entry. (`KIKWI-009`)
- [ ] `EXECUTABLE_TASK.executor` is non-blank (`KIKWI-011`) **and** resolves to a registered `TaskHandler` bean
      (`KIKWI-012`).
- [ ] `EXECUTABLE_TASK.boundaryEventIds` only reference `BOUNDARY_NON_INTERRUPTIVE_TIMER` or
      `BOUNDARY_ERROR_HANDLER` nodes.
- [ ] `EXTERNAL_TASK.boundaryEventIds` only reference `BOUNDARY_INTERRUPTIVE_TIMER`,
      `BOUNDARY_NON_INTERRUPTIVE_TIMER`, or `BOUNDARY_INTERRUPTIVE_CATCH_EVENT` nodes (no error handler) — this
      host used to have zero validation, now it does.
- [ ] `EXCLUSIVE_GATEWAY.providerType` is `BEAN` or `VARIABLE` (never null/missing).
- [ ] If `providerType: BEAN` → `providerBean` non-blank and resolves to a registered `AnswerProvider` bean.
- [ ] If `providerType: VARIABLE` → `providerVariable` non-blank.
- [ ] An `EXCLUSIVE_GATEWAY` has **at least one** `outgoing` entry (`KIKWI-021`), **at most one** with
      `isDefault: true`, **at most one** with `handlesNull: true` (`KIKWI-019`), and no two edges sharing the
      same `expectedAnswer` (`KIKWI-020`).
- [ ] `PARALLEL_GATEWAY.targetJoinId` is non-blank (`KIKWI-024`), resolves to an existing node (`KIKWI-025`),
      and that node is a `JOIN_GATEWAY` (`KIKWI-026`).
- [ ] `CALL_ACTIVITY_COORDINATOR.calledElement` is non-blank.
- [ ] `CALL_ACTIVITY_COORDINATOR.elementVariable`, if present, has `collectionVariable` also present.
- [ ] `CALL_ACTIVITY_COORDINATOR.boundaryEventIds` only reference `BOUNDARY_INTERRUPTIVE_TIMER` or
      `BOUNDARY_NON_INTERRUPTIVE_TIMER` nodes (no error handler, no catch event).
- [ ] `EVENT_CATCHER`/`EVENT_THROWER`/`BOUNDARY_INTERRUPTIVE_CATCH_EVENT`: `providerType` is set, and the field
      required by that type is filled (`staticKey`/`providerVariable`/`providerBean` resolving to a real
      `CorrelationKeysProvider`/`correlationTemplates` non-empty).
- [ ] `EVENT_CATCHER` never combines `catchType: GROUP` with `providerType: STATIC`.
- [ ] `EVENT_CATCHER.boundaryEventIds` only reference `BOUNDARY_INTERRUPTIVE_TIMER` or
      `BOUNDARY_NON_INTERRUPTIVE_TIMER` nodes.
- [ ] `BOUNDARY_INTERRUPTIVE_CATCH_EVENT.attachedToRef` points to a node that is `EXTERNAL_TASK` or
      `TIMER_TASK` (never `EXECUTABLE_TASK`, never anything else).
- [ ] `TIMER_TASK.boundaryEventIds` only reference `BOUNDARY_INTERRUPTIVE_TIMER`,
      `BOUNDARY_NON_INTERRUPTIVE_TIMER`, or `BOUNDARY_INTERRUPTIVE_CATCH_EVENT` nodes (no error handler).
- [ ] `BOUNDARY_INTERRUPTIVE_TIMER.providerType` is set, and the matching field is filled
      (`staticValue`/`providerVariable`/`providerBean`). (`KIKWI-033`–`036`) — note this is **not** (yet)
      enforced for `TIMER_TASK`'s own provider fields, only the boundary variant; still get `TIMER_TASK` right,
      it's just not deploy-blocking.
- [ ] `BOUNDARY_NON_INTERRUPTIVE_TIMER.schedulePolicy` is present, with `type` set (`KIKWI-037`) and the field
      that `type` needs filled (`expression` for `RATE_DURATION`, non-empty `fixedDates` for `FIXED_DATES`).
      (`KIKWI-039`/`040`)
- [ ] No two `BOUNDARY_ERROR_HANDLER` on the same parent share an `errorCode`, and at most one wildcard
      (no `errorCode`) is allowed per parent. (`KIKWI-042`)
- [ ] `retryPolicy`, if present, has `maxRetries >= 1` explicit (`KIKWI-043`), a real `strategy`
      (`LINEAR`/`EXPONENTIAL_BACKOFF`, `KIKWI-044`), and `initialInterval` if `strategy: EXPONENTIAL_BACKOFF`
      (`KIKWI-045`).

### B — Not enforced today, but still get it right

- [ ] `defaultStartPoint` resolves to a node that is actually a `DEFAULT_START_EVENT` — the engine doesn't
      care about the target's type, only that it resolves (which **is** enforced, see tier A).
- [ ] Every `flowNodes` key equals the `"id"` inside that node — no longer load-bearing for the engine itself
      (it was fixed at the root to resolve everything by map key, never by the internal `id` field), but still
      worth keeping consistent for tooling/readability.
- [ ] Every `DEFAULT_END_EVENT` has empty `outgoing`.
- [ ] Every node except `EXCLUSIVE_GATEWAY`/`PARALLEL_GATEWAY` has at most one `outgoing` entry.
- [ ] A branch node's `outgoing` pointing directly at the `JOIN_GATEWAY` closing its own `PARALLEL_GATEWAY` is
      the **normal, correct** way to end a branch (same shortcut as ending a branch with a `DEFAULT_END_EVENT`)
      — don't "fix" this pattern, it's not a bug.
- [ ] `TIMER_TASK`'s own `providerType` + matching value field are actually filled in — not validated at
      deploy (unlike the boundary-timer variant above), fails when the timer is due to fire.
- [ ] `BOUNDARY_ERROR_HANDLER.attachedToRef` points to an `EXECUTABLE_TASK` (the only host it makes semantic
      sense on, and the only one the boundaryEventIds allowlist accepts it on) — not independently re-checked
      from the handler's own side.
- [ ] Every node in `flowNodes` is reachable from `defaultStartPoint` — no orphaned nodes nothing points to.
- [ ] Every `executor`/`providerBean` either matches a real bean found in Step 4, or is listed as a component
      to implement in the delivery (Step 7) — never a silent dangling reference.

---

## Step 7 — What to deliver

1. The `<process-key>.kikwi` file.
2. **Components to implement** — a short list of any `executor`/`providerBean`/`providerVariable` values that
   don't yet correspond to real code, each with: the bean name used, the interface it needs to implement
   (`TaskHandler`/`AnswerProvider`/`DueDateProvider`/`CorrelationKeysProvider`), and which node(s) reference it.
   Empty list is fine and worth stating explicitly ("everything resolved against existing beans").
3. **Assumptions and open questions** — every load-bearing gap from Step 1 that you resolved with a
   conservative default instead of getting confirmation, and anything you *did* ask about but is still
   unresolved. Be explicit that these are guesses, not requirements extracted from the spec.
4. If anything in the model relies on a **tier-B** item from Step 6 (unenforced at deploy), call it out — it's
   the kind of thing that passes a smoke-test deploy and then breaks the first time a real instance exercises
   that path.
5. A one-paragraph summary: node count, types used, and any simplification made versus the literal spec text.

---

## Compact reference example

A short "request needs review, with an SLA that escalates it" shape — enough to show format + layout math
without repeating a full end-to-end process. Use it as a formatting template, not content to copy; the actual
flow, names, and traceability always come from the real spec.

```json
{
  "key": "expense-approval",
  "name": "Expense Approval",
  "description": "An expense report is validated, then routed for manager approval; if there's no response within 48h, it escalates to finance.",
  "sla": "",
  "defaultStartPoint": "START",
  "flowNodes": {
    "START": {
      "id": "START", "name": "Expense Submitted", "type": "DEFAULT_START_EVENT", "description": "",
      "commitBefore": false, "commitAfter": false,
      "outgoing": [{ "id": "flow-1", "name": "", "description": "", "targetNodeId": "VALIDATE", "isDefault": false, "handlesNull": false }],
      "extensionProperties": {}, "layout": { "x": 0, "y": 0 }
    },
    "VALIDATE": {
      "id": "VALIDATE", "name": "Validate Expense Data", "type": "EXECUTABLE_TASK", "description": "",
      "executor": "expenseValidationTaskHandler",
      "commitBefore": false, "commitAfter": false,
      "outgoing": [{ "id": "flow-2", "name": "", "description": "", "targetNodeId": "AWAIT_APPROVAL", "isDefault": false, "handlesNull": false }],
      "extensionProperties": {}, "layout": { "x": 380, "y": 0 }
    },
    "AWAIT_APPROVAL": {
      "id": "AWAIT_APPROVAL", "name": "Await Manager Approval", "type": "EXTERNAL_TASK", "description": "",
      "commitBefore": true, "commitAfter": false,
      "outgoing": [{ "id": "flow-3", "name": "", "description": "", "targetNodeId": "APPROVED", "isDefault": false, "handlesNull": false }],
      "boundaryEventIds": ["ESCALATE_TIMER"],
      "extensionProperties": {}, "layout": { "x": 740, "y": 0 }
    },
    "ESCALATE_TIMER": {
      "id": "ESCALATE_TIMER", "name": "48h No Response", "type": "BOUNDARY_INTERRUPTIVE_TIMER", "description": "",
      "attachedToRef": "AWAIT_APPROVAL", "providerType": "STATIC", "staticValue": "PT48H",
      "commitBefore": false, "commitAfter": false,
      "outgoing": [{ "id": "flow-4", "name": "", "description": "", "targetNodeId": "AWAIT_FINANCE_REVIEW", "isDefault": false, "handlesNull": false }],
      "extensionProperties": {}, "layout": { "x": 740, "y": 200 }
    },
    "AWAIT_FINANCE_REVIEW": {
      "id": "AWAIT_FINANCE_REVIEW", "name": "Await Finance Review (Escalated)", "type": "EXTERNAL_TASK", "description": "",
      "commitBefore": false, "commitAfter": false,
      "outgoing": [{ "id": "flow-5", "name": "", "description": "", "targetNodeId": "APPROVED", "isDefault": false, "handlesNull": false }],
      "extensionProperties": {}, "layout": { "x": 1100, "y": 200 }
    },
    "APPROVED": {
      "id": "APPROVED", "name": "Expense Approved", "type": "DEFAULT_END_EVENT", "description": "",
      "commitBefore": false, "commitAfter": false, "outgoing": [],
      "extensionProperties": {}, "layout": { "x": 1500, "y": 100 }
    }
  },
  "extensionProperties": {}
}
```

Note the open gap this example would flag in a real delivery (Step 7): the spec fragment above never says what
happens if the *manager* explicitly rejects the expense (only "no response" is handled, via the timer) — that's
exactly the kind of load-bearing ambiguity to surface rather than silently omit or silently invent a rejection
path.

## Where the ground truth lives

This file's field names and Step 6/tier-A checklist were transcribed directly from `kikwi-model`'s
`record`s (`io.kikwiflow.model.definition.process.elements.*`) and `kikwi-core`'s
`io.kikwiflow.validation.DeployValidator`, not from prose documentation — the engine's own docs under `docs/`
are known to drift ahead of what's actually implemented. If the Kikwiflow engine repository is available for
cross-checking (it usually won't be, from inside a downstream project), those two source locations are the
authoritative tie-breaker over anything written here, including this file.
