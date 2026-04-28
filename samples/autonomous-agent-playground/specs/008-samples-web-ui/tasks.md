---

description: "Implementation tasks for the Web UI for Autonomous Agent Samples"
---

# Tasks: Web UI for Autonomous Agent Samples

**Input**: Design documents from `/specs/008-samples-web-ui/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/*.md (all present)

**Tests**: Included for the three new backend endpoints (mandated by `plan.md` Constitution Check III). UI behavior is verified manually via `quickstart.md` (Complexity Tracking trade-off — no automated browser tests).

**Organization**: Tasks are grouped by user story so each story can be implemented and tested independently. User Story 1 (P1) is the MVP.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps task to a user story (US1, US2, US3). Setup, Foundational, and Polish phases have no story label.

## Path Conventions

Single Akka project. Backend Java under `src/main/java/demo/...`, browser assets under `src/main/resources/static-resources/playground/...`, tests under `src/test/java/demo/ui/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Lay the on-disk skeleton so subsequent tasks have unambiguous paths.

- [X] T001 Create the static-asset directory tree at `src/main/resources/static-resources/playground/`, with `index.html` placeholder, and subdirectories `static/`, `static/styles/`, and `static/samples/`
- [X] T002 [P] Copy `akka-context/ui/default-akka-style.css` verbatim to `src/main/resources/static-resources/playground/static/styles/akka.css` (no edits — vendoring decision per research R-5)
- [X] T003 [P] Create empty `src/main/resources/static-resources/playground/static/styles/playground.css` (will receive theme overrides in US3 and event-tier styling in US2)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Wire the AgentRegistry, the per-sample POST response shape change, the asset-serving endpoint, the basic SPA shell, and the empty `RunControlEndpoint`. Every user story depends on these landing first.

**⚠️ CRITICAL**: No US task may begin until Phase 2 completes (T026 below is the gate).

### Backend: registry + control endpoint scaffold + UI endpoint

- [X] T004 [P] Create `src/main/java/demo/ui/api/AgentRegistry.java` — a final class with a static `Map<String, Class<? extends AutonomousAgent>>` mapping each owning agent's component id to its class. Populate per the table in `contracts/run-control.md` (`question-answerer`, `report-agent`, `document-reviewer`, `dynamic-agent`, `research-coordinator`, `consulting-coordinator`, `triage-agent`, `content-agent`, `debate-moderator`, `facilitator`, `review-moderator`, `project-lead`). Expose `static Class<? extends AutonomousAgent> classFor(String componentId)` that throws `HttpException.notFound` on unknown ids.
- [X] T005 [P] Create `src/main/java/demo/ui/api/PlaygroundUiEndpoint.java` with `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))` and three routes per `contracts/ui-endpoints.md`: `@Get("/")` returning a 302 to `/playground`, `@Get("/playground/static/**")` calling `HttpResponses.staticResource(request, "/playground/static/")`, and `@Get("/playground/**")` returning `HttpResponses.staticResource("playground/index.html")` (the SPA fallback).
- [X] T006 [P] Create `src/main/java/demo/ui/api/RunControlEndpoint.java` with `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))` and a single working route `@Get("/playground/api/samples")` returning a JSON list of `{id, displayName, agentComponentId}` derived from `AgentRegistry`. Other routes (`/runs/{runId}/status`, `/runs/{runId}/stop`) get added in later phases as stubs returning 501 for now.

### Frontend shell

- [X] T007 [P] Create `src/main/resources/static-resources/playground/index.html` with `<html data-theme>` root, `<head>` linking `static/styles/akka.css` then `static/styles/playground.css`, an inline synchronous theme-bootstrap script placeholder (filled in US3 — for now just sets `document.documentElement.dataset.theme = "platform"`), `<title>Autonomous Agent Playground</title>`, mount points `<header id="app-header">` and `<main id="app-main">`, and a `<script type="module" src="/playground/static/app.js">` reference at the bottom.
- [X] T008 [P] Create `src/main/resources/static-resources/playground/static/app.js` skeleton with: a router that inspects `location.pathname` and dispatches `/playground` → renderLanding, `/playground/<sample>` → renderPanel(sample, null), `/playground/<sample>/run/<runId>` → renderPanel(sample, runId); a panel mount/unmount lifecycle; an empty `renderLanding()` and `renderPanel()` to be filled in US1; a `renderNotFound()` placeholder. Imports the registry from `./samples/_registry.js`.
- [X] T009 [P] Create `src/main/resources/static-resources/playground/static/samples/_registry.js` exporting an empty object `export const samples = {};` — populated as each per-sample descriptor is added in US1.

