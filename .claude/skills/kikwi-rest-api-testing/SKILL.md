---
name: kikwi-rest-api-testing
description: >
  Field guide to the Kikwiflow Management REST API (kikwi-management-rest) for agents working inside a project
  that *embeds* Kikwiflow as a dependency (not the engine repo itself) and needs to write tests, curl scripts,
  or assertions against a running instance — deploy a process, start/drive instances, complete external tasks,
  inspect incidents, search, and read event history over HTTP. Trigger this skill when asked to test, verify,
  smoke-check, or write integration assertions against a Kikwiflow-backed service's REST endpoints. Do not use
  it to test the engine in-process (JUnit tests inside the engine repo use `AssertableKikwiEngine` instead —
  out of scope here) or to author process JSON itself (see the process-authoring guide/docs of the target
  project for that).
---

# Skill: Testing a Kikwiflow app via its Management REST API

## Scope

This skill is for a **downstream project** — an application that has `kikwi-management-rest-spring-boot-starter`
(or an equivalent starter) on its classpath and exposes a running `KikwiflowEngine` over HTTP. The goal is
always the same shape of task: drive a process instance through HTTP calls and assert on the JSON that comes
back, the same way a human QA engineer would with curl/Postman, but scripted.

**Not this skill's job:**
- Testing the Kikwiflow *engine* itself from inside its own repo — that uses `SingletonsFactory` /
  `AssertableKikwiEngine` in `kikwi-core-tests`, entirely in-process, no HTTP involved.
- Designing or validating process JSON definitions — this skill assumes a definition already exists (or is
  handed to you) and treats it as a black box reachable by `processDefinitionKey`.

## Before writing any request

1. **Find the base path.** All endpoints below are relative to `kikwiflow.rest.base-path`
   (`application.yml`/`.properties`), default `/kikwiflow/api/v1`. Grep the target project's config before
   assuming the default holds.
2. **Confirm both halves of the API are actually registered.** Command controllers (deploy/start/claim/
   complete/retry/correlate) need a `KikwiflowEngine` bean in context; query controllers (`GET`s, `/search`,
   `/pulse/*`, `/events`) need a `QueryRepository` bean. In most Spring Boot apps both come free from the same
   persistence starter, but if one is missing, its endpoints 404 with "no handler" — not a Kikwiflow error at
   all — and you'll waste time debugging the wrong layer.
3. **Check `kikwiflow.security.deploy-enabled`.** Default is `false`. If you need `POST /process-definitions`
   for a test (dynamic deploy) and it's off, either ask whether a test profile enables it, or deploy through
   whatever classpath auto-deploy mechanism the project already uses instead of fighting the flag.
4. **Check history flags if you need `GET /process-instances/{id}/events`.** Two independent switches:
   `kikwiflow.outbox.events-enabled` (default `false` — whether data is ever written) and
   `kikwiflow.history.enabled` (default `true` — whether the endpoint exists at all). Both must be on, or
   you'll get an empty list / 404 that has nothing to do with your test being wrong.
5. **Don't assume auth headers.** Kikwiflow ships no auth scheme. Unless the target app registers its own
   `HttpIdentityResolver`, every request is anonymous (`actorId: "anonymous"`, `roles: []`) and `tenantId` is
   read raw from an `X-Tenant-Id` header if your test needs to scope by tenant. Check the project for a custom
   resolver before inventing a bearer token that doesn't exist.

## Global wire conventions (apply to every endpoint below)

- Plain JSON in/out, no envelope — a success response *is* the resource (or an array of them), never
  `{ data: ... }`.
- **Process variables** are always a map, never a bare value:
  ```json
  { "variables": { "approved": { "name": "approved", "value": true, "isTransient": false } } }
  ```
  The map key and the inner `name` must match — this is the #1 way a hand-written test payload fails silently
  or 500s. `isTransient` is optional, defaults `false`.
- **Paginated** endpoints (`POST /process-instances/search`, deprecated `GET /process-instances/summary`)
  return `{ content, totalElements, totalPages, page, size }`. `page` is 0-based. `size` silently caps at
  **100** — sending 500 doesn't error, it just gets clamped, so don't assert on a `size` you sent above 100.
