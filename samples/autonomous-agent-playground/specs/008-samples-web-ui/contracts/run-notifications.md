# Contract — Run notification stream (Server-Sent Events)

**Endpoint class**: `demo.ui.api.RunNotificationEndpoint`
**ACL**: `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))` at the class level.

This endpoint is the live event bridge from the Akka SDK's `componentClient.forAutonomousAgent(...).notificationStream()` to the browser, formatted as Server-Sent Events.

---

## Route

### `GET /playground/api/runs/{runId}/events?component={agentComponentId}`

Subscribes to the notification stream of the agent identified by `(agentComponentId, runId)` and emits each notification as an SSE frame containing an `EventEnvelope`.

- **Request**:
  - `runId` path parameter — the owning agent's UUID instance id.
  - `component` query parameter — the owning agent's component id (e.g. `research-coordinator`). Required because the SDK's `forAutonomousAgent` needs the `Class` corresponding to that component id; the endpoint resolves the class via `SampleRegistry.classFor(component)`. Putting it in the URL keeps the bookmark / share design self-contained: the run's URL plus the component id is enough to re-subscribe on reload.
  - Optional `Last-Event-ID` header — set automatically by the browser's `EventSource` on reconnect, used by the SDK's `serverSentEvents(source, extractEventId)` to pick up after the last delivered event.

- **Response**: `200 OK`, `Content-Type: text/event-stream`. Each SSE frame:

  ```
  id: <eventId>\n
  event: notification\n
  data: <JSON-encoded EventEnvelope>\n
  \n
  ```

  Where `<EventEnvelope>` matches the data-model.md shape:

  ```json
  {
    "eventId": 42,
    "timestamp": "2026-04-27T13:14:15.123Z",
    "tier": "healthy",
    "category": "task",
    "kind": "TaskStarted",
    "raw": {
      "taskId": "…",
      "taskName": "…"
      /* SDK Notification fields, Jackson-serialized */
    }
  }
  ```

  The bridge emits a 5-second keep-alive frame (`: keep-alive\n\n`) when idle (provided by the SDK's `serverSentEvents` builder).

- **Errors**:
  - `404 Not Found` if `SampleRegistry.classFor(component)` does not know the component id.
  - The SSE stream simply ends if the agent instance does not exist or the agent has stopped; the browser's `EventSource` will attempt reconnect, and on reconnect the UI fetches `/runs/{runId}/status` to recover the terminal state (FR-009a).

- **Cancellation**: the browser closes the `EventSource`; the bridge's source is cancelled and the underlying notification subscription is released.

---

## Tier classification rule

Server-side, the bridge maps each `Notification` to a `tier` string before emitting:

| Predicate | `tier` |
|---|---|
| `n instanceof Notification.StruggleNotification` | `struggle` |
| `n instanceof Notification.IterationFailed` | `struggle` |
| `n instanceof Notification.TaskResultRejected` | `struggle` |
| `n instanceof Notification.TaskDependencyWait` | `struggle` |
| `n instanceof Notification.TeamMemberSetupFailed` | `terminal_failure` |
| `n instanceof Notification.TaskFailed` | `terminal_failure` |
| `n instanceof Notification.TaskCancelled` | `terminal_failure` |
| `n instanceof Notification.Stopped && "operator".equals(n.reason())` | `terminal_failure` |
| any other | `healthy` |

`category` is derived from the marker sub-interface (`LifecycleNotification` → `lifecycle`, etc.).

`kind` is the simple class name of the concrete record, e.g. `n.getClass().getSimpleName()`.

---

## Test contract

`RunNotificationEndpointIntegrationTest` (uses `TestKitSupport` + `httpClient`):

1. **Missing component**: `GET /playground/api/runs/<uuid>/events?component=unknown-id` returns 404.
2. **Empty stream**: subscribing to a freshly-created agent with no work assigned returns `text/event-stream` and emits keep-alive frames; the test reads the first 1–2 frames and confirms the content type.
3. **Live event**: in a TestKit setup, post a helloworld run, then subscribe to its events stream; assert that within a few seconds the test sees an `Activated` lifecycle event and a `TaskStarted` task event encoded as `EventEnvelope` JSON with `tier == "healthy"`.
4. **Tier classification**: feed a known `TaskFailed` notification through the bridge (in-process unit-style test of the classifier function) and assert `tier == "terminal_failure"`. Repeat for `IterationFailed` (`struggle`) and `TaskStarted` (`healthy`).
5. **Last-Event-ID resume**: not asserted in integration (would require deterministic event ordering across runs); covered by manual quickstart step.

---

## Why a query parameter for `component`, not part of the path?

A path of the form `/runs/{component}/{instanceId}/events` would also work. Putting `component` in the query keeps the *run id* — the user-visible portion of the URL surfaced by FR-008 — clean and identical across the events / status / stop endpoints (`/runs/{runId}/...`). The browser-side router treats the run URL as `/playground/<sample>/run/<runId>` only; the `component` parameter is internal plumbing the UI carries in the SSE subscription URL.