### Per-sample POST response shape change (additive — see `contracts/run-control.md`)

Each task adds two fields (`runId`, `agentComponentId`) to the existing per-sample POST response record. The local `var agentInstanceId = UUID.randomUUID().toString()` already exists in each endpoint; surface it as `runId` in the response and add a constant `agentComponentId` that matches the owning agent's `@Component(id = …)`.

- [X] T010 [P] Modify `QuestionResponse` in `src/main/java/demo/helloworld/api/QuestionEndpoint.java`: add `runId` and `agentComponentId` (constant `"question-answerer"`); update the `request` handler to populate them.
- [X] T011 [P] Modify `PipelineResponse` in `src/main/java/demo/pipeline/api/PipelineEndpoint.java`: add `runId` (the ReportAgent's instance id) and `agentComponentId = "report-agent"`; the existing `pipelineId` and three task ids stay.
- [X] T012 [P] Modify `ReviewResponse` in `src/main/java/demo/docreview/api/DocReviewEndpoint.java`: add `runId` + `agentComponentId = "document-reviewer"`.
- [X] T013 [P] Modify `TaskResponse` in `src/main/java/demo/dynamic/api/DynamicEndpoint.java`: add `runId` + `agentComponentId = "dynamic-agent"`. Apply to both `/summarize` and `/translate` handlers.
- [X] T014 [P] Modify `ResearchResponse` in `src/main/java/demo/research/api/ResearchEndpoint.java`: add `runId` + `agentComponentId = "research-coordinator"`.
- [X] T015 [P] Modify `ConsultingResponse` in `src/main/java/demo/consulting/api/ConsultingEndpoint.java`: add `runId` + `agentComponentId = "consulting-coordinator"`.
- [X] T016 [P] Modify `SupportResponse` in `src/main/java/demo/support/api/SupportEndpoint.java`: add `runId` + `agentComponentId = "triage-agent"`.
- [X] T017 [P] Modify `PublishingPipeline` in `src/main/java/demo/publishing/api/PublishingEndpoint.java`: add `runId` (the ContentAgent's instance id — the run's owning agent for the *draft* phase) and `agentComponentId = "content-agent"`. Existing fields `draftTaskId`, `approvalTaskId`, `publishTaskId` remain.
- [X] T018 [P] Modify `DebateResponse` in `src/main/java/demo/debate/api/DebateEndpoint.java`: add `runId` + `agentComponentId = "debate-moderator"`.
- [X] T019 [P] Modify `NegotiationResponse` in `src/main/java/demo/negotiation/api/NegotiationEndpoint.java`: add `runId` + `agentComponentId = "facilitator"`.
- [X] T020 [P] Modify `ReviewResponse` in `src/main/java/demo/peerreview/api/PeerReviewEndpoint.java`: add `runId` + `agentComponentId = "review-moderator"`.
- [X] T021 [P] Modify `ProjectResponse` in `src/main/java/demo/devteam/api/DevTeamEndpoint.java`: add `runId` + `agentComponentId = "project-lead"`.

### Backend tests for the foundational pieces

- [X] T022 [P] Create `src/test/java/demo/ui/PlaygroundUiEndpointIntegrationTest.java` extending `TestKitSupport`. Cover the seven test items in `contracts/ui-endpoints.md` "Test contract" (root redirect, asset content types, 404 for missing asset, SPA fallback for `/playground/research`, SPA fallback for `/playground/research/run/abc-123`).
- [X] T023 [P] Create `src/test/java/demo/ui/RunControlEndpointSamplesTest.java` extending `TestKitSupport`. Verify `GET /playground/api/samples` returns a JSON list with at least the 12 expected sample ids.
- [X] T024 [P] Add a regression test for each existing per-sample POST response in the existing per-sample integration tests (where they exist), asserting the response now includes non-null `runId` and `agentComponentId`. If a sample has no existing test, add a minimal one in `src/test/java/demo/<sample>/`.

### Foundation gate

- [X] T025 Run `mvn compile` and confirm clean compile of the whole project.
- [X] T026 Run `mvn verify` and confirm only the new tests for the new endpoints exist, plus the augmented per-sample POST regressions (T024) pass. **Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 — Run a sample and read the final result (Priority: P1) 🎯 MVP

**Goal**: A user opens the playground in a browser, picks any sample, fills a tailored input form, submits, and (when the agent finishes) reads the structured final result — or sees a clear failure with reason. The panel updates without manual refresh and the run is URL-addressable. Approval-gated samples (publishing) expose the artifact-and-action UI.

**Independent test**: Per `quickstart.md` §1 (helloworld, docreview, publishing, URL share, failure visibility) and §4b (404), §4c (multi-run navigation), §5 (sample matrix). At the end of US1 the user can demo the playground end-to-end without seeing live event progress, tiers, run summary, or theme toggle (those land in US2 and US3).

### Tests for User Story 1 (write first, expect to fail before T029 onward)

- [X] T027 [P] [US1] Create `src/test/java/demo/ui/RunControlEndpointStatusTest.java` covering the four `GET /status` test items in `contracts/run-control.md` (404 unknown run, valid PENDING/RUNNING immediately after submit, `Awaitility.await()` to COMPLETED with typed `finalResult`, and a missing-required-query 400). Use `httpClient` and the existing helloworld POST to seed a run.
- [X] T028 [P] [US1] Create `src/test/java/demo/ui/PlaygroundUiPublishingApprovalTest.java` (integration test covering the AWAITING_INPUT path): post a publishing run, assert `runState == AWAITING_INPUT` once the draft completes, fetch the draft body via the existing `GET /publishing/draft/{id}`, then approve via the existing `POST /publishing/approve/{id}`, assert `runState == COMPLETED` and `finalResult` is a `PublishedPost`. (Existing endpoints; this asserts the run-status synthesis.)

### Backend implementation for US1

- [X] T029 [US1] Implement `GET /playground/api/runs/{runId}/status` in `src/main/java/demo/ui/api/RunControlEndpoint.java`. Required query parameters `component`, `task`, `sample`. Internally: resolve agent class via `AgentRegistry.classFor(component)` (throw `HttpException.notFound` on unknown); call `componentClient.forAutonomousAgent(<class>, runId).getState()`; call `componentClient.forTask(taskId).get(<task definition resolved from sample>)`; synthesize `RunStatus` per `data-model.md`, including the `runState` roll-up rule (PENDING/RUNNING/AWAITING_INPUT/COMPLETED/FAILED/CANCELLED). For `AWAITING_INPUT`, detect via the sample's known dependency chain (publishing's APPROVAL task ⇒ if its status is PENDING and it is unassigned, the run is AWAITING_INPUT).
- [X] T030 [US1] Add a small per-sample task-definition lookup helper in `src/main/java/demo/ui/api/RunControlEndpoint.java` that returns the right `Task<T>` constant for a given `sampleId` (so `forTask(taskId).get(...)` typechecks). One line per sample mapping to `QuestionTasks.ANSWER`, `PipelineTasks.*`, `ReviewTasks.REVIEW` (docreview), `DynamicTasks.SUMMARIZE`/`TRANSLATE`, `ResearchTasks.BRIEF`, `ConsultingTasks.ENGAGEMENT`, `SupportTasks.RESOLVE`, `PublishingTasks.PUBLISH`, `DebateTasks.DEBATE`, `NegotiationTasks.NEGOTIATE`, `ReviewTasks.REVIEW` (peerreview), `ProjectTasks.PLAN`. (Disambiguate the two `ReviewTasks` classes by FQN: `demo.docreview.application.ReviewTasks` vs `demo.peerreview.application.ReviewTasks`.)
- [X] T031 [US1] Add 400 handling for missing query parameters and 404 for unknown `sample` to the same endpoint method (`HttpException.badRequest(...)` / `HttpException.notFound(...)`).

