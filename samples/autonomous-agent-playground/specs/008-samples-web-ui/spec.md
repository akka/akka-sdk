# Feature Specification: Web UI for Autonomous Agent Samples

**Feature Branch**: `008-samples-web-ui`
**Created**: 2026-04-27
**Status**: Draft
**Input**: User description: "We have several samples for autonomous agents in this playground project. I want to add a web UI with a tailored panel/page for each sample. 1) It should be possible to enter the goal/task input, and see the final result text when the agent(s) have finished. 2) Live progress of the agents should be displayed, including history of the progress notification events. Struggle or failure should also be clearly visible. 3) The UI should use the Akka default style, and have toggle for light/dark/platform colors."

## Clarifications

### Session 2026-04-27

- Q: For per-sample description content (FR-002a), what is shown and where does it come from? → A: Description content is curated and shipped with the UI itself (not fetched from the README at runtime). It mirrors the README sample-section structure — short overview followed by agents, tasks, flow, and what the sample demonstrates — adapted for web display rather than copied verbatim.
- Q: When the live progress-event stream disconnects mid-run, what does the UI do? → A: Auto-reconnect for future events and re-poll the run's GET status endpoint on reconnect so terminal status / final result is never missed; intermediate events that occur during the disconnect may be lost. No server-side per-run event log is required.
- Q: How does a sample panel behave when the user submits a second run? → A: Submitting starts a new run and the panel navigates to the new run's URL, replacing the current view. Prior runs are reachable only via their own URLs (browser back, bookmark, or shared link). The panel does not keep a session-scoped run-history widget.
- Q: How are agents identified in the event log? → A: Show the agent's role/component id. When the panel observes more than one instance of the same role in the current run, append a short instance suffix (e.g., the first ~6 characters of the instance id) so the instances are distinguishable. Single-instance roles stay clean (no suffix). Full UUIDs are not displayed inline.
- Q: How long does the panel wait before flagging a run with no recent events as inactive? → A: 60 seconds. After 60 seconds of silence while the run is still `IN_PROGRESS`, the panel surfaces a non-alarming "no recent activity" notice. The notice is informational; the run is not declared failed and continues to run.
- Q: Can the user stop / cancel an in-progress run from the sample panel? → A: Yes. The panel exposes a stop control on runs that are still in progress, gated by a confirmation step before the stop signal is sent. After stop, the run is shown as cancelled with reason `"operator"`, which is the existing terminal-state handling.
- Q: On approval-gated runs (e.g. *publishing*), what does the panel show alongside the operator action controls? → A: The panel MUST fetch and render the artifact the operator needs to review (the result of the dependency task — e.g. the draft body for *publishing*) inline alongside the approve/reject controls. The artifact display is required, not optional; without it the controls would be blind.
- Q: Should *struggles* (recoverable) and *terminal failures* be visually distinct from each other, not just from healthy events? → A: Yes — three visual tiers: healthy, struggle / recoverable (iteration failed, dependency wait, result rejected), and terminal failure (task failed, task cancelled, run failed). Differentiation uses both color and icon/label so the distinction is robust to colorblind viewers and to the chosen theme.
- Q: Is per-run token usage / iteration count surfaced in the UI? → A: Yes, in two places: each `IterationCompleted` event row is annotated with that iteration's token usage (input/output), AND each panel shows a compact run-summary line with cumulative input/output tokens and iteration count for the current run, updated live as events arrive. Only per-run, not aggregated across runs (cross-run dashboards remain out of scope).
- Q: What does the UI show when the user opens a run URL that does not exist (typo, garbage-collected, or service restarted)? → A: A minimal 404 message. The user is responsible for navigating back themselves; no auto-redirect, no recovery widget, no fallback to the empty form.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run a sample and read the final result (Priority: P1)

A developer or evaluator opens the playground in a browser, picks one of the autonomous agent samples (for example *helloworld*, *research*, or *publishing*), enters the goal or task input that sample asks for, submits it, and — when the agent(s) finish — reads the final result rendered for that sample.

**Why this priority**: This is the core value of the feature. Without it the playground stays a curl-and-tail-the-logs experience. Every demo, evaluation, and ad-hoc test starts here. Even with no other story shipped, this one alone turns the playground into something a non-author can use.