- **Errors** that Kikwiflow itself recognizes come back as `{ "code": "...", "message": "..." }`:

  | `code` | HTTP | When |
  |---|---|---|
  | `NOT_FOUND` | 404 | Instance/definition/task/incident id doesn't exist |
  | `NOT_IMPLEMENTED` | 501 | A filter param the endpoint's signature accepts but doesn't actually support yet |
  | `CONFLICT` | 409 | Invalid for current state (e.g. retrying a non-`OPEN` incident) |
  | `BAD_REQUEST` | 400 | Domain validation rejected input (e.g. `orderBy` outside the whitelist) |

  **But two important cases skip this envelope entirely and come back as a raw framework 500**, so don't assert
  `code`/`message` on them: (a) `POST /process-definitions` while `deploy-enabled` is `false` — throws
  `SecurityException` with no handler; (b) deploying a structurally invalid definition — throws
  `InvalidProcessDefinitionException`, also no handler. Also: **there is no `@Valid`/Bean Validation** on
  request bodies anywhere — a missing required field (e.g. `processDefinitionKey` omitted on start) doesn't
  fail fast with 400, it falls through to whatever the engine does with `null`, which can be an unhelpful 500.
  If a test needs to assert "bad input is rejected cleanly," verify the real behavior first instead of assuming
  a 400.
- **Synchronous-until-the-next-wait-point.** `POST /process-instances` (start), `POST
  /external-tasks/{id}/complete`, and `POST /events/correlate/{key}` all advance the flow **inside the same
  HTTP call** as far as the engine can go synchronously — you get back the already-advanced `ProcessInstance`,
  no polling needed to observe *that* step. But if the definition uses `commitBefore()` nodes further down the
  path (async continuation, picked up later by a separate task poller), state past that point is **not**
  guaranteed to exist yet when your call returns — see the polling recipe below before asserting on anything
  beyond the immediate next wait point.

## Endpoint reference

### Process definitions
| Op | Endpoint | Notes |
|---|---|---|
| Deploy | `POST /process-definitions` | 201; gated by `deploy-enabled` (see above). Same `key` redeployed → new `version`; running instances stay on their original version. |
| List | `GET /process-definitions[?key=]` | 200, array, **not deduplicated** — all versions, newest first. |
| Latest by key | `GET /process-definitions/one-by-key/{key}` | 200 or 404. One call, always the highest version — prefer this over listing+filtering client-side. |
| Exact version by id | `GET /process-definitions/{id}` | `id` is a version id, not the `key`. |
| Clear cache | `DELETE /process-definitions/cache` | 204. Needed after editing a definition directly in the DB outside the normal deploy flow. |

### Process instances
| Op | Endpoint | Notes |
|---|---|---|
| Start | `POST /process-instances` | 201, body `{ processDefinitionKey, tenant?, businessKey?, businessValue?, origin?, variables?, targetFlowNodeId? }`. `processDefinitionKey` always resolves to the latest deployed version. |
| Get by id | `GET /process-instances/{id}` | 200 or 404. |
| List | `GET /process-instances?ids=...` **or** `?process-definition-id=&tenant-id=` | Only these two filter shapes work — any other combination (including none) → 501. For anything richer, use `/search`. |
| Count | `GET /process-instances/count?process-definition-id=` | `{ "total": N }`. Counts **all** statuses, not just active — for "running now" use the pulse snapshot instead. Omitting the param → 501, not a global count. |
| Merge variables | `PUT /process-instances/{id}/variables` | 200. Upsert by name — variables not mentioned are untouched. |
| Unset variables | `PUT /process-instances/{id}/variables/unset` | body `{ "variableNames": [...] }`, 200. |
| Delete | `PUT /process-instances/{id}` | **204, no body — but this is a hard delete**, not a graceful stop. Deletes the instance and its tasks; incidents and history events survive and now point at a dead id. No tenant/identity check. Use only as test teardown, and don't rely on it for anything you'd call "cancel." |
| Incidents of instance | `GET /process-instances/{id}/incidents` | 200, unpaginated array. |
| Snapshot | `GET /process-instances/{id}/snapshot` | 200: `{ instance, executableTasks, externalTasks, incidents, eventCatcherWaitStatus }` in one call — the cheapest way to assert a lot at once instead of four separate requests. 404 only if the instance itself doesn't exist; empty sub-lists are normal, not an error. |

### Advanced search — `POST /process-instances/search`
The rich query endpoint; every field optional, all combined with AND (never OR).

