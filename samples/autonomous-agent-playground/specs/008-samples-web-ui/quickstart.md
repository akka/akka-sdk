# Quickstart — Manual verification of the Web UI

**Feature**: 008-samples-web-ui
**Audience**: a developer or reviewer verifying that the implemented UI satisfies the spec end-to-end.
**Pre-requisites**: a working local checkout that compiles (`mvn compile`) and an `OPENAI_API_KEY` (or whichever provider is configured in `application.conf`) exported in the shell.

This document is the manual recipe that complements the backend integration tests. It walks each user story and edge case in `spec.md` and gives concrete actions plus expected observations. The goal: a reviewer can run through it in roughly 15 minutes and convince themselves the feature is correct without reading any source.

---

## 0. Boot

```bash
mvn compile -q
mvn exec:java -q
```

Wait for `Started service in <region>` in the logs. Open `http://localhost:9000/`. You should land on `/playground` (the root redirect).

✅ The landing page shows a list of every sample currently shipped: helloworld, pipeline, docreview, dynamic, research, consulting, support, publishing, debate, negotiation, peerreview, devteam. Each row has the sample's name and short description.
✅ A theme toggle is visible somewhere on every page (typically a control in the header).
✅ The default theme follows the operating system's color preference (the toggle reads "Platform").

---

## 1. User Story 1 — Run a sample and read the final result (P1)

### 1a. helloworld (single-agent, single-task)

1. Click **Hello world** on the landing page.
2. Expected:
   - The panel shows a structured description (overview · agents · tasks · flow · demonstrates) before any input is entered.
   - The input form is a single labeled text field for the question. No JSON editor.
3. Type "What is the capital of France?" and click **Submit**.
4. Expected:
   - The URL changes to `/playground/helloworld/run/<runId>` where `<runId>` is a UUID.
   - The status badge moves from PENDING → RUNNING.
   - Progress events begin appearing in the event log: at minimum `Activated`, `IterationStarted`, `IterationCompleted`, `TaskStarted`, `TaskCompleted`, `Deactivated`.
   - After completion the **Final result** panel renders the structured answer (`answer` text and `confidence` number) — not a JSON dump.

### 1b. docreview (structured input)

1. Back to landing → **Document review**.
2. Expected: the input form has *two* labeled fields, a multi-line textarea for the document body and a single-line field for review instructions.
3. Paste a small policy paragraph and an instruction like "flag any GDPR concerns". Submit.
4. Expected: the final result renders `assessment`, `findings`, and a clear `compliant: true|false` indicator — each as its own field, not a JSON dump.

### 1c. publishing (operator action mid-run)

1. Landing → **Publishing**. Submit a topic, e.g. "the future of edge AI".
2. Expected:
   - Event log shows the draft phase running.
   - Status moves to **AWAITING_INPUT** when the draft is ready.
   - The panel shows the **draft body** inline (FR-013 artifact-display requirement) and exposes **Approve** and **Reject** buttons.
   - Approving drives the run to COMPLETED with a `PublishedPost` final result.
3. Repeat the run; this time **Reject** with a reason. Expected: the run moves to FAILED with the rejection reason in the failure panel.

### 1d. URL share/round-trip

1. Copy the URL of a completed run (any sample). Paste into a fresh browser tab.
2. Expected: the same finished run with the same final result is shown immediately, no re-submission. The event log may be empty (events emitted before the new tab opened are not retroactively replayed) — that's expected per the Assumptions in `spec.md`.

### 1e. Failure visibility

1. Open helloworld. Submit a deliberately impossible task ("write a 10,000-line Java file with no imports") that pushes the agent past its iteration budget.
2. Expected:
   - At some point a struggle event appears in the event log (e.g. `IterationFailed`, `TaskApproachingMaxIterations`) with a distinct color and icon.
   - Eventually the run terminates with FAILED and the failure reason is visible in the failure panel.

---

## 2. User Story 2 — Live progress + history + struggle/failure visibility (P2)

### 2a. Multi-agent live updates

1. Landing → **Research**. Submit a topic, e.g. "Akka SDK in 2026".
2. Expected events arriving live, without page reload:
   - `Activated` (research-coordinator)
   - `DelegationStarted` carrying two children (researcher and analyst)
   - `TaskStarted` and `TaskCompleted` referencing each child
   - `DelegationResolved` with `succeeded: 2, failed: 0`
   - `Deactivated`
3. Each row carries a timestamp, the agent identifier, and a one-line summary.

### 2b. Multi-instance disambiguation (FR-010a)

1. Landing → **Devteam**. Submit a small project description with two or three deliverables (e.g. "build a CLI for converting CSV to JSON, plus tests, plus README").
2. Expected: while only one developer instance is active, the event log shows just `developer`. The moment a *second* developer instance appears in the events, the panel re-labels both rows so each shows `developer <first-6-of-instanceId>`.
3. Hover or expand an event row → the full instance UUID is available on demand (tooltip / detail view).

### 2c. Three visual tiers (FR-011)

1. While a run is in flight, induce a struggle (e.g. submit research for an extremely vague topic so the LLM retries; or use docreview with intentionally contradictory instructions). Force a terminal failure if needed by stopping a downstream component.
2. Expected: healthy events render in the brand neutral/ok color, struggle events render in the warn color (and carry a distinct icon/label), terminal-failure events render in the err color. The distinction is preserved when switching to dark theme. Switch to a colorblind-simulator extension and confirm the icon/label still distinguishes the three tiers.