**Independent Test**: Open the *helloworld* panel, type "What is the capital of France?", submit, wait until the panel shows the final answer text. Repeat for at least two more samples that have different input shapes (e.g. *docreview* takes a document plus instructions; *publishing* takes a topic). Each must accept input, run, and display the typed result of that sample without hand-editing JSON.

**Acceptance Scenarios**:

1. **Given** a fresh browser session at the playground root, **When** the user clicks into the *helloworld* panel, types a question, and submits, **Then** the panel shows that a run is in progress, and once the agent finishes the final answer text appears in the panel.
2. **Given** a sample whose input is structured (e.g. *docreview* expects a document body and reviewer instructions), **When** the user fills the form fields, **Then** each field is labeled and shaped to match what that sample requires — not a generic JSON box.
3. **Given** a viewer who has never seen this sample before, **When** they open the panel, **Then** a short description of what the sample does and what coordination pattern it demonstrates is visible before they submit any input — so they can decide what to type without first reading the README.
4. **Given** a run that has produced a final result, **When** the user copies the URL and opens it in a new tab, **Then** the same finished run with the same result is shown without re-submitting.
5. **Given** a sample that exposes operator actions while running (e.g. *publishing* approve/reject of an editorial decision), **When** the run reaches a state expecting that input, **Then** the panel exposes the relevant action(s) inline and submitting the action lets the run continue.
6. **Given** a run that ends in failure, **When** the agent reports the failure to the runtime, **Then** the panel shows that the run failed and surfaces the failure reason rather than spinning silently.

---

### User Story 2 - See live progress and the history of progress events (Priority: P2)

While a run is in progress, the user watches a live stream of progress events from the agent(s) — what task started, which agent picked it up, when an iteration completed, when a delegation fanned out, when a team member joined, when a dependency resolved — and can scroll back through earlier events as a history. Struggles (iteration failures, dependency waits, retries) and outright failures are visually distinct from healthy progress.

**Why this priority**: A chunk of what makes autonomous agents interesting is *how* they get to the answer, not just *that* they get there. Without this, the UI is a slow box: input goes in, output eventually comes out. With this, it becomes a teaching tool — observers can see delegation, handoff, team formation and recovery happen in real time. It also makes mid-run debugging tractable.

**Independent Test**: Start a multi-agent sample (e.g. *research* or *debate*). Without refreshing, observe events appearing in the panel as they happen — at minimum: agent activated, task started, task completed, agent deactivated. Scroll back through the event list to confirm history is preserved. Trigger a failure path (e.g. by submitting input known to push the agent past its iteration budget, or by stopping a downstream component) and confirm that the failed/struggling events are clearly distinguishable from healthy ones.

**Acceptance Scenarios**:

1. **Given** an in-progress run on a multi-agent sample, **When** the runtime publishes lifecycle, task, delegation, handoff, or team events, **Then** each event appears in the panel within a few seconds with a timestamp, the agent it relates to, and a human-readable summary.
2. **Given** a run with many events, **When** the user scrolls the event area, **Then** the full history of events received so far is browsable, ordered by time.
3. **Given** events from each of the three visual tiers (healthy, struggle/recoverable, terminal failure), **When** they are rendered side by side in the event log, **Then** each tier is distinguishable from the other two at a glance — using both color and icon/label so the distinction is preserved for colorblind viewers and across light/dark themes.
4. **Given** a run is still pending after a long time, **When** the user looks at the panel, **Then** it is clear whether the agent is actively iterating, blocked on a dependency, or simply idle — not just "loading".
5. **Given** the page is reloaded mid-run, **When** the panel reopens, **Then** events that were already published before reload are not retroactively required to reappear, but live events from the moment of reload onwards must continue to stream and the current run status must be visible. (See Assumptions for retention.)

---

### User Story 3 - Akka-styled UI with light / dark / platform color toggle (Priority: P3)

The UI looks like it belongs to the Akka product family — the existing Akka default style. A control on every page lets the user switch between three color modes: explicit light, explicit dark, or "platform" (follow the operating system / browser preference). The choice persists across reloads.

**Why this priority**: Visual coherence. Once the UI works (P1) and is informative (P2), this makes it presentable in demos, screenshots, and shared-screen sessions. It is a polish layer, not a blocker for any functional flow.

**Independent Test**: Open any sample panel. Confirm the visual style (palette, typography, spacing, status colors) matches the existing Akka default style. Toggle between light, dark, and platform; the visible theme changes accordingly. With "platform" selected, change the operating system color preference and confirm the UI follows. Reload the page; the previously chosen mode is restored.

