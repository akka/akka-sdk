# Implementation Plan: Web UI for Autonomous Agent Samples

**Branch**: `008-samples-web-ui` | **Date**: 2026-04-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/008-samples-web-ui/spec.md`

## Summary

Add a browser-based UI to the autonomous-agent-playground service so each existing sample (helloworld, pipeline, docreview, dynamic, research, consulting, support, publishing, debate, negotiation, peerreview, devteam) gets a dedicated, tailored panel: input form sized to that sample's request, live event log streaming the agent runtime's notifications, three-tier visual differentiation between healthy / struggling / terminally-failed events, per-run token and iteration summary, operator approval and stop controls, sample-specific result rendering, and a light/dark/platform theme toggle. The UI is served by the playground service itself with no separate frontend build step at runtime.

Technical approach: ship the UI as static HTML/CSS/JavaScript assets from the existing playground service via a new `PlaygroundUiEndpoint`. Use the SDK's `componentClient.forAutonomousAgent(...).notificationStream()` to subscribe to runtime notifications and bridge that stream to the browser as Server-Sent Events through a new endpoint. Add a small `RunControlEndpoint` for operator stop. Per-sample input shapes, result renderers, and curated descriptions live in vanilla JS modules per sample. No frontend framework, no bundler, no build step. The Akka default style sheet is copied into the UI assets and extended with the per-feature components (run summary line, three-tier event rows, stuck-run notice, etc.).

## Technical Context

**Language/Version**: Java 21 (matches existing playground service); UI assets are vanilla HTML5 + CSS3 + ES2022 JavaScript (no transpilation, no bundler).
**Primary Dependencies**: Akka SDK ≥ 3.6.0-M2 (already in `pom.xml`). No new runtime dependencies. `Notification` types and `componentClient.forAutonomousAgent(...).notificationStream()` come from the SDK. Server-Sent Events use the existing Akka HTTP / akka-streams API; no new SSE library needed.
**Storage**: None server-side beyond what the SDK and existing per-sample endpoints already use. UI state (theme preference, last viewed run id per sample) lives in the browser's `localStorage`.
**Testing**: Existing JUnit 5 + `TestKitSupport` setup is reused. New backend endpoints get integration tests via `TestKitSupport` + `httpClient`. Browser-side behavior is verified manually via the quickstart steps; no automated browser test framework is added (Simplicity gate).
**Target Platform**: JVM service runtime as today; UI consumed by current desktop browsers (Chromium-family, Firefox, Safari).
**Project Type**: Single Akka project — the UI is bundled into the same service rather than a separate frontend project.
**Performance Goals**: Live progress events appear in the UI ≤ 2 s after the runtime emits them (SC-004); first paint after navigation ≤ 1 s on local dev; theme switch is instant (no flash of unstyled content). Stuck-run notice triggers at 60 s of event silence (FR-011a).
**Constraints**: No frontend build step at runtime (FR-018); description content is shipped with the UI rather than fetched from the README (FR-002a); no server-side per-run event log retention required for reconnect (FR-009a); operator stop must be confirmation-gated (FR-013a); 404 page when run id is unknown (FR-008b); URLs are stable so prior runs are reachable via browser back / shared link (FR-008a).
**Scale/Scope**: 12 sample panels currently. Single concurrent operator typical (demo / evaluator). Each run produces tens to a few hundred events; the UI must remain responsive with a few hundred event rows in the log.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Akka SDK First — PASS

All server-side code uses Akka SDK primitives:
- New HTTP endpoints (`PlaygroundUiEndpoint`, `RunNotificationEndpoint`, `RunControlEndpoint`) extend / annotate the SDK's HTTP endpoint surface.
- Static asset serving uses the SDK's documented static-resource pattern (see Phase 0 research item R-1).
- Live event subscription uses `componentClient.forAutonomousAgent(...).notificationStream()` (SDK; documented in `akka-context/sdk/autonomous-agents.html.md`).
- Operator stop uses the SDK's autonomous-agent operator API (Phase 0 research item R-3 confirms exact method).
- No third-party web frameworks, SSE libraries, or HTTP middleware are introduced.

UI client code is vanilla HTML + CSS + JavaScript. The constitution mandates Akka SDK *for service components*; the browser-side asset is not a "service component" in that sense, and adding a frontend framework would violate the Simplicity gate without justification.

### II. Design Principles — PASS

- **Domain independence**: this feature has no new domain logic. The UI is in the `api` layer; no `domain` or `application` packages are touched.
- **API isolation**: new endpoints define their own request / response records (e.g. `RunSummary`, `EventEnvelope`, `StopResponse`). Existing domain types are not exposed; instead small DTOs translate runtime notifications into shapes the UI consumes.
- **Single responsibility**: three small new endpoint classes, one per concern (asset serving, notification streaming, run control). Per-sample JS modules encapsulate per-sample input/result/description.
- **Descriptive naming**: classes named after their role (`PlaygroundUiEndpoint`, `RunNotificationEndpoint`, `RunControlEndpoint`, `EventEnvelope`, `RunSummary`).

### III. Test Coverage — PASS (with caveat)

- All three new server endpoints will have integration tests via `TestKitSupport` + `httpClient`: index serving, sample asset serving, 404 handling, notification stream emits valid SSE frames, stop endpoint asserts the right SDK calls.
- Per-sample JS modules and DOM rendering are covered by manual quickstart checks; no automated browser-test framework is introduced (Simplicity).
- This is a knowing trade-off: it is recorded in Complexity Tracking below for transparency rather than as a violation.

### IV. Simplicity — PASS

- One service, no new project. No bundler, no transpiler, no frontend framework. CSS is the existing Akka stylesheet plus a small extension stylesheet for the UI's added components.
- Per-sample customization lives in small per-sample JS modules sharing a common shell — no abstraction layer for "panel rendering engine"; each module is direct DOM code.
- Live updates use Server-Sent Events (one-way is sufficient) rather than WebSocket (which would require a bidirectional protocol the UI does not need).

### Post-Phase-1 re-check (filled in after research, data-model, contracts)

- **I. Akka SDK First — STILL PASS.** All design decisions use only SDK primitives: `HttpEndpoint`, `HttpResponses.staticResource`, `HttpResponses.serverSentEvents`, `HttpException.notFound`, `componentClient.forAutonomousAgent(...).notificationStream()` / `.stop()` / `.getState()`, `componentClient.forTask(...).get(...)`. No third-party HTTP, SSE, or DI library is introduced. The browser code is vanilla HTML/CSS/JS as previously justified.
- **II. Design Principles — STILL PASS.** Each new endpoint defines its own request/response records (`RunRef`, `EventEnvelope`, `RunStatus`, `StopResponse`, etc.) — see `contracts/`. Three single-responsibility endpoints (asset / events / control) replace one monolithic class. Domain remains untouched.
- **III. Test Coverage — STILL PASS (with the previously recorded caveat).** Each new endpoint has integration test contracts in `contracts/*.md`. The tier classifier is testable in isolation. UI behavior remains manual via `quickstart.md`. The trade-off is documented in Complexity Tracking below.
- **IV. Simplicity — STILL PASS.** No new persistence introduced (the AgentRegistry is a static map in code). The "subscribe to children" complexity was deliberately deferred (research R-3). SSE chosen over WebSocket. Theme implemented with CSS custom properties + a small extension stylesheet rather than a runtime theming engine.

### Spec-level adjustment surfaced during research

Research finding R-3 forces a single small modification to the spec's Assumptions: the assumption that "the existing per-sample HTTP endpoint contracts … are sufficient as the contract the UI submits and reads against" is true *with one mechanical change* — each existing sample's POST response gains two new fields (`runId` aliasing the owning agent's instance id, and `agentComponentId`). No fields are renamed or removed, so the change is purely additive for any existing callers. The spec text remains accurate in spirit — no endpoint is redesigned, only extended — but `tasks.md` will explicitly enumerate the per-sample edits as part of the implementation backlog.

## Project Structure

### Documentation (this feature)

```text
specs/008-samples-web-ui/
├── plan.md              # This file
├── research.md          # Phase 0 — resolves R-1…R-5
├── data-model.md        # Phase 1 — Run, EventEnvelope, RunSummary, ThemePreference
├── quickstart.md        # Phase 1 — manual verification recipe per US
├── contracts/
│   ├── ui-endpoints.md      # GET / serving, GET /playground/* SPA fallback
│   ├── run-notifications.md # GET /playground/api/runs/{taskId}/events  (SSE)
│   └── run-control.md       # POST /playground/api/runs/{taskId}/stop  + 404 / status
├── checklists/
│   └── requirements.md  # already exists
└── spec.md              # already exists
```

### Source Code (repository root)

```text
src/main/java/demo/
├── ui/                                         # New top-level package for the UI surface
│   └── api/
│       ├── PlaygroundUiEndpoint.java           # Static index + SPA fallback for /playground/...
│       ├── RunNotificationEndpoint.java        # SSE bridge to componentClient.notificationStream()
│       ├── RunControlEndpoint.java             # POST stop, GET status proxy if needed
│       └── (request/response records as inner records of each endpoint)
├── helloworld/, pipeline/, …                   # Existing samples — UNCHANGED in this plan
└── (no domain or application changes)

src/main/resources/
├── static-resources/                           # Served by PlaygroundUiEndpoint
│   ├── index.html                              # Single shell with client-side router
│   ├── styles/
│   │   ├── akka.css                            # Copy of akka-context/ui/default-akka-style.css
│   │   └── playground.css                      # Additions: event tiers, run summary, stuck notice
│   ├── app.js                                  # Shell, router, theme controller, SSE client
│   ├── theme.js                                # Theme controller + persistence
│   ├── event-log.js                            # Event row rendering, three-tier classification
│   ├── run-summary.js                          # Token + iteration totals
│   └── samples/                                # One module per sample
│       ├── helloworld.js
│       ├── pipeline.js
│       ├── docreview.js
│       ├── dynamic.js
│       ├── research.js
│       ├── consulting.js
│       ├── support.js
│       ├── publishing.js
│       ├── debate.js
│       ├── negotiation.js
│       ├── peerreview.js
│       ├── devteam.js
│       └── _registry.js                        # Maps sample id → module
└── application.conf                            # UNCHANGED

src/test/java/demo/
└── ui/
    ├── PlaygroundUiEndpointIntegrationTest.java
    ├── RunNotificationEndpointIntegrationTest.java
    └── RunControlEndpointIntegrationTest.java
```

**Structure Decision**: Single project, additive only. The UI lives in a new `demo.ui.api` package and `src/main/resources/static-resources/`. No existing sample code is modified; the spec deliberately consumes existing per-sample HTTP endpoints unchanged (Assumptions). Test code follows the existing TestKitSupport pattern under `src/test/java/demo/ui/`.

## Complexity Tracking

| Item | Why accepted | Simpler alternative rejected because |
|---|---|---|
| Manual-only browser verification (no automated frontend test framework) | Constitution III calls for tests; we add backend integration tests but rely on documented manual recipe in `quickstart.md` for UI behavior. | Adding Playwright/Selenium would introduce a frontend toolchain (build, browsers, CI) violating the Simplicity gate. The UI is a thin demo shell; backend correctness is what tests must guard. |
| Three new endpoint classes instead of one | Each has a focused responsibility (asset serving, SSE bridge, control). | Folding all three into one class would violate Single Responsibility (Design Principles II) and entangle the SSE long-lived response with synchronous control routes. |
