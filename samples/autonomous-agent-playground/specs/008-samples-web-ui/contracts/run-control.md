# Contract — Run status & control

**Endpoint class**: `demo.ui.api.RunControlEndpoint`
**ACL**: `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))` at the class level.

This endpoint covers the synchronous read and control routes the UI uses on top of the SSE event stream: status snapshot on URL load, operator stop, and 404 handling.

---

## Routes

### `GET /playground/api/runs/{runId}/status?component={agentComponentId}&task={primaryTaskId}&sample={sampleId}`

Returns the consolidated `RunStatus` DTO (see `data-model.md`). The UI calls this on first load of a run URL (so it can render even before the SSE stream produces its first event) and on every SSE reconnect (so terminal outcomes are not missed; FR-009a).

- **Path**: `runId` is the owning agent's instance id.
- **Query**:
  - `component` — owning agent's component id, used to resolve the agent class via `SampleRegistry`.
  - `task` — primary task id (the one whose typed result becomes the run's `finalResult`).
  - `sample` — sample id; lets the endpoint pick the right `Task<T>` definition for `forTask(taskId).get(taskDef)`.

  All three are required. The endpoint validates: any unknown `component` or `sample` ⇒ `404`; missing `task` ⇒ `400`.

- **Response**: `200 OK`, `application/json`, body conforms to `RunStatus`:

  ```json
  {
    "runId": "…",
    "sampleId": "research",
    "agentComponentId": "research-coordinator",
    "taskId": "…",
    "agentPhase": "PHASE_RUNNING",
    "agentPaused": false,
    "taskStatus": "IN_PROGRESS",
    "runState": "RUNNING",
    "finalResult": null,
    "failureReason": null,
    "startedAt": "2026-04-27T13:14:15.123Z",
    "endedAt": null
  }
  ```

- **Errors**:
  - `404` when the run does not exist (unknown agent instance, unknown component, unknown sample, or task lookup returns "not found"). This is the FR-008b path.
  - `400` for missing required query parameters.

### `POST /playground/api/runs/{runId}/stop?component={agentComponentId}`

Sends operator-driven stop to the owning agent. The UI invokes this only after a confirmation dialog (FR-013a).

- **Request**: empty body. `runId` and `component` identify the agent. `Content-Length: 0`.
- **Implementation**:
  ```java
  componentClient.forAutonomousAgent(SampleRegistry.classFor(component), runId).stop();
  ```
- **Response**: `200 OK`, `application/json`:
  ```json
  { "runState": "CANCELLED", "stoppedAt": "2026-04-27T13:14:18.000Z" }
  ```
  The endpoint returns immediately after issuing `.stop()`. The UI does not depend on this response for the actual state transition — the `Stopped` notification arriving on the SSE stream is what flips the panel into the cancelled state.
- **Errors**:
  - `404` if the agent instance is unknown.
  - The endpoint is **idempotent**: calling stop on an already-stopped agent returns `200 OK` with `runState: "CANCELLED"` and an `stoppedAt` reflecting the original stop.

### `GET /playground/api/samples`

Returns the list of samples the UI should show on its landing page. Implementation reads the same `SampleRegistry` keys.

- **Response**: `200 OK`, `application/json`:
  ```json
  {
    "samples": [
      { "id": "helloworld",  "displayName": "Hello world",        "agentComponentId": "question-answerer" },
      { "id": "pipeline",    "displayName": "Pipeline",           "agentComponentId": "report-agent" },
      { "id": "research",    "displayName": "Research",           "agentComponentId": "research-coordinator" },
      …
    ]
  }
  ```
  This keeps the front-end's sample list in sync with the backend without hard-coding it twice. (The per-sample descriptors in the front-end still live in `static-resources/playground/static/samples/<id>.js`; the registry is the union of "implemented samples".)

---

## Per-sample POST endpoints — additive contract change

The existing per-sample `POST /<sample>` endpoints (e.g. `POST /questions`, `POST /research`, `POST /debate`, etc.) currently return only `{taskId}` (and pipeline returns multiple task ids). This feature **extends** each existing response shape with the owning agent's component id and instance id. Renaming or removing existing fields is out of scope.

For example, `QuestionEndpoint.QuestionResponse` becomes:

```java
public record QuestionResponse(
    String taskId,            // existing
    String runId,             // NEW: == agentInstanceId of the QuestionAnswerer instance
    String agentComponentId   // NEW: "question-answerer"
) {}
```

The change is mechanical: each sample endpoint already constructs `var agentInstanceId = UUID.randomUUID().toString()` immediately before calling `runSingleTask`; we surface that variable as `runId` in the response and add a constant `agentComponentId` matching the agent class's `@Component(id = …)` value.

| Sample | Existing response | Owning-agent `componentId` to add |
|---|---|---|
| helloworld | `QuestionResponse(taskId)` | `question-answerer` |
| pipeline | `PipelineResponse(pipelineId, collectTaskId, analyzeTaskId, reportTaskId)` | `report-agent` (with `runId` = the agent's instance id) |
| docreview | `ReviewResponse(id)` | `document-reviewer` |
| dynamic | `TaskResponse(taskId)` × 2 routes | `dynamic-agent` (per-route instance) |
| research | `ResearchResponse(id)` | `research-coordinator` |
| consulting | `ConsultingResponse(id)` | `consulting-coordinator` |
| support | `SupportResponse(id)` | `triage-agent` |
| publishing | `PublishingPipeline(draftTaskId, approvalTaskId, publishTaskId)` | `content-agent` (the run's owning agent for the *draft* phase; `publish-agent`'s instance id is internal) |
| debate | `DebateResponse(taskId)` | `debate-moderator` |
| negotiation | `NegotiationResponse(taskId)` | `facilitator` |
| peerreview | `ReviewResponse(taskId)` | `review-moderator` |
| devteam | `ProjectResponse(taskId)` | `project-lead` |

A small note for **publishing**: the run's "primary task" is the `DRAFT` task. The UI's run URL points at the ContentAgent's instance id; the panel's progress display covers DRAFT → AWAITING_INPUT (approval) → PUBLISH and the final `PublishedPost`. The PublishingAgent is treated as a delegated child for display purposes.

---

## Test contracts

`RunControlEndpointIntegrationTest`:

1. `GET /playground/api/runs/{unknownId}/status?component=question-answerer&task=…&sample=helloworld` → 404.
2. `GET /playground/api/runs/{validId}/status?component=…&task=…&sample=helloworld` immediately after submission → 200 with `runState ∈ {PENDING, RUNNING}` and `finalResult == null`.
3. After `Awaitility.await()` until task completion → 200 with `runState == COMPLETED` and `finalResult` carrying the typed answer.
4. `POST /playground/api/runs/{validId}/stop?component=…` returns 200 with `runState == CANCELLED`. A subsequent `GET /status` returns `runState == CANCELLED`.
5. `POST /playground/api/runs/{validId}/stop?component=…` called twice in sequence — second call returns 200 (idempotent), not an error.
6. `POST /playground/api/runs/{unknownId}/stop?component=question-answerer` → 404.

Per-sample POST tests already exist; this feature adds an assertion that each existing response includes the new `runId` and `agentComponentId` fields, and that those values can be used by the new endpoints to fetch a valid status.
