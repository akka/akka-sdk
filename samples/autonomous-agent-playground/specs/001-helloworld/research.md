# Research: Hello World Autonomous Agent

**Feature**: 001-helloworld | **Date**: 2026-03-12

## R1: AutonomousAgent lifecycle and API

**Decision**: Use `AutonomousAgent` base class with `strategy()` method. Use `runSingleTask()` for the one-shot pattern.

**Rationale**: The documentation shows `runSingleTask()` as the simplest pattern — it creates the task, starts the agent, and auto-stops the agent when done. This matches the helloworld requirement of one question = one agent instance.

**Alternatives considered**:
- Separate `create()` + `assignTasks()` + manual `stop()` — unnecessary complexity for a single-task sample.
- Request-based `Agent` with `Workflow` orchestration — overkill; AutonomousAgent handles the durable loop natively.

## R2: Task definition and typed results

**Decision**: Define `QuestionTasks.ANSWER` as a `static final Task<Answer>` constant. The `Answer` record has `answer` (String) and `confidence` (int) fields.

**Rationale**: The SDK requires task definitions as immutable constants with `resultConformsTo()`. The LLM output is validated against this schema automatically. The confidence field as int (0–100) matches the spec.

**Alternatives considered**:
- Untyped string results — loses the typed result demonstration, which is the P2 user story.
- Separate task definition class vs. inlining — a dedicated `QuestionTasks` class follows the naming convention (`{Domain}Tasks`) and is reusable.

## R3: Polling for task results

**Decision**: Use `componentClient.forTask(QuestionTasks.ANSWER).get(taskId)` returning a `TaskSnapshot<Answer>`. The snapshot includes `status()` and `result()`.

**Rationale**: The SDK's task snapshot API directly supports the polling pattern. Status can be `PENDING`, `IN_PROGRESS`, `COMPLETED`, or `FAILED`. The endpoint maps these to appropriate HTTP responses.

**Alternatives considered**:
- Notification/streaming — spec explicitly states polling is sufficient and no push notifications are needed.

## R4: Error handling for unknown task IDs

**Decision**: The `get()` call will throw or return an appropriate error for unknown task IDs. The endpoint catches this and returns a 404.

**Rationale**: Spec FR-006 requires appropriate error for unknown identifiers. Standard HTTP 404 semantics.

## R5: Testing approach

**Decision**: Use `TestModelProvider` with `fixedResponse()` mocking the `complete_task` tool invocation. Use `Awaitility.await()` for async polling in tests.

**Rationale**: The documentation shows this exact pattern for autonomous agent testing. The mock response uses `ToolInvocationRequest("complete_task", ...)` to simulate the LLM completing the task with a typed result.

**Alternatives considered**:
- Real LLM calls in tests — non-deterministic, slow, costly; `TestModelProvider` is the standard approach.
