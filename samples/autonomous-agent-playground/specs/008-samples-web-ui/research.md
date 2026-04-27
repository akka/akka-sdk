# Phase 0 Research — Web UI for Autonomous Agent Samples

**Feature**: 008-samples-web-ui
**Date**: 2026-04-27

This document resolves the unknowns identified in `plan.md`'s Technical Context and Constitution Check. Each finding records the **decision**, the **rationale**, and the **alternatives considered**.

---

## R-1 — Serving the static UI from the SDK with an SPA-style fallback

**Decision**: Place all UI assets under `src/main/resources/static-resources/`. Serve them from a new `PlaygroundUiEndpoint` using two routes on the same class:

1. `@Get("/playground/static/**")` — serves any asset under `static-resources/playground/static/...` via `HttpResponses.staticResource(request, "/playground/static/")`.
2. `@Get("/playground/**")` — catch-all that returns the same `index.html` for any path under `/playground` that isn't a known asset, so the client-side router can pick up `<sample>` and `run/<runId>` segments.

A separate `@Get("/")` returns a small redirect to `/playground` so the playground service's root URL lands the user on the UI.

**Rationale**: The SDK's `HttpResponses.staticResource(request, prefix)` is the canonical pattern for serving an asset tree (`akka-context/sdk/http-endpoints.html.md` lines 474–514). The SDK does not ship a built-in single-page-app helper, but a catch-all `@Get("/playground/**")` returning `index.html` is the standard idiom (also used in the `doc-snippets` sample's `StaticResourcesEndpoint`). ACLs declared at the endpoint class level apply to both routes, so a single `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))` covers them.

**Alternatives considered**:
- Per-asset `@Get` methods: verbose and brittle when the file tree changes.
- Serving from an external CDN: violates FR-018 ("served by the playground service itself") and the Simplicity gate.
- Akka HTTP low-level `HttpResponse.create()` with manual MIME mapping: needless complexity when `staticResource()` does the mapping.

**Citations**:
- `akka-context/sdk/http-endpoints.html.md` §"Serving static content" (lines 474–514).
- `akka-context/sdk/access-control.html.md` lines 36–50 (class-level ACLs apply to all routes).

---

## R-2 — Bridging `notificationStream()` to the browser as Server-Sent Events

**Decision**: Each in-progress run subscribes via a new `RunNotificationEndpoint` route:

```text
GET /playground/api/runs/{runId}/events  →  text/event-stream
```

Internally, the handler loads the `Source<Notification, NotUsed>` from `componentClient.forAutonomousAgent(<class>, instanceId).notificationStream()`, transforms each `Notification` into a small `EventEnvelope` DTO (see `data-model.md`), and returns the source to the browser via `HttpResponses.serverSentEvents(source)`. No explicit `Materializer` is needed — the SDK handles materialization inside the response builder. The endpoint passes a `Function<EventEnvelope, String> extractEventId` so the SDK populates the SSE `id:` field for built-in `Last-Event-ID` reconnect.

**Rationale**: The SDK directly supports this pattern. `AutonomousAgentClient#notificationStream()` returns `Source<Notification, NotUsed>` (`akka.javasdk.client.AutonomousAgentClient`), and `HttpResponses#serverSentEvents(Source<T, ?>)` and `HttpResponses#serverSentEvents(Source<T, ?>, Function<T, String>)` are the documented bridges (`akka-context/sdk/http-endpoints.html.md` lines 562–601). The SDK's HTTP runtime emits 5-second keep-alive frames automatically, which prevents intermediate proxies from killing the stream and aligns with FR-009b (the panel must show "live updates unavailable" — a missed keep-alive is the trigger).

The `Notification` sealed interface and its sub-types serialize via Jackson without custom configuration. We will still introduce a small `EventEnvelope` record (see data-model.md) so the wire shape is stable across SDK changes and so the agent identifier policy from FR-010a (role + short suffix when needed) is computed server-side from raw `componentId` / `instanceId`.

**Alternatives considered**:
- WebSocket: bidirectional, but the live event flow is one-way; SSE is enough and simpler.
- Long-polling: works without WebSockets but adds latency and per-poll allocation.
- Custom HTTP chunked encoding: re-implements what `HttpResponses.serverSentEvents` already provides.

**Citations**:
- `akka.javasdk.client.AutonomousAgentClient#notificationStream` (signature in SDK source).
- `akka.javasdk.http.HttpResponses#serverSentEvents` overloads.
- `akka-context/sdk/autonomous-agents.html.md` "Agent notifications" (lines 467–612 for the full taxonomy).
- `akka-context/sdk/http-endpoints.html.md` §"Server-Sent Events" (lines 562–735).

---

## R-3 — Mapping a run to the agent instance(s) we must subscribe to

**Decision**: Modify each existing per-sample POST response to additionally return the **owning agent's component id and instance id** alongside the existing task id(s). The UI's run identifier is the owning agent's `instanceId` (a UUID, already globally unique). On every SSE subscription the backend looks up the owning agent's `componentId` server-side from a static **AgentRegistry** (a hand-coded enum-backed map of `componentId → AutonomousAgent class`) so it can call `componentClient.forAutonomousAgent(<class>, runId).notificationStream()`. The owning agent's stream is the *only* stream the UI subscribes to per run.

