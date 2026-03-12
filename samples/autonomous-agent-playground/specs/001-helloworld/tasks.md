# Tasks: Hello World Autonomous Agent

**Input**: Design documents from `/specs/001-helloworld/`
**Prerequisites**: plan.md, spec.md, data-model.md, research.md

**Tests**: Included — integration tests are essential for validating autonomous agent behaviour.

**Organization**: Tasks grouped by user story. US1 (submit + poll) is the MVP. US2 (typed result verification) layers on top.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- `src/main/java/demo/helloworld/` — source root
- `src/test/java/demo/helloworld/` — test root
- `src/main/resources/` — configuration

---

## Phase 1: Setup

**Purpose**: Project initialization and base configuration

- [x] T001 Configure `application.conf` with LLM model settings in `src/main/resources/application.conf`

---

## Phase 2: Foundational (Domain + Task Definition)

**Purpose**: Domain record and task definition that ALL user stories depend on

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T002 [P] Create `Answer` domain record in `src/main/java/demo/helloworld/domain/Answer.java` — fields: `answer` (String), `confidence` (int)
- [x] T003 [P] Create `QuestionTasks` task definition class in `src/main/java/demo/helloworld/application/QuestionTasks.java` — defines `ANSWER` task with `resultConformsTo(Answer.class)`

**Checkpoint**: `mvn compile` passes. Domain and task definition ready.

---

## Phase 3: User Story 1 — Submit a Question and Get an Answer (Priority: P1) MVP

**Goal**: User submits a question via HTTP POST, system creates an autonomous agent, processes it, and user polls for the result via GET.

**Independent Test**: POST a question to `/questions`, receive a task ID, GET `/questions/{taskId}` until status is COMPLETED, verify answer and confidence are present.

### Implementation for User Story 1

- [x] T004 [US1] Create `QuestionAnswerer` autonomous agent in `src/main/java/demo/helloworld/application/QuestionAnswerer.java` — extends `AutonomousAgent`, strategy with goal, accepts `ANSWER`, `maxIterations(3)`
- [x] T005 [US1] Create `QuestionEndpoint` HTTP endpoint in `src/main/java/demo/helloworld/api/QuestionEndpoint.java` — inner records `QuestionRequest`, `QuestionResponse`, `AnswerResponse`; `POST /questions` creates agent via `runSingleTask`, `GET /questions/{taskId}` queries task snapshot; validate empty question (400), handle unknown task ID (404), handle in-progress status

### Test for User Story 1

- [x] T006 [US1] Create `QuestionAnswererIntegrationTest` in `src/test/java/demo/helloworld/QuestionAnswererIntegrationTest.java` — use `TestModelProvider` with `fixedResponse` mocking `complete_task` tool; test: submit question via HTTP POST and get task ID, poll via HTTP GET until COMPLETED, verify answer and confidence fields; run with `mvn verify`

**Checkpoint**: `mvn verify` passes. Full submit-and-poll flow works end-to-end.

---

## Phase 4: User Story 2 — Typed Result Verification (Priority: P2)

**Goal**: Verify the result is a structured typed object (not raw string) with both fields populated and consistent across multiple retrievals.

**Independent Test**: Submit a question, wait for completion, retrieve result multiple times, verify same structured response each time.

### Implementation for User Story 2

- [x] T007 [US2] Add typed result consistency test in `src/test/java/demo/helloworld/QuestionAnswererIntegrationTest.java` — new test method: submit question, poll until complete, retrieve result twice, assert both retrievals return identical `answer` and `confidence` values

**Checkpoint**: `mvn verify` passes. Typed result consistency verified.

---

## Phase 5: Edge Cases

**Purpose**: Handle edge cases identified in the spec

- [x] T008 Add edge case tests in `src/test/java/demo/helloworld/QuestionAnswererIntegrationTest.java` — test: POST with empty/blank question returns 400; GET with unknown task ID returns 404

**Checkpoint**: `mvn verify` passes. All edge cases handled.

---

## Phase 6: Polish

**Purpose**: Final validation and documentation

- [x] T009 Update `README.md` with curl examples for `POST /questions` and `GET /questions/{taskId}`
- [ ] T010 Run local service with `akka local` and manually verify the endpoints

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: T002 and T003 can run in parallel; BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2; T004 before T005 (endpoint needs agent class); T006 after T005
- **US2 (Phase 4)**: Depends on Phase 3 (needs working submit+poll flow)
- **Edge Cases (Phase 5)**: Depends on Phase 3 (needs endpoint implementation)
- **Polish (Phase 6)**: Depends on all phases complete

### Parallel Opportunities

- T002 and T003 (domain record + task definition) — different files, no dependencies
- T007 and T008 can run in parallel after Phase 3 (different test concerns)

---

## Parallel Example: Foundational Phase

```text
# These two tasks touch different files and can run in parallel:
T002: Create Answer.java (domain record)
T003: Create QuestionTasks.java (task definition)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002, T003)
3. Complete Phase 3: US1 (T004, T005, T006)
4. **STOP and VALIDATE**: `mvn verify` — full submit-and-poll flow works
5. Ready for local testing

### Incremental Delivery

1. Setup + Foundational → compile passes
2. US1 → full flow works → MVP done
3. US2 → typed result consistency verified
4. Edge cases → error handling complete
5. Polish → documentation and manual verification

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 10 |
| US1 tasks | 3 (T004–T006) |
| US2 tasks | 1 (T007) |
| Edge case tasks | 1 (T008) |
| Setup/foundational | 3 (T001–T003) |
| Polish | 2 (T009–T010) |
| Parallel opportunities | 2 groups |
| MVP scope | Phases 1–3 (T001–T006) |