**Acceptance Scenarios**:

1. **Given** the user is on any sample panel, **When** they open the theme control, **Then** they see three options: light, dark, platform.
2. **Given** the user picks "dark", **When** the page renders, **Then** all panels, controls, status badges, and event entries use the dark variant of the Akka style.
3. **Given** the user picks "platform" and the OS is set to dark mode, **When** the page renders, **Then** the dark variant is shown; switching the OS to light flips the UI without further user action.
4. **Given** the user has chosen a mode, **When** they reload or revisit the playground later, **Then** that mode is restored.

---

### Edge Cases

- A sample's POST endpoint accepts the submission but the run never produces a final result (e.g. agent stuck in iteration loop, downstream LLM unavailable). The panel must show this state rather than appear stuck on "loading" indefinitely.
- The user submits a second run for the same sample while a previous run is still in progress. The panel navigates to the new run's URL; the previous run remains reachable via browser back or its own URL and continues to make progress in the background. The UI must not interleave state from the older run into the newer.
- The user navigates away from a sample panel mid-run. Returning to the same run (via URL or history) must show its current state, not a fresh empty panel.
- A sample whose final output is large (long article from *publishing*, full debate synthesis from *debate*) must still render readably, with whatever wrapping/scroll affordance is appropriate.
- A sample whose final result is structured (e.g. an answer with a confidence score, a research brief with title, summary and key findings) renders each meaningful field, not a raw JSON dump.
- Approval-gated samples: if the operator never acts, the run sits in "waiting for input" — that state must be obvious and the action controls must remain available.
- If the runtime publishes a notification type the UI does not yet specifically know about, the event must still appear in the history with a sensible default rendering rather than crash or be silently dropped.
- Multiple browser tabs viewing the same run should both see live updates without one tab's actions corrupting the other's view.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST present a landing surface that lists every autonomous agent sample currently shipped with the playground project and lets the user open the panel for any of them.
- **FR-002**: The system MUST provide a dedicated panel/page for each sample, tailored to that sample's input shape, coordination pattern, and result shape — not a generic catch-all form.
- **FR-002a**: Each sample panel MUST display a structured description of what that sample does, positioned so a first-time viewer can read it before submitting input. The description MUST follow the same structure used in the README sample sections — a short overview, the participating agents and their roles, the tasks involved, the flow, and what the sample demonstrates — adapted for web display (concise, well-spaced) rather than rendered verbatim from the README. The description content MUST be curated and shipped together with the UI; it does NOT need to be fetched from the README at runtime.
- **FR-003**: Each sample panel MUST expose an input form whose fields match what that sample expects as goal/task input (e.g. a single question for *helloworld*; a document body and reviewer instructions for *docreview*; a research topic for *research*; an editorial topic for *publishing*).
- **FR-004**: Submitting the input form MUST start a run on that sample using the existing backend behavior, with no requirement for the user to construct request bodies by hand.
- **FR-005**: After submission, the panel MUST show the run's status (e.g. running, awaiting input, completed, failed, cancelled) without requiring the user to manually refresh.
- **FR-006**: When a run completes successfully, the panel MUST render the final result in a layout shaped to that sample's result type — meaningful fields surfaced, not a raw payload dump.
- **FR-007**: When a run fails, the panel MUST render a failure indicator and surface the failure reason text in a place the user can read it.
- **FR-008**: Each run MUST be addressable by URL so a user can share, bookmark, or reopen a specific run and see its current state.
- **FR-008a**: When the user submits a new run from a sample panel, the panel MUST navigate to the new run's URL and replace its current view. Prior runs MUST remain reachable via their URLs (browser back, bookmark, or shared link); the panel is NOT required to expose a session-scoped run-history widget.
- **FR-008b**: When the user opens a run URL that does not correspond to a known run (bad id, typo, garbage-collected, or the playground service has been restarted since the run was created), the UI MUST render a minimal 404 page. No automatic redirect, no fallback to the sample's empty input form, and no in-page recovery widget are required — the user is expected to navigate back themselves.
- **FR-009**: While a run is in progress, the panel MUST display progress events emitted by the agent runtime (lifecycle, task, delegation, handoff, team, backlog, dependency wait, etc.) in near real time as they are emitted.
- **FR-009a**: If the live progress-event stream disconnects mid-run, the UI MUST automatically attempt to reconnect for future events and MUST re-fetch the run's status (and final result, if available) so terminal outcomes — completed, failed, or cancelled — are never missed by the user. Intermediate events that occur during the disconnect window MAY be lost; the system is not required to maintain a server-side per-run event log to replay them.
- **FR-009b**: While the live stream is disconnected and reconnect attempts are ongoing, the panel MUST visibly indicate that live updates are temporarily unavailable so the user does not interpret the absence of new events as an idle agent.
- **FR-010**: The panel MUST keep a scrollable history of all progress events it has observed for the current run, ordered by time, each carrying at minimum a timestamp, an indication of which agent/task it relates to, and a human-readable summary.
- **FR-010a**: When an event row references an agent, the panel MUST display the agent's role/component id. If the panel has observed more than one instance of the same role within the current run, it MUST also append a short instance suffix (e.g. the first few characters of the instance id) to disambiguate instances. The full instance UUID MUST NOT be shown inline in event rows; if the user needs the full id (for log correlation), it MAY be exposed on demand (e.g. tooltip or expanded view).
- **FR-011**: Events MUST be rendered in three visual tiers, distinguishable at a glance by both color *and* icon/label (so the distinction survives colorblind viewers and theme switches):
  - **Healthy** — progress events that represent normal forward movement (agent activated, iteration started/completed, task started/completed, delegation/handoff/team formation, etc.).
  - **Struggle / recoverable** — events that indicate friction but are not terminal for the run (iteration failed, dependency wait that is currently blocking progress, result rejected by a guard rule, retries).
  - **Terminal failure** — events that mean a task or the run will not recover (task failed, task cancelled, run failed).