### Frontend implementation for US1 — shell + landing + panel

- [X] T032 [US1] Implement `renderLanding()` in `src/main/resources/static-resources/playground/static/app.js`: fetch `GET /playground/api/samples`, render a list of links `/playground/<id>` with the sample's `displayName` and a one-line teaser drawn from each registered descriptor's `description.overview`. (Falls back to a "no samples registered" message if the registry is empty.)
- [X] T033 [US1] Implement `renderPanel(sampleId, runId | null)` in `src/main/resources/static-resources/playground/static/app.js`: look up the descriptor in `samples`; render the structured description (overview / agents / tasks / flow / demonstrates) in a header strip; render the input form (when `runId === null`) or the run view (when `runId` is set); on submit, POST to the descriptor's submit endpoint, navigate to `/playground/<sample>/run/<returned runId>` via `history.pushState`, and start polling. While in the run view, poll `GET /playground/api/runs/{runId}/status?component=…&task=…&sample=…` every 2 seconds until `runState` is terminal (COMPLETED/FAILED/CANCELLED). Render a status badge using `runState`. On COMPLETED, call the descriptor's `renderResult(finalResult)`. On FAILED/CANCELLED, render the failure block with `failureReason`.
- [X] T034 [US1] Implement `renderNotFound()` in `src/main/resources/static-resources/playground/static/app.js`: minimal "Run not found" page with no auto-redirect, no recovery widget. Trigger when `GET /status` returns 404 (FR-008b).
- [X] T035 [US1] Implement the AWAITING_INPUT artifact-and-action display in `src/main/resources/static-resources/playground/static/app.js`: when `runState === "AWAITING_INPUT"`, fetch the artifact from the descriptor's `artifactEndpoint(run)` (publishing returns `/publishing/draft/{taskId}`), render the artifact body inline above the action controls, and bind each descriptor `action` button to its handler (calls the existing per-sample approve/reject endpoint and resumes polling). FR-013 + Q2 clarification.

