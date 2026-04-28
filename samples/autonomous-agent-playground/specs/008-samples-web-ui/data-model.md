# Data Model — Web UI for Autonomous Agent Samples

**Feature**: 008-samples-web-ui
**Date**: 2026-04-27

This feature introduces no new persistent state. The "data model" here covers the **wire DTOs** exchanged between the playground service and the browser, and the **in-browser model** the UI maintains for a session. All names below are illustrative and finalised in code review; field semantics and tier classification are normative.

---

## Server-side DTOs

These records live in the new `demo.ui.api` package as inner records of the relevant endpoint class (per Akka SDK convention).

### `RunRef`

Returned by every per-sample POST endpoint after this feature lands. Replaces the existing "task id only" response shape. The owning agent's `componentId` and `instanceId` let the UI subscribe to its notification stream; `taskId` is the primary task whose final result the UI fetches when the run completes.

```text
RunRef {
  String runId            // == agentInstanceId of the owning agent (UUID, globally unique)
  String sampleId         // == "helloworld" | "research" | … — the sample's short name (FR-001)
  String agentComponentId // e.g. "research-coordinator" — for SampleRegistry lookup
  String taskId           // primary task; pipeline returns multiple, see RunRef.tasks
  List<String> tasks      // all task ids created by this submit, in submission order;
                          //   used by samples like pipeline that pre-create several
}
```

Validation: all fields non-null and non-empty; `sampleId` is a known registry key; `runId` is RFC-4122 UUID format.

### `EventEnvelope`

The wire shape for every event sent over the SSE stream. Stable across SDK changes — the UI reads `tier`, `category`, `kind`, and `raw` directly without having to know every SDK Notification subtype.

```text
EventEnvelope {
  long      eventId      // monotonic per run, used as SSE id: for Last-Event-ID resumption
  Instant   timestamp    // ISO-8601 (UTC); when the bridge received the notification
  String    tier         // "healthy" | "struggle" | "terminal_failure"  (FR-011)
  String    category     // "lifecycle" | "task" | "handoff" | "delegation" |
                         //   "team" | "backlog" | "conversation" | "messaging" |
                         //   "struggle" | "other"
  String    kind         // simple class name, e.g. "TaskStarted", "DelegationStarted"
  Notification raw       // the SDK's sealed Notification subtype, serialized by Jackson
}
```

The `category` is derived from the marker sub-interface the runtime stamps on the notification. The `tier` is computed by the bridge:

| Notification kind | Tier |
|---|---|
| `IterationFailed` (when iteration retries are still possible) | `struggle` |
| `TaskResultRejected` | `struggle` |
| `TaskDependencyWait` | `struggle` |
| Any `Notification.StruggleNotification` (`TaskStruggleDetected`, `TaskDependencyStuck`, `TaskApproachingMaxIterations`, `RepeatedIterationFailure`) | `struggle` |
| `TeamMemberSetupFailed` | `terminal_failure` (for the member's slice of work) |
| `TaskFailed`, `TaskCancelled` | `terminal_failure` |
| `Stopped` with `reason() == "operator"` | `terminal_failure` (run-level) |
| `Stopped` with `reason() == "auto-stopped"` | `healthy` (graceful end of the agent's queue) |
| Anything else | `healthy` |

The classifier is a single small function in the bridge endpoint; rule changes are local.

### `RunStatus`

Returned by `GET /playground/api/runs/{runId}/status`. Synthesised server-side from `getState()` on the agent and `forTask(...).get(...)` on the primary task, so the UI does one round-trip on page load.

```text
RunStatus {
  String  runId
  String  sampleId
  String  agentComponentId
  String  taskId

  String  agentPhase       // mirrors agent's getState().phase()
  Boolean agentPaused
  String  taskStatus       // PENDING | ASSIGNED | IN_PROGRESS | COMPLETED | FAILED | CANCELLED

  String  runState         // "PENDING" | "RUNNING" | "AWAITING_INPUT" |
                           //   "COMPLETED" | "FAILED" | "CANCELLED"
                           // Computed server-side from agentPhase + taskStatus + any
                           // unassigned dependent tasks.

  Object  finalResult      // the task's typed result if taskStatus == COMPLETED, else null
  String  failureReason    // populated if taskStatus == FAILED or runState == CANCELLED
  Instant startedAt
  Instant endedAt          // null while in progress
}
```

`runState` is the user-facing roll-up driving FR-005 (panel shows status without manual refresh). The mapping is:

- All tasks COMPLETED ⇒ `COMPLETED`
- Any task FAILED ⇒ `FAILED`
- Agent stopped (`Stopped(reason="operator")`) ⇒ `CANCELLED`
- Any task PENDING and waiting on an unassigned dependent task (e.g. publishing's APPROVAL) ⇒ `AWAITING_INPUT`
- Otherwise the task is `IN_PROGRESS` or `ASSIGNED` ⇒ `RUNNING`
- Initial state before the agent activates ⇒ `PENDING`

Validation: every field's allowed values are a closed enum; mismatches are bugs in the bridge mapper, not user errors.

### `StopRequest` / `StopResponse`

```text
StopRequest  { /* empty body */ }
StopResponse { String runState; Instant stoppedAt; }
```

`StopResponse.runState` will be `CANCELLED` after the SDK's `Notification.Stopped` arrives. The endpoint returns immediately after invoking `.stop()`; the UI will see the actual `Stopped` event over SSE shortly after.

### `SampleRegistry`

A static lookup table the new endpoints use to translate a `componentId` (e.g. `"research-coordinator"`, `"debate-moderator"`) back into the concrete `Class<? extends AutonomousAgent>` needed by `componentClient.forAutonomousAgent(<class>, instanceId)`. Implemented as a final `Map<String, Class<? extends AutonomousAgent>>` in `demo.ui.api.SampleRegistry`, populated in the file alongside the agent classes — one line per sample's owning agent.

```text
SampleRegistry {
  static Class<? extends AutonomousAgent> classFor(String componentId)
      throws HttpException.notFound  // when the id is unknown
}
```

The registry only needs to cover **owning agents** (the agent each sample's POST endpoint dispatches to first). Children are not subscribed to in this MVP (see research R-3, "edge cases / what is missed").

---

## Browser-side model

### Run (in-browser)

Per visited run URL, the UI maintains:

```text
Run {
  String        runId, sampleId, agentComponentId, taskId
  String        runState              // mirrored from RunStatus.runState
  Object        finalResult           // populated when terminal
  String        failureReason         // populated on FAILED / CANCELLED
  EventEnvelope[] events              // append-only log for current panel session
  long          lastEventId           // last seen SSE id (for Last-Event-ID reconnect)
  RunSummary    summary               // see below; rebuilt from events
  ConnectionStatus connection         // "connected" | "reconnecting" | "disconnected"
                                      //   drives FR-009b indicator
  Instant       lastEventReceivedAt   // drives the 60s stuck-run notice (FR-011a)
}
```

When the user opens a new run URL, a fresh `Run` is created, populated by `GET /status`, then attached to the SSE stream.

### `RunSummary`

```text
RunSummary {
  long iterations           // count of IterationCompleted events seen
  long inputTokens          // sum of IterationCompleted.tokenUsage().input across the run
  long outputTokens         // sum of IterationCompleted.tokenUsage().output across the run
}
```

Computed in the browser from the live event stream and any historical events delivered via Last-Event-ID resume. Drives FR-012a (per-event annotation on `IterationCompleted` rows) and FR-012b (always-visible run-summary line).

### `AgentDisplay`

A small in-browser map per run, used by FR-010a's "role + suffix when multi-instance" rule:

```text
AgentDisplay {
  Map<String, Set<String>> instancesByComponentId   // componentId → seen instance ids
  String labelFor(componentId, instanceId)
    // Returns:
    //   - "<componentId>"                              when only one instance is observed
    //   - "<componentId> <first-6-of-instanceId>"     when two or more are observed
    // Re-labels existing rows when the second instance of a role first appears.
}
```

### `ThemePreference`

```text
ThemePreference {
  String mode             // "light" | "dark" | "platform"
}
```

Stored in `localStorage` under key `playground-theme`. Read synchronously by `theme.js` before any rendering to satisfy SC-008 (no flash of wrong theme). Applied by setting `document.documentElement.dataset.theme`.

### `SamplePanelDescriptor`

Per-sample static descriptor exported from `static-resources/playground/static/samples/<sample>.js`:

```text
SamplePanelDescriptor {
  String id                  // == sampleId
  String displayName         // shown in the landing list and in the panel header
  Description description    // structured per FR-002a (see below)
  InputForm inputForm        // field schema and submit handler
  ResultRenderer renderResult(finalResult, runState): HTMLElement
  OperatorActions[] actions  // 0..n, each with: when (predicate over RunStatus),
                             // label, requireConfirm, invoke(run): Promise<void>
}

Description {
  String overview            // 1–2 sentences
  String[] agents            // agent name + role line, one entry per agent
  String[] tasks             // task name → result type, one entry per task
  String flow                // a paragraph
  String demonstrates        // a paragraph
}
```

The registry (`samples/_registry.js`) maps each `sampleId` to its descriptor. Adding a new panel later means adding one module + one registry entry; no change to the shell.

---

## State transitions

### `Run.runState` (canonical run lifecycle)

```text
        ┌─────────┐
        │ PENDING │ initial; agent has not yet activated
        └────┬────┘
             ▼
         ┌─────────┐
         │ RUNNING │ agent activated; at least one task IN_PROGRESS or ASSIGNED
         └──┬───┬──┘
            │   │
            │   └────────────────────────────────────────┐
            │                                            ▼
            │                                  ┌──────────────────┐
            │                                  │ AWAITING_INPUT   │  some sample-specific
            │                                  └────────┬─────────┘  unassigned task is gating
            │                                           │
            │                                           ▼
            │                                       (back to RUNNING when
            │                                       human completes the task)
            ▼
   ┌────────────┬────────────┬────────────┐
   │ COMPLETED  │  FAILED    │ CANCELLED  │  terminal — no further transitions
   └────────────┴────────────┴────────────┘
```

`AWAITING_INPUT` is sample-specific: the publishing pipeline's APPROVAL task is the canonical example. The bridge detects this by inspecting the dependency chain of the primary task — if a dependent task is in `PENDING` and is unassigned, the run is `AWAITING_INPUT`.

### `ConnectionStatus` (browser-side)

```text
connected ──disconnect──► reconnecting ──recover──► connected
                              │
                              └──N attempts fail──► disconnected
```

`disconnected` is reached after the SSE EventSource exhausts the browser's built-in reconnect attempts. The UI surfaces FR-009b's notice from the moment we leave `connected`. On any return to `connected` we additionally fetch `GET /status` so the terminal outcome cannot be missed (FR-009a).

---

## Field-to-requirement traceability

| Spec ref | Where in the model |
|---|---|
| FR-001 (sample list) | `SampleRegistry` exposes the canonical sample list; landing page reads from `_registry.js` |
| FR-002 (per-sample panel) | `SamplePanelDescriptor` |
| FR-002a (description structure) | `SamplePanelDescriptor.description` (overview / agents / tasks / flow / demonstrates) |
| FR-003 (input shape) | `SamplePanelDescriptor.inputForm` |
| FR-005 (live status) | `RunStatus.runState` + SSE updates |
| FR-006 (result rendering) | `SamplePanelDescriptor.renderResult` |
| FR-007 (failure reason) | `RunStatus.failureReason`, `EventEnvelope.tier == terminal_failure` |
| FR-008 / FR-008a / FR-008b | `runId` is the URL key; no run id ⇒ `HttpException.notFound` |
| FR-009 / FR-009a / FR-009b | SSE stream + Last-Event-ID + `ConnectionStatus` + `GET /status` re-fetch on reconnect |
| FR-010 / FR-010a (event log + agent label policy) | `Run.events`, `AgentDisplay.labelFor` |
| FR-011 (three tiers) | `EventEnvelope.tier` |
| FR-011a (60s stuck notice) | `Run.lastEventReceivedAt` + a setInterval that watches for >60s gap |
| FR-012a / FR-012b (token + iteration display) | `RunSummary` |
| FR-013 / FR-013a (operator actions, stop) | `SamplePanelDescriptor.actions` + `StopRequest`/`StopResponse` + native `<dialog>` confirm |
| FR-015 / FR-016 / FR-017 (theme) | `ThemePreference` + `<html data-theme>` + `prefers-color-scheme` fallback |