- **FR-011a**: When a run is in progress and no progress events have been received for 60 seconds, the panel MUST surface a non-alarming "no recent activity" notice so the user can distinguish silence from healthy iteration. The notice is informational only — the run is NOT to be declared failed by the UI on this basis, and the notice MUST clear automatically as soon as new events arrive or the run reaches a terminal state.
- **FR-012**: Events for which the UI does not have a specific renderer MUST still appear in the history with a default rendering rather than being dropped or causing the panel to error.
- **FR-012a**: Each event row that represents an `IterationCompleted` notification MUST display the iteration's token usage (input and output token counts as carried by the runtime). Other event rows do not require token annotations.
- **FR-012b**: Each sample panel MUST display a compact, always-visible run-summary line showing the run's cumulative input tokens, cumulative output tokens, and total iteration count, updated live as events arrive. The summary is per-run only; cross-run aggregation remains out of scope.
- **FR-013**: For samples that include operator actions during a run (e.g. approve/reject in *publishing*), the panel MUST surface those actions inline at the appropriate point in the run and allow the operator to invoke them from the UI. When the operator decision depends on reviewing an artifact produced earlier in the run (e.g. the draft post that approval is gating in *publishing*), the panel MUST fetch and render that artifact inline alongside the action controls so the operator can read it before deciding. A panel that exposes the controls without the artifact is non-conforming.
- **FR-013a**: For any run that is still in progress, the panel MUST expose an operator-driven stop control. Activating the stop control MUST require a confirmation step before the stop signal is sent. Once stop is confirmed, the panel MUST send the stop and treat the resulting run state as cancelled with reason `"operator"`, surfaced via the existing terminal-state handling (FR-005, FR-007).
- **FR-014**: The visual style of the UI MUST be based on the Akka default style already present in the project's documentation context, including its palette, typography, status colors, and component primitives (buttons, inputs, badges, progress indicators, dialogs).
- **FR-015**: A theme control MUST be available on every page offering three modes: light, dark, and platform (follow operating system / browser preference).
- **FR-016**: The selected theme MUST persist across page reloads and revisits within the same browser.
- **FR-017**: When the theme is set to "platform" and the operating system color preference changes, the UI MUST follow without requiring the user to take further action.
- **FR-018**: The web UI MUST be served by the playground service itself so that running the playground locally is sufficient to use it — no separate frontend dev server, build step, or external host needed at runtime.
- **FR-019**: The UI MUST work in current versions of the major desktop browsers (Chromium-family, Firefox, Safari).
- **FR-020**: The UI MUST be navigable with keyboard alone for the core flows (open a sample panel, fill the input form, submit, read the result, switch themes).

### Key Entities