### Frontend implementation for US1 — per-sample descriptors

Each task creates one module with: `id`, `displayName`, `description`, `inputForm` (field schema + a `submit(values)` function calling the existing per-sample POST endpoint), `renderResult(finalResult, runState)`, optional `actions`, and (for publishing) `artifactEndpoint(run)`.

- [X] T036 [P] [US1] `src/main/resources/static-resources/playground/static/samples/helloworld.js` — single text input "question"; submit POST `/questions`; render `{answer, confidence}`.
- [X] T037 [P] [US1] `src/main/resources/static-resources/playground/static/samples/pipeline.js` — single text input "topic"; submit POST `/pipeline`; render the three-phase `ReportResult` collected via separate `forTask` calls (collect / analyze / report) — note: pipeline has multiple task ids; descriptor stores all three and renders each phase as it completes.
- [X] T038 [P] [US1] `src/main/resources/static-resources/playground/static/samples/docreview.js` — multi-line "document" textarea + single-line "review instructions"; submit POST `/docreview`; render `{assessment, findings[], compliant}`.
- [X] T039 [P] [US1] `src/main/resources/static-resources/playground/static/samples/dynamic.js` — radio for mode (`summarize` / `translate`) + multi-line "content"; submit POST `/dynamic/summarize` or `/dynamic/translate`; render result string in a `<pre>` block.
- [X] T040 [P] [US1] `src/main/resources/static-resources/playground/static/samples/research.js` — single text input "topic"; submit POST `/research`; render `ResearchBrief {title, summary, keyFindings[]}`.
- [X] T041 [P] [US1] `src/main/resources/static-resources/playground/static/samples/consulting.js` — multi-line "problem"; submit POST `/consulting`; render `ConsultingResult {assessment, recommendation, escalated}`.
- [X] T042 [P] [US1] `src/main/resources/static-resources/playground/static/samples/support.js` — multi-line "issue"; submit POST `/support`; render `SupportResolution {category, resolution, resolved}`.
- [X] T043 [P] [US1] `src/main/resources/static-resources/playground/static/samples/publishing.js` — single text input "topic"; submit POST `/publishing`; declares `artifactEndpoint(run)` returning `/publishing/draft/{run.tasks[0]}`; declares two actions (Approve, Reject) calling `/publishing/approve/{approvalTaskId}` and `/publishing/reject/{approvalTaskId}` (both come from the POST response's `approvalTaskId`); render `PublishedPost {url, publishedAt}`.
- [X] T044 [P] [US1] `src/main/resources/static-resources/playground/static/samples/debate.js` — single text input "topic"; submit POST `/debate`; render `DebateResult {topic, synthesis, keyArguments[]}`.
- [X] T045 [P] [US1] `src/main/resources/static-resources/playground/static/samples/negotiation.js` — single text input "topic"; submit POST `/negotiation`; render `NegotiationResult {topic, outcome, finalOffer}`.
- [X] T046 [P] [US1] `src/main/resources/static-resources/playground/static/samples/peerreview.js` — multi-line "document"; submit POST `/peerreview`; render `ReviewResult {document, assessment, reviewerFindings[]}`.
- [X] T047 [P] [US1] `src/main/resources/static-resources/playground/static/samples/devteam.js` — multi-line "project description"; submit POST `/devteam`; render `ProjectResult` (use the actual record fields — verify by reading the existing `demo.devteam.application.ProjectResult` record).
- [X] T048 [US1] Populate `src/main/resources/static-resources/playground/static/samples/_registry.js` to import each descriptor and export `samples = { helloworld, pipeline, docreview, dynamic, research, consulting, support, publishing, debate, negotiation, peerreview, devteam }`. Depends on T036–T047.

### Manual validation for US1

- [X] T049 [US1] Run `mvn verify` (all backend tests pass, including the new ones from T022, T023, T024, T027, T028).
- [X] T050 [US1] Walk `quickstart.md` §0 (boot), §1 (US1 acceptance scenarios), §4b (404), §4c (multi-run), §5 (sample matrix) on a local browser. **Checkpoint**: User Story 1 is fully functional and demoable as MVP.

---

## Phase 4: User Story 2 — Live progress + history + struggle/failure visibility (Priority: P2)

**Goal**: While a run is in progress, the user sees a live stream of the agent runtime's notifications, with three visual tiers (healthy / struggle / terminal failure), agent-instance disambiguation when multiple instances of the same role are observed, a per-iteration token annotation, an always-visible run-summary line (cumulative input/output tokens, iteration count), a stuck-run notice after 60 s of silence, a "live updates unavailable" indicator on disconnect with auto-reconnect, and a confirmation-gated stop control. SSE replaces the US1 polling for status updates (polling kept as a fallback on reconnect to recover terminal outcomes per FR-009a).

**Independent test**: Per `quickstart.md` §2 (multi-agent live updates, multi-instance disambiguation, three tiers, stuck-run notice, stream resilience, reload mid-run) and §4a (stop) and §4d (token / iteration summary).

### Tests for User Story 2 (write first)

- [X] T051 [P] [US2] Create `src/test/java/demo/ui/TierClassifierTest.java` — unit-style test of the tier classifier function. Feed synthetic instances of each `Notification` subtype that the classifier must categorize (TaskFailed, TaskCancelled, IterationFailed, TaskResultRejected, TaskDependencyWait, TaskStruggleDetected, TaskApproachingMaxIterations, RepeatedIterationFailure, TaskDependencyStuck, TeamMemberSetupFailed, Stopped(reason="operator"), Stopped(reason="auto-stopped"), TaskStarted, TaskCompleted, Activated). Assert each maps to the correct tier per the table in `contracts/run-notifications.md`.
- [X] T052 [P] [US2] Create `src/test/java/demo/ui/RunNotificationEndpointIntegrationTest.java` covering the three test items in `contracts/run-notifications.md` (404 for missing component, content type `text/event-stream` on a freshly-created agent, live event emission after seeding a helloworld run, EventEnvelope JSON shape including `tier`, `category`, `kind`, and the wrapped `raw` payload).
- [X] T053 [P] [US2] Create `src/test/java/demo/ui/RunControlEndpointStopTest.java` covering test items 4–6 in `contracts/run-control.md` (stop succeeds and run becomes CANCELLED; idempotent on re-call; 404 on unknown run id).

### Backend implementation for US2

- [X] T054 [US2] Implement the tier classifier as a private static method `tierFor(Notification n): String` in `src/main/java/demo/ui/api/RunNotificationEndpoint.java` per the table in `contracts/run-notifications.md` (StruggleNotification + IterationFailed + TaskResultRejected + TaskDependencyWait → "struggle"; TaskFailed + TaskCancelled + TeamMemberSetupFailed + Stopped("operator") → "terminal_failure"; else → "healthy").
- [X] T055 [US2] Implement the category derivation `categoryFor(Notification n): String` in the same class — read the marker sub-interface (`LifecycleNotification` → `"lifecycle"`, `TaskNotification` → `"task"`, `HandoffNotification` → `"handoff"`, `DelegationNotification` → `"delegation"`, `TeamNotification` → `"team"`, `BacklogNotification` → `"backlog"`, `ConversationNotification` → `"conversation"`, `MessagingNotification` → `"messaging"`, `StruggleNotification` → `"struggle"`, anything else → `"other"`).
- [X] T056 [US2] Implement the `EventEnvelope` record (inner record of `RunNotificationEndpoint`) per `data-model.md`: `eventId`, `timestamp`, `tier`, `category`, `kind`, `raw`. Provide a small factory that wraps a `Notification` with a monotonic counter.
- [X] T057 [US2] Implement the route `GET /playground/api/runs/{runId}/events?component={agentComponentId}` in `src/main/java/demo/ui/api/RunNotificationEndpoint.java` per `contracts/run-notifications.md`. Resolve agent class via `AgentRegistry.classFor`; obtain `Source<Notification, NotUsed>` from `componentClient.forAutonomousAgent(<class>, runId).notificationStream()`; map to `EventEnvelope`; return `HttpResponses.serverSentEvents(source, env -> String.valueOf(env.eventId()))` so the SDK populates the SSE `id:` field. Class-level `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))`.
- [X] T058 [US2] Implement the route `POST /playground/api/runs/{runId}/stop?component={agentComponentId}` in `src/main/java/demo/ui/api/RunControlEndpoint.java` per `contracts/run-control.md`. Resolve class via `AgentRegistry`, call `componentClient.forAutonomousAgent(<class>, runId).stop()`, return `StopResponse(runState="CANCELLED", stoppedAt=Instant.now())`. Idempotent on re-call (a second `.stop()` is a no-op or the SDK handles it; either way return 200). 404 on unknown run id.

### Frontend implementation for US2

- [X] T059 [P] [US2] Add three-tier styling to `src/main/resources/static-resources/playground/static/styles/playground.css`: `.event-row[data-tier="healthy"]` (uses `--ok` / neutral colors and a check-mark icon glyph), `.event-row[data-tier="struggle"]` (uses `--warn` and a warning glyph), `.event-row[data-tier="terminal_failure"]` (uses `--err` and an X glyph). Pair each tier with a label string in addition to color so colorblind viewers see the distinction (FR-011 Q clarification).
- [X] T060 [P] [US2] Create `src/main/resources/static-resources/playground/static/event-log.js`: `connectEventStream(runId, agentComponentId, onEnvelope)` opens an `EventSource` to `/playground/api/runs/{runId}/events?component=…`, parses each SSE `data:` payload as JSON, and invokes `onEnvelope(envelope)`. Reconnect is delegated to the browser's built-in EventSource reconnect; on every `onerror` the caller is notified so it can refetch `/status` (FR-009a). Export also `renderEventRow(envelope, agentDisplay)` returning an `HTMLElement` with `data-tier`, the agent display string from `agentDisplay.labelFor(...)`, and a one-line summary derived from `envelope.kind` + relevant `envelope.raw` fields.
- [X] T061 [P] [US2] Implement the agent display label policy in `src/main/resources/static-resources/playground/static/event-log.js` as `class AgentDisplay { /* tracks instances by componentId */; labelFor(componentId, instanceId) }`. Returns `componentId` while only one instance is observed; switches to `componentId <first-6-of-instanceId>` once a second instance of the same `componentId` is seen, retroactively re-labeling existing rows of that component. Expose the full instance UUID via a `title` (tooltip) on the rendered row.
- [X] T062 [P] [US2] Create `src/main/resources/static-resources/playground/static/run-summary.js`: `class RunSummary { ingest(envelope); render() }`. Tracks cumulative `inputTokens`, `outputTokens`, `iterations` from `IterationCompleted` events. `render()` returns an HTML element with the three counters; updated live as events arrive.
- [X] T063 [US2] Wire the event log + run summary into the panel in `src/main/resources/static-resources/playground/static/app.js`: replace the US1 polling-only behavior with: open the SSE stream on panel mount; for each envelope, append a row via `event-log.js`, feed it to `run-summary.js`, update `lastEventReceivedAt`. Keep a *fallback* `GET /status` poll only when SSE is disconnected, and on every reconnect to recover terminal state (FR-009a).
- [X] T064 [US2] Annotate `IterationCompleted` event rows with that iteration's token usage in `src/main/resources/static-resources/playground/static/event-log.js` (FR-012a). Read `envelope.raw.tokenUsage()` shape (input / output) and append a small subtle " • in/out tokens" suffix to the row.
- [X] T065 [US2] Implement the 60-second stuck-run notice in `src/main/resources/static-resources/playground/static/app.js`: setInterval(1000) compares `Date.now() - lastEventReceivedAt`; once over 60 s and `runState` is non-terminal, render a non-alarming notice element ("no recent activity for over a minute"). Clear the notice as soon as a new event arrives or the run reaches a terminal state (FR-011a).
- [X] T066 [US2] Implement the connection-status indicator (FR-009b) in `src/main/resources/static-resources/playground/static/app.js` + style in `playground.css`: maintain a `connection ∈ {connected, reconnecting, disconnected}`. Display `reconnecting` and `disconnected` states as a small banner near the event log. Tie to `EventSource.onerror` (transition to `reconnecting`) and `onopen` (back to `connected`); after browser's reconnect attempts give up, transition to `disconnected`. On every transition `* → connected`, refetch `GET /status` to recover terminal state.
- [X] T067 [US2] Implement the stop control + confirmation dialog in `src/main/resources/static-resources/playground/static/app.js`: a button visible only while `runState ∈ {RUNNING, AWAITING_INPUT}`; clicking it opens a native `<dialog>` element ("Stop the run?") with cancel/confirm; on confirm, POST `/playground/api/runs/{runId}/stop?component=…`. The actual state transition is observed via the incoming `Stopped` SSE event, not the response.

### Manual validation for US2

- [X] T068 [US2] Run `mvn verify` and confirm the new tests (T051, T052, T053) pass.
- [X] T069 [US2] Walk `quickstart.md` §2 (US2 acceptance scenarios) and §4a (stop) and §4d (token/iteration summary). **Checkpoint**: User Stories 1 AND 2 work together.

---

## Phase 5: User Story 3 — Akka style + light/dark/platform theme toggle (Priority: P3)

**Goal**: The UI looks like part of the Akka product family on every page, and a 3-way theme toggle (light / dark / platform) is available, with the chosen mode persisted in `localStorage`. The page first-paints in the chosen theme on reload (no flash).

**Independent test**: Per `quickstart.md` §3 (theme toggle) and §3c (visual coherence). The tests for US3 are entirely manual (no backend tests; theme is purely client-side).

### Implementation for US3

- [X] T070 [P] [US3] Add theme override CSS to `src/main/resources/static-resources/playground/static/styles/playground.css`:  `[data-theme="light"]` and `[data-theme="dark"]` selectors that re-apply the same CSS custom-property values the akka stylesheet defines under each `prefers-color-scheme` branch. `[data-theme="platform"]` is a no-op (lets the stylesheet's existing `prefers-color-scheme` rule win). FR-015, FR-017.
- [X] T071 [P] [US3] Create `src/main/resources/static-resources/playground/static/theme.js`: read `localStorage.getItem('playground-theme')` (default `"platform"`), apply `document.documentElement.dataset.theme = mode`, expose `setTheme(mode)` that persists and re-applies. Exports `currentTheme()`.
- [X] T072 [P] [US3] Add a 3-way theme toggle UI control in the header. Implement in `src/main/resources/static-resources/playground/static/app.js` `renderHeader()` (or extend the existing landing renderer). Use the Akka stylesheet's `[data-badge]` or button primitives. Three buttons (or a segmented control) for Light / Dark / Platform; clicking each calls `setTheme(...)` from `theme.js` and visibly marks the active one. Available on every panel (header is global).
- [X] T073 [US3] Replace the placeholder theme bootstrap in `src/main/resources/static-resources/playground/index.html` `<head>` with an inline synchronous `<script>` that reads `localStorage.getItem('playground-theme') || 'platform'` and writes it to `document.documentElement.dataset.theme` *before* any external stylesheet loads — preventing the flash of wrong theme on reload. SC-008.

### Manual validation for US3

- [ ] T074 [US3] Walk `quickstart.md` §3. **Checkpoint**: All three user stories independently functional and demoable.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation across the full feature surface; cross-browser smoke; documentation.

- [ ] T075 [P] Cross-browser smoke test: load the playground in current Chromium-family, Firefox, and Safari. Run a helloworld submission end-to-end in each. Verify SSE works, theme toggle works, no console errors. SC-009.
- [ ] T076 [P] Keyboard-only walkthrough: navigate from the landing page to a sample, fill the input form, submit, watch the event log, switch themes — all using only `Tab`, `Shift+Tab`, `Enter`, and `Esc`. SC-010.
- [X] T077 [P] Update `README.md` with a single paragraph and a link describing how to run the playground locally and open the new UI; reference `specs/008-samples-web-ui/quickstart.md`. (Skip if the reviewer says the existing README is sufficient.)
- [ ] T078 Run the full `quickstart.md` end-to-end once more on a fresh checkout to confirm "done criteria" at the bottom of `quickstart.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: starts immediately. T001 must finish before T002 / T003.
- **Phase 2 (Foundational)**: starts after Phase 1. T004–T009 parallel after dirs+CSS skeletons exist. T010–T021 fully parallel (one file each). T022–T024 parallel after their dependents. T025/T026 are gates.
- **Phase 3 (US1)**: depends on Phase 2 complete (T026 gate).
- **Phase 4 (US2)**: depends on Phase 2 complete; can run in parallel with Phase 3 *if separate developers are working*. The two phases share `app.js` so a single developer should sequence Phase 4 after Phase 3.
- **Phase 5 (US3)**: depends on Phase 2 complete; **can run fully in parallel with Phase 3 and Phase 4** — touches only `theme.js`, `playground.css` (additive selectors), `index.html` (one script tag), and `app.js` `renderHeader()` (small addition).
- **Phase 6 (Polish)**: depends on all selected user stories complete.

### Within each user story

- Tests first (T027/T028 in US1; T051/T052/T053 in US2). Expect them to FAIL.
- Backend implementation before frontend that calls it (e.g., T029 before T033).
- Models/DTOs before the routes that return them (T056 before T057).
- Per-sample descriptors are independent of each other (all [P]); they all complete before the registry-population task (T048).

### Parallel opportunities per phase

- **Phase 2**: T004, T005, T006, T007, T008, T009, T010–T021, T022, T023, T024 — almost everything is parallel within phase. An aggressive parallel team could land Phase 2 in one sitting.
- **Phase 3**: T036–T047 all parallel (twelve sample descriptor files). T032/T033/T034/T035 share `app.js` so are sequential per developer but logically partitionable. T027/T028 (tests) are parallel.
- **Phase 4**: T059, T060, T061, T062 are parallel (all in different files). T063/T064/T065/T066/T067 share `app.js` — sequential per developer. T051/T052/T053 (tests) parallel.

---

## Parallel Examples

### Phase 2 — landing the foundation

```text
# In one block after T001 + T002 + T003:
Task: "T004 Create AgentRegistry"
Task: "T005 Create PlaygroundUiEndpoint"
Task: "T006 Create RunControlEndpoint scaffold"
Task: "T007 Create index.html shell"
Task: "T008 Create app.js skeleton"
Task: "T009 Create _registry.js stub"

# In one block (modifies 12 different files):
Task: "T010 Modify QuestionEndpoint"
Task: "T011 Modify PipelineEndpoint"
Task: "T012 Modify DocReviewEndpoint"
Task: "T013 Modify DynamicEndpoint"
Task: "T014 Modify ResearchEndpoint"
Task: "T015 Modify ConsultingEndpoint"
Task: "T016 Modify SupportEndpoint"
Task: "T017 Modify PublishingEndpoint"
Task: "T018 Modify DebateEndpoint"
Task: "T019 Modify NegotiationEndpoint"
Task: "T020 Modify PeerReviewEndpoint"
Task: "T021 Modify DevTeamEndpoint"
```

### Phase 3 — twelve sample descriptors in parallel

```text
Task: "T036 helloworld.js"
Task: "T037 pipeline.js"
Task: "T038 docreview.js"
Task: "T039 dynamic.js"
Task: "T040 research.js"
Task: "T041 consulting.js"
Task: "T042 support.js"
Task: "T043 publishing.js"
Task: "T044 debate.js"
Task: "T045 negotiation.js"
Task: "T046 peerreview.js"
Task: "T047 devteam.js"
# After all twelve land:
Task: "T048 Populate _registry.js"
```

---

## Implementation Strategy

### MVP first (User Story 1 only)

1. Land Phase 1 + Phase 2 (T001–T026). One short pass.
2. Land Phase 3 (T027–T050). At T050 the playground is demoable end-to-end with all 12 samples. **Stop here for an MVP demo if desired** — no live event log yet, no theme toggle, but every sample runs.

### Incremental delivery

1. Phase 1+2 → foundation.
2. + Phase 3 → MVP, demo to stakeholders.
3. + Phase 4 → live progress shines; demo upgraded.
4. + Phase 5 → visual polish, light/dark, demo-presentable on a projector.
5. + Phase 6 → cross-browser confirmation and documentation.

### Parallel team strategy

With three developers after Phase 2 lands:

- Dev A: Phase 3 (US1, MVP).
- Dev B: Phase 4 (US2, live event UI). Coordinates with Dev A on `app.js` integration.
- Dev C: Phase 5 (US3, theme). Effectively independent of A and B.

---

## Notes

- Every `[P]` task touches a file no other concurrently-running task is editing.
- Every per-sample descriptor (T036–T047) and per-sample endpoint modification (T010–T021) is independent — these are the easy parallel wins.
- The publishing sample's approval flow (T035 + T043) is the *only* place US1 needs an artifact-display + action UI; other approval-gated samples (compliance, editorial — both currently *not yet implemented*) will reuse the same descriptor shape when added.
- `app.js` is a shared file across many tasks within a phase; assign a single owner per phase to avoid merge thrash.
- The tier classifier (T054) is the smallest piece of important logic; keep it in one method and keep `TierClassifierTest` (T051) tight — both should be reviewed together.
- After every checkpoint (T050, T069, T074), the running playground is in a useful state for an interactive demo; don't skip the manual walkthrough.
