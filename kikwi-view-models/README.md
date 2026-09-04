# kikwi-view-models

Framework-independent adapters that project a `ProcessDefinition` into simplified, use-case-specific
view models — e.g. a `Workflow` / `WorkflowStage` structure (`WorkflowAdapter.toManualWorkflow()`) that
collapses a process graph down to its human tasks for rendering a Kanban-style board.

Depends only on `kikwi-model`. No Spring, no engine, no persistence.

## Status — not part of the v1 Community package

> **This module is experimental and is intentionally excluded from the Kikwiflow v1 Community release.**
>
> - Its API (`io.kikwiflow.view.*`) is **not stable** and may change or be removed without notice or a
>   deprecation cycle.
> - It is **not documented** in the user-facing docs and is **not covered by the v1 support/compatibility
>   guarantees**.
> - It is built and published only to unblock internal/downstream experiments (Kanban board views).
>
> Do not build production code against it yet. If you need a stable view-projection API, open an issue so it
> can be considered for a later release.