| Field | Notes |
|---|---|
| `processDefinitionKeys` | **Prefer this** over `processDefinitionId(s)` — covers all versions of a key. |
| `activeNodeId` | Instances currently waiting at exactly this node id. |
| `parentInstanceId` | Direct children of a `CALL_ACTIVITY_COORDINATOR` instance. |
| `statuses` | `ACTIVE` / `COMPLETED` / `CANCELLED`. |
| `tenantId(s)`, `businessKey(s)` | Exact match. |
| `startedAfter`/`startedBefore` | ISO-8601 UTC, `Z` suffix. |
| `variables` | Map key→value, **exact match only** (Mongo impl does `variables.<key>.value` equality — no partial/substring search). |
| `variablesExist` | Array of variable names that must be present, any value. |
| `orderBy` | Whitelist `id,businessKey,status,processDefinitionId,startedAt,endedAt` — anything else is **400**, not silently ignored. |
| `page`/`size` | `size` caps silently at 100. |

Response is a `PageResult` of `ProcessInstanceSummary` — a **projection** (no `variables`, no task maps). Chain
into `GET /process-instances/{id}` or `.../snapshot` for full content on a specific hit.

### External tasks
| Op | Endpoint | Notes |
|---|---|---|
| Claim | `PUT /external-tasks/{id}/claim/{assignee}` | 204. Purely advisory — `complete` works on an unclaimed task too. |
| Unclaim | `PUT /external-tasks/{id}/unclaim` | 204. |
| Complete | `POST /external-tasks/{id}/complete` | body `{ "variables": {...} }`, **202** with the advanced `ProcessInstance`. `tenant`/`targetFlowNodeId` in the request body are accepted but **silently ignored** — the controller doesn't forward them to the engine. |
| Get by id | `GET /external-tasks/{id}` | 200 or 404. |
| List / Count | `GET /external-tasks?...` / `GET /external-tasks/count?...` | Same 7 query params accepted by both, but **different subsets actually implemented** — an unsupported one is 501, not ignored. `process-instance-id` works on list but 501s on count. `process-instance-id-in`, `task-definition-id`, `tenant-ids` are 501 on **both** today. Only `process-definition-id`, `tenant-id`, `assignee` are safe on both. |

### Incidents
| Op | Endpoint | Notes |
|---|---|---|
| Get by id | `GET /incidents/{id}` | 200 (`type`, `message`, `stackTrace`, `taskDefinitionId`, `executionId`, `status: OPEN|RESOLVED`) or 404. |
| Retry | `PUT /incidents/{id}/retry` | No body, 204. Resets the `ExecutableTask` to `PENDING` with retries restored to the node's `retryPolicy` max (or 3 if unset — a fixed default, no config knob), marks incident `RESOLVED`, atomically. 404 if the incident's `ExecutableTask` no longer exists (e.g. instance was deleted); **409** if the incident isn't `OPEN` anymore. |

### Event correlation — `POST /events/correlate/{correlationKey}`
Body `{ "variables": {...} }` (optional), **202** with the advanced `ProcessInstance` — same synchronous-advance
model as completing an external task. **404** for every "nobody's waiting on this key" case (never existed,
already consumed, an `ANY`-group race already decided by another key, cancelled by a boundary, wrong tenant) —
all collapse into the same 404, so don't try to distinguish them from the response. Resending an already-consumed
key is a safe, idempotent-by-construction 404 — useful for webhook retries.

### Event history — `GET /process-instances/{id}/events`
200 with `HistoryEventSummary[]`, ascending by `timestamp`. Empty array (not an error) if the outbox was never
enabled for that instance or it just hasn't produced events yet. 404 only if the instance id itself doesn't
exist — and note deleting the instance (`PUT /process-instances/{id}`) does **not** delete its history, so this
endpoint can outlive the instance it describes. `eventType` catalog: `FLOW_NODE_FINISHED`,
`GATEWAY_ANSWER_RESOLVED`, `PROCESS_INSTANCE_STARTED`, `PROCESS_INSTANCE_FINISHED`, `INCIDENT_CREATED`,
`INCIDENT_RESOLVED`, `EXTERNAL_TASK_CLAIMED`, `EXTERNAL_TASK_UNCLAIMED`, `EXTERNAL_TASK_COMPLETED`,
`RETRY_SCHEDULED`, `PROCESS_VARIABLE_CHANGED`, `TIMER_FIRED`, `ORPHANED_CHILD_COMPLETION`. Requires both
`kikwiflow.outbox.events-enabled` and `kikwiflow.history.enabled` (see setup checklist).