- **Sample panel**: A per-sample surface in the UI. Knows the sample's display name, a structured description (overview · agents · tasks · flow · demonstrates) curated for the UI and shipped with it, the shape of its input form, the shape of its final result, and any operator actions it can expose mid-run.
- **Run**: One execution of a sample, identified so it can be reopened by URL. Has a current status (pending/running/awaiting input/completed/failed/cancelled), a final result when applicable, and an associated stream of progress events.
- **Progress event**: A single notification emitted by the runtime during a run. Carries a category (lifecycle / task / delegation / handoff / team / backlog / dependency / other), a timestamp, identifying references to the agent and task it relates to, and any category-specific details (e.g. failure reason, token usage, dependency id).
- **Final result**: The outcome of a successful run, rendered in a sample-specific layout.
- **Failure record**: The outcome of an unsuccessful run — at minimum a reason and the event(s) that led to the failure.
- **Theme preference**: The user's choice of light, dark, or platform — persisted per browser.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user new to the project can open the playground in a browser, run any sample end-to-end (input → final result or failure), and read the outcome without consulting documentation or the terminal — typically within 90 seconds for fast samples.
- **SC-002**: Every autonomous agent sample currently shipped with the project has its own working panel reachable from the landing surface; no sample is reachable only through curl or terminal logs.
- **SC-003**: Users can identify whether a long-running run is making progress, blocked on a dependency, awaiting human input, or stuck/failing — without reading server logs.
- **SC-004**: Progress events appear in the panel within roughly 2 seconds of being emitted by the runtime under nominal local conditions.
- **SC-005**: Healthy, struggle/recoverable, and terminal-failure events are each recognizable at a glance — distinguishable from one another, not just from "healthy" — in both light and dark themes, including for colorblind viewers (color is paired with icon/label).
- **SC-006**: A run's URL can be shared or bookmarked and, when reopened, restores the same view of status and final result (where applicable).
- **SC-007**: When a run fails, the user can read the failure reason inside the panel without inspecting any other system.
- **SC-008**: The chosen theme (light / dark / platform) is respected on first paint after reload — no visible flash to the wrong theme.
- **SC-009**: The UI works consistently across the latest stable Chromium-family, Firefox, and Safari without sample-specific breakage.
- **SC-010**: All core flows (pick a sample, submit input, observe progress, view result, switch theme, invoke an operator action) are operable with keyboard only.

## Assumptions

- "Platform colors" in the user description means *follow the operating system / browser color-scheme preference*, mapping cleanly onto the existing light/dark variants of the Akka default style. The toggle therefore offers three modes: light, dark, platform.
- The set of samples covered is "every autonomous agent sample currently present in the project source tree". At the time of writing, that is the twelve samples *helloworld*, *pipeline*, *docreview*, *dynamic*, *research*, *consulting*, *support*, *publishing*, *debate*, *negotiation*, *peerreview*, and *devteam*. Samples described in the README as *not yet implemented* (currently *compliance*, *brainstorm*, *editorial*, *deepdive*) are out of scope here and become in scope when they are added.
- Progress event history shown to the user is the events the UI has *observed* since the panel began watching the run. Events emitted before a freshly opened panel started observing are not required to be replayed by this feature. If long-term retention of event history across reloads turns out to matter for a specific sample, that is a follow-up concern, not part of this MVP.
- The UI is assumed to run alongside the playground service; it does not need to authenticate users beyond whatever the existing endpoints already require.
- The existing per-sample HTTP endpoint contracts (POST to start, GET to fetch status/result, sample-specific operator actions where present) are sufficient as the contract the UI submits and reads against; this feature does not require redesigning those endpoints, only consuming them and adding a runtime notification subscription where needed.
- The Akka default style file already in the project's documentation context is the visual reference — colors, typography, components, and dark/light handling are taken from there rather than redesigned.

## Out of Scope

- Persisting user-submitted runs or results beyond what the existing backend already does.
- Authentication, user accounts, multi-tenant access control, or per-user run history beyond what each browser session sees.
- A unified "compare samples side by side" or "rerun with different input" workflow across samples.
- Editing or building agents from the UI.
- Mobile-first or small-touchscreen layouts (best-effort only; primary target is desktop browsers).
- Internationalization / localization of UI strings.
- Telemetry, analytics, or token-cost dashboards aggregated across runs.
- Adding panels for samples described in the README as *not yet implemented*.