For multi-agent samples (research, debate, peerreview, devteam, negotiation), the owning agent's stream already carries enough information for the UI's purposes: `Notification.DelegationStarted/Resolved`, `Notification.TeamCreated/MemberReady/MemberStopped/Disbanded`, and `Notification.ConversationCreated/ParticipantReady/Ended` arrive on the *parent's* stream with the parallel `componentIds` / `instanceIds` lists for the children. The UI does *not* subscribe to children's streams in this MVP; it surfaces "Researcher started", "Researcher completed" via the parent's emitted notifications. (See "Edge cases / what is missed" below.)

**Why a backend change is needed**: The current per-sample endpoints generate `var agentInstanceId = UUID.randomUUID().toString()` locally and return only the task id (verified by reading `QuestionEndpoint`, `ResearchEndpoint`, `DebateEndpoint`, etc.). `TaskSnapshot` exposes only `status()`, `result()`, and `failureReason()` — there is no `assignee()` accessor. Without exposing the instance id at submission time, the UI has no way to subscribe. The required change is one extra field on the existing response record per sample (see contracts/run-control.md and tasks.md). This is a minor, additive contract change that does not redesign the endpoints, consistent with the spec's Assumption that existing endpoints are "sufficient as the contract the UI submits and reads against". The original Assumption is updated in `plan.md` Technical Context.

**Edge cases / what is missed under this scheme**:
- *Worker-side details*: a worker emits `WorkerTaskReceived` and `WorkerTaskCompleted` on its own stream; only the parent's `DelegationStarted/Resolved` (with aggregate counts and id lists) reach the UI. For the demo intent this is enough — viewers see "delegated to Researcher and Analyst" and later "delegation resolved: 2 succeeded, 0 failed".
- *Request-based delegations*: `DelegationStarted.subtaskIds()` and `workerInstanceIds()` are empty for request-based delegations; only the count is authoritative. The UI shows the count and a "(per-subtask details unavailable)" footnote.
- *Conversation messages*: peer-to-peer `MessageReceived` events on members' streams are not surfaced. The UI shows turn boundaries via `ConversationTurnReceived` if the parent emits them, otherwise high-level lifecycle only.
- *Child failures during a parent's run*: surfaced through `DelegationResolved.failedSubtaskIds()` rather than per-child `TaskFailed` events.

**Alternatives considered**:
- Subscribe to children dynamically as `DelegationStarted` arrives: adds connection sprawl per-run (one SSE upstream per child), requires the AgentRegistry to map every component id, and brings little new information for a demo UI. Defer to a follow-up if we later want a "drill into worker" view.
- Server-side mapping table from `taskId → instanceId`: requires a new entity/store, fights the spec's "no extra persistence" Out-of-Scope item, and provides nothing the existing per-sample POST cannot expose directly.
- Inferring instance id from the `TaskAssigned` notification stream only: chicken-and-egg — you cannot subscribe to the instance's stream until you know the instance id.

**Citations**:
- `akka-context/sdk/autonomous-agents.html.md` lines 303–308 (TaskSnapshot fields), 467–612 (full notification taxonomy with parallel `componentIds`/`instanceIds`), 534–543 (DelegationStarted / DelegationResolved field shapes), 545–581 (TeamCreated / ConversationCreated).
- Source: `src/main/java/demo/helloworld/api/QuestionEndpoint.java`, `src/main/java/demo/research/api/ResearchEndpoint.java`, `src/main/java/demo/debate/api/DebateEndpoint.java`, `src/main/java/demo/pipeline/api/PipelineEndpoint.java` — confirm the current "taskId only" return shape.

---

## R-4 — Operator stop, 404, and querying current run state

**Decision**:

- **Operator stop**: `componentClient.forAutonomousAgent(<class>, runId).stop()`. This emits a `Notification.Stopped` with `reason() == "operator"`, which the SSE stream forwards to the UI. **Caveat**: `.stop()` does not auto-cancel an in-flight task — the task remains in `IN_PROGRESS` until it terminates naturally or is explicitly cancelled. For the UI's purposes, the *run* is shown as cancelled the moment the `Stopped` event arrives; the underlying task may converge to its terminal state slightly later. The UI treats `Stopped(reason="operator")` as the run-level terminal signal and re-fetches the task status to surface the final task result/state (FR-009a behavior).
- **404**: `throw HttpException.notFound("…")` from any endpoint method (or `return HttpResponses.notFound()` for the static-asset path). This is the canonical SDK pattern (`akka-context/sdk/http-endpoints.html.md` §"Error responses"). Used for FR-008b and for `RunNotificationEndpoint` when the run id has no associated agent instance.
- **Querying current state on URL load**: on page load (e.g., a shared run URL), the UI calls a new `GET /playground/api/runs/{runId}/status` endpoint that returns `{ sample, taskId, taskStatus, agentPhase, finalResult?, failureReason? }`. Internally:
  - `componentClient.forAutonomousAgent(<class>, runId).getState()` → `phase()` (`PHASE_RUNNING` / `PHASE_STOPPED` / etc.), `paused()`, `currentTask()`, `pendingTaskIds()`.
  - `componentClient.forTask(taskId).get(<TaskDef>)` → `status()` (PENDING/ASSIGNED/IN_PROGRESS/COMPLETED/FAILED/CANCELLED), `result()`, `failureReason()`.
  - The endpoint synthesises a single status DTO so the UI does not need two round-trips on every load.