### Stats (Pulse)
| Op | Endpoint | Notes |
|---|---|---|
| Snapshot | `GET /pulse/process-definition/{processDefinitionId}/snapshot` | `processDefinitionId` is a specific version id. `metrics.running` = active **right now**; `metrics.fail` = open incidents right now; `metrics.sla` is **hardcoded `100.0`** — never assert real SLA math against it. Per-node `metrics` is populated only for `EXECUTABLE_TASK`/`EXTERNAL_TASK`/`EVENT_CATCHER`/`CALL_ACTIVITY_COORDINATOR`/`TIMER_TASK`; `null` for gateways/events/boundaries by design. |
| Stream | `.../snapshot/stream` (SSE) | Re-pushes the same object every `kikwiflow.pulse.sse-endpoints.interval` (default 5000ms) while connected. One shared poll loop per definition id server-side, not per connection. |

## Testing recipes

### 1. Happy-path E2E via curl/bash
```bash
BASE=http://localhost:8080/kikwiflow/api/v1

curl -s -X POST "$BASE/process-instances" -H 'Content-Type: application/json' -d '{
  "processDefinitionKey": "onboarding-customer",
  "businessKey": "test-'"$(date +%s)"'",
  "variables": { "email": { "name": "email", "value": "test@example.com" } }
}' | tee /tmp/instance.json

INSTANCE_ID=$(jq -r .id /tmp/instance.json)

# assert it's waiting at the expected node
jq -r '.activeNodes' /tmp/instance.json   # expect the node id you designed the test around

# drive the external task it's parked on
TASK_ID=$(curl -s "$BASE/external-tasks?process-instance-id=$INSTANCE_ID" | jq -r '.[0].id')
curl -s -X POST "$BASE/external-tasks/$TASK_ID/complete" -H 'Content-Type: application/json' \
  -d '{ "variables": { "approved": { "name": "approved", "value": true } } }'

curl -s "$BASE/process-instances/$INSTANCE_ID" | jq -r .status   # expect COMPLETED, or the next node
```

### 2. Poll-until pattern (for anything past an async `commitBefore()` continuation)
Don't assert immediately after a call if the path you care about crosses a `commitBefore()` node — a background
poller, not your HTTP call, finishes that step. Poll `GET /process-instances/{id}` (or the snapshot) on a short
interval with a timeout instead of a fixed `sleep`:
```bash
for i in $(seq 1 20); do
  STATUS=$(curl -s "$BASE/process-instances/$INSTANCE_ID" | jq -r .status)
  [ "$STATUS" = "COMPLETED" ] && break
  sleep 0.5
done
```

### 3. Asserting on an incident and its recovery
1. Drive the instance into the failing task (however the target project simulates a downstream failure).
2. `GET /process-instances/{id}/incidents`, assert `status: OPEN` and inspect `type`/`message` for the expected
   failure cause.
3. Fix the underlying condition (e.g. flip a stub back to succeeding), `PUT /incidents/{id}/retry` (204).
4. Re-fetch the incident (now `RESOLVED`) or the instance (now past that node) to confirm recovery.

### 4. Prefer `/search` over scanning
When a test needs "find the instance(s) matching X" rather than "I already have the id," use `POST
/process-instances/search` with `processDefinitionKeys` + `variables`/`businessKey`/`statuses` instead of
listing everything and filtering client-side — it's the only endpoint with real filtering, and using it also
exercises the same contract your application's own tooling will rely on.

### 5. Cleanup
`PUT /process-instances/{id}` is a real delete, not a soft-stop — safe to use as teardown for instances your
test created, but don't reach for it as a "cancel" verb in an assertion (it doesn't produce a `CANCELLED`
status; the instance just stops existing).

## Where the ground truth lives

This file is a condensed field guide, current as of the Kikwiflow version it was written against — it can drift
from an updated engine. If the Kikwiflow engine repository itself is available for cross-checking, the
authoritative sources are `docs/apis/*.md` (the full prose reference this skill was distilled from) and the
controllers under `kikwi-management-rest/src/main/java/io/kikwiflow/management/controller/`. When in doubt
about a specific status code or field, that source wins over this file.