### 2d. Stuck-run notice (FR-011a)

1. With a long-running sample (e.g. a multi-step research run), wait for a stretch where the LLM is "thinking" with no events for >60 seconds.
2. Expected: a non-alarming "no recent activity" notice appears on the panel (e.g. yellow banner, dismissible). The run is **not** declared failed; status remains RUNNING. The notice clears the moment a new event arrives.

### 2e. Stream resilience (FR-009a, FR-009b)

1. While a run is in progress, simulate a network blip: open browser dev-tools → Network → toggle "Offline" for ~10 seconds, then back online.
2. Expected:
   - Within seconds of going offline a small "live updates unavailable" indicator appears on the panel.
   - When connectivity returns the indicator clears, the SSE stream reconnects, and the panel re-fetches the run status. If the run terminated while offline, the terminal state is now reflected and the final result rendered.

### 2f. Reload mid-run

1. Mid-run, hit browser refresh.
2. Expected: the panel re-renders, fetches `/status`, and the SSE stream picks up live events from that point forward. Events emitted while you were navigating are not retroactively shown — that's the documented Assumption.

---

## 3. User Story 3 — Theme toggle (P3)

### 3a. Light / dark / platform

1. From any sample panel, open the theme toggle.
2. Click **Dark** → the page switches to dark immediately. All panels, status badges, and event rows use the dark variant of the Akka style.
3. Click **Light** → switches to light immediately.
4. Click **Platform** → the page now follows the operating system's color preference. With macOS / Windows / Linux still set to dark, the page is dark; switch the OS to light without touching the toggle and the page flips to light.

### 3b. Persistence

1. Pick **Dark**. Reload the page.
2. Expected: the page first-paints dark — no flash to a light intermediate. The toggle still reads **Dark**.

### 3c. Visual coherence

✅ Buttons, inputs, badges, dialogs, and progress indicators on every panel match the Akka default style (typography Instrument Sans, mono Roboto Mono, brand colors `--primary` / `--secondary`, status colors `--ok` / `--warn` / `--err`, surface tiers).
✅ The Akka style is the source — no obvious bespoke colors or fonts.

---

## 4. Edge cases & operator actions

### 4a. Stop a run (FR-013a)

1. Start a long-running multi-agent sample (e.g. devteam with a non-trivial project).
2. While RUNNING, click **Stop**. A confirmation dialog appears.
3. Cancel out of the dialog. Run continues.
4. Click **Stop** again, confirm. Expected:
   - A `Stopped` event with `reason: operator` appears on the SSE stream.
   - Run status flips to CANCELLED.
   - The Stop control disappears (or becomes disabled) once terminal.

### 4b. Unknown run id → 404 (FR-008b)

1. Visit `/playground/research/run/00000000-0000-0000-0000-000000000000`.
2. Expected: a minimal 404 page. No auto-redirect, no fallback to the empty form. The user has to navigate back manually.

### 4c. Multi-run in one tab (FR-008a)

1. Submit a helloworld run. While it is still running, click **New question** on the panel and submit a different question.
2. Expected: the panel navigates to the new run's URL and shows the new run. Pressing browser back returns to the prior run's URL, which still shows that run's progress (and may have completed in the meantime).

### 4d. Token / iteration summary (FR-012a, FR-012b)

✅ Every panel shows a compact run-summary line with cumulative input tokens, output tokens, and iteration count, updated live.
✅ Every `IterationCompleted` event row in the event log is annotated with that iteration's token usage.

### 4e. Keyboard navigation (FR-020, SC-010)

1. Without using the mouse, press `Tab` from the landing page. Move focus to a sample link, press Enter, fill the input form, submit, then `Tab` to the theme toggle and switch theme. All interactions reachable.

---

## 5. Sample matrix sanity check (SC-002)

Run a representative submission on every sample. For each, confirm: (a) the panel renders the right input fields; (b) the run reaches a terminal state; (c) the final result is shown with meaningful fields, not a JSON dump.

| Sample | Suggested input | Expected result fields |
|---|---|---|
| helloworld | "What is the speed of light?" | `answer`, `confidence` |
| pipeline | "Akka adoption in fintech" | three `ReportResult` phases (collect / analyze / report) |
| docreview | a short policy paragraph + "GDPR check" | `assessment`, `findings`, `compliant` |
| dynamic | summarize: a paragraph; translate: another paragraph | summary text; French translation text |
| research | "Modular monoliths in 2026" | `title`, `summary`, `keyFindings` |
| consulting | "We're considering an M&A; assess risk" | `assessment`, `recommendation`, `escalated` |
| support | "My invoice is wrong" | `category`, `resolution`, `resolved` |
| publishing | "the future of edge AI" + approve | `PublishedPost` with `url` and timestamp |
| debate | "Is microservices architecture obsolete?" | `topic`, `synthesis`, `keyArguments` |
| negotiation | "Acquiring a 5-year-old SaaS startup at $20M" | `topic`, `outcome`, `finalOffer` |
| peerreview | a short technical doc | `document`, `assessment`, `reviewerFindings` |
| devteam | "build a CLI to convert CSV to JSON" | `ProjectResult` summary |

---

## Done criteria

- All sections above complete without unexpected behavior or backend errors.
- `mvn verify` passes (backend integration tests include the new endpoints).
- The branch's only behavioral change is additive: existing per-sample endpoints work unchanged for callers that ignore the new `runId` / `agentComponentId` response fields.