**Rationale**: All three are documented SDK affordances. Centralising the status DTO server-side keeps the UI client small and lets us evolve the source data without breaking the client contract.

**Alternatives considered**:
- Letting the UI directly call the existing per-sample `GET /<sample>/{taskId}` endpoints: works but bypasses the run-id-as-URL design and re-introduces sample-shape coupling in the UI's status path. Better to centralise.
- Sending stop via a different SDK affordance (e.g. cancelling the task): the spec calls for `reason="operator"` and the documented mechanism for that is `.stop()`.

**Citations**:
- `akka-context/sdk/autonomous-agents.html.md` lines 408–412 (`stop()`), 434–465 (`getState()` shape), 299–307 (TaskSnapshot via `forTask().get()`), Notification.Stopped.reason() at line 507.
- `akka-context/sdk/http-endpoints.html.md` lines 179–206 (`HttpException.notFound` / `HttpResponses.notFound`).

---

## R-5 — Akka default style + theme toggle mechanism

**Decision**: Copy `akka-context/ui/default-akka-style.css` verbatim into `src/main/resources/static-resources/playground/static/styles/akka.css`. Add a small sibling stylesheet `playground.css` (estimated under 200 lines) with two responsibilities:

1. `[data-theme="light"]` and `[data-theme="dark"]` selectors that re-apply the stylesheet's existing custom-property values, so a class-based override beats `prefers-color-scheme`. `[data-theme="platform"]` (or absence of the attribute) leaves the OS preference in charge.
2. Feature-specific UI primitives the base sheet does not cover: three-tier event row colors (uses the existing `--ok` / `--warn` / `--err` CSS vars), the run-summary line, the stuck-run banner, the "live updates unavailable" indicator.

Theme is applied by setting `<html data-theme="light|dark|platform">`. A small `theme.js` module reads the stored preference from `localStorage` (key `playground-theme`) on page load before any rendering — preventing the flash-of-wrong-theme called out in SC-008 — and writes it back when the user changes the toggle.

**Rationale**: The base stylesheet is self-contained (no `@import`, no remote fonts, no relative asset paths) and uses CSS custom properties throughout, which makes a class-based override clean to layer. Avoiding any modification to the imported file keeps the canonical Akka style as the single source of truth and makes future updates a drop-in replacement.

**CSS custom properties we will rely on** (verbatim names in the imported sheet):

- Brand: `--primary`, `--secondary`
- Status: `--ok`, `--warn`, `--err`  *(maps directly onto healthy / struggle / terminal-failure tiers in FR-011)*
- Surfaces: `--surface-1`, `--surface-2`, `--surface-3`, `--surface-4`
- Text & structure: `--bg`, `--fg`, `--divider`, `--radius`
- Type: `--font-sans`, `--font-mono`

**Existing primitives reused**: button, input/textarea/select, badge (`[data-badge]`), table, native `<dialog>` (for the stop-confirmation dialog from FR-013a), progress bar, spinner (`[aria-busy=true]`), tooltip (`[data-tooltip]`), switch (`input[type=checkbox][role=switch]`).

**Alternatives considered**:
- Edit `default-akka-style.css` in place to add class-based overrides: pollutes the canonical file and creates merge friction when it is updated upstream.
- Write a fresh stylesheet from scratch: violates FR-014 and the Simplicity gate.
- Put the theme attribute on `<body>` instead of `<html>`: works, but `<html>` is the conventional target and avoids a brief flash before the body class lands.

**Citations**:
- `akka-context/ui/default-akka-style.css` (verified self-contained, no external dependencies).
- Spec FR-014, FR-015, FR-016, FR-017, SC-008.

---

## Summary table

| ID | Topic | Decision | Status |
|---|---|---|---|
| R-1 | Static asset serving | `staticResource(request, prefix)` + catch-all SPA fallback | Resolved |
| R-2 | Live notification stream → SSE | `HttpResponses.serverSentEvents(notificationStream)` with `extractEventId` | Resolved |
| R-3 | run id ↔ agent instance | Modify per-sample POST responses to return `agentInstanceId`; runId == instanceId; AgentRegistry maps componentId → class | Resolved |
| R-4 | stop / 404 / status query | `.stop()` + `HttpException.notFound` + `getState()` + `forTask().get()` aggregated into one status endpoint | Resolved |
| R-5 | Akka style + theme toggle | Copy stylesheet verbatim; add `playground.css` extension keyed on `[data-theme="light\|dark\|platform"]` on `<html>` with `localStorage` persistence | Resolved |

All NEEDS CLARIFICATION items from the plan's Technical Context are now resolved.
