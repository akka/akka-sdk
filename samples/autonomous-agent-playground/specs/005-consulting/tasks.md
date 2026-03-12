# Tasks: Consulting — Delegation + Handoff

**Input**: Design documents from `/specs/005-consulting/`
**Prerequisites**: plan.md, spec.md, data-model.md, research.md

**Tests**: Included — integration tests validate both delegation and handoff coordination flows.

**Organization**: Tasks grouped by user story. US1 (standard/delegation) and US2 (complex/handoff) are both P1. US3 (shared tools) is P2 but tools are foundational — built in Phase 2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `src/main/java/demo/consulting/` — source root
- `src/test/java/demo/consulting/` — test root

---

## Phase 1: Setup

**Purpose**: Project directory structure

- [x] T001 Create package directories for `src/main/java/demo/consulting/{domain,application,api}` and `src/test/java/demo/consulting/`

---

## Phase 2: Foundational (Domain + Tasks + Shared Tools)

**Purpose**: Domain records, task definitions, and shared tools that ALL user stories depend on

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T002 [P] Create `ConsultingResult` domain record in `src/main/java/demo/consulting/domain/ConsultingResult.java` — fields: `assessment` (String), `recommendation` (String), `escalated` (boolean)
- [x] T003 [P] Create `ResearchSummary` domain record in `src/main/java/demo/consulting/domain/ResearchSummary.java` — fields: `topic` (String), `findings` (String)
- [x] T004 [P] Create `ConsultingTasks` task definitions in `src/main/java/demo/consulting/application/ConsultingTasks.java` — `ENGAGEMENT` with `resultConformsTo(ConsultingResult.class)`, `RESEARCH` with `resultConformsTo(ResearchSummary.class)`
- [x] T005 [P] Create `ConsultingTools` shared tools in `src/main/java/demo/consulting/application/ConsultingTools.java` — `@FunctionTool assessProblem(String)` returns preliminary assessment, `@FunctionTool checkComplexity(String)` returns "COMPLEX: ..." for regulatory/M&A/compliance problems or "STANDARD: ..." otherwise

**Checkpoint**: `mvn compile` passes. Domain, tasks, and shared tools ready.

---

## Phase 3: User Story 1 — Standard Problem: Delegate Research and Synthesise (Priority: P1) MVP

**Goal**: Client submits a standard consulting problem. Coordinator delegates RESEARCH to ConsultingResearcher, waits for findings, synthesises recommendation with escalated=false.

**Independent Test**: POST a standard problem (e.g., "market analysis for expanding into Southeast Asia"), poll until COMPLETED, verify escalated=false and recommendation references research findings.

### Implementation for User Story 1

- [x] T006 [P] [US1] Create `ConsultingResearcher` agent in `src/main/java/demo/consulting/application/ConsultingResearcher.java` — extends `AutonomousAgent`, `@Component(id = "consulting-researcher", description = "Performs targeted research on specific aspects of consulting problems")`, accepts `RESEARCH`, `maxIterations(3)`
- [x] T007 [US1] Create `ConsultingCoordinator` agent in `src/main/java/demo/consulting/application/ConsultingCoordinator.java` — extends `AutonomousAgent`, `@Component(id = "consulting-coordinator")`, accepts `ENGAGEMENT`, `tools(new ConsultingTools())`, `canDelegateTo(ConsultingResearcher.class)`, `canHandoffTo(SeniorConsultant.class)`, `maxIterations(10)`
- [x] T008 [US1] Create `ConsultingEndpoint` in `src/main/java/demo/consulting/api/ConsultingEndpoint.java` — inner records `EngagementRequest`, `EngagementResponse`, `ConsultingResultResponse`; `POST /engagements` creates coordinator via `runSingleTask(ENGAGEMENT.instructions(problem))`, `GET /engagements/{taskId}` queries task snapshot; validate empty problem (400)

### Test for User Story 1

- [x] T009 [US1] Create `ConsultingIntegrationTest` in `src/test/java/demo/consulting/ConsultingIntegrationTest.java` — register `TestModelProvider` for `ConsultingCoordinator` and `ConsultingResearcher`; test `shouldDelegateStandardProblem`: mock coordinator to call `assessProblem` then `checkComplexity` (returns STANDARD) then `delegateToConsultingResearcher`, mock researcher to call `complete_task` with ResearchSummary, mock coordinator second turn to call `complete_task` with ConsultingResult(escalated=false); submit via HTTP POST, poll via GET, verify escalated=false

**Checkpoint**: `mvn verify` passes. Delegation flow works end-to-end.

---

## Phase 4: User Story 2 — Complex Problem: Hand Off to Senior Consultant (Priority: P1)

**Goal**: Client submits a complex problem (regulatory/M&A). Coordinator hands off ENGAGEMENT to SeniorConsultant who completes it with escalated=true.

**Independent Test**: POST a complex problem (e.g., "regulatory compliance for GDPR"), poll until COMPLETED, verify escalated=true.

### Implementation for User Story 2

- [x] T010 [US2] Create `SeniorConsultant` agent in `src/main/java/demo/consulting/application/SeniorConsultant.java` — extends `AutonomousAgent`, `@Component(id = "senior-consultant", description = "Handles complex, high-stakes consulting issues requiring senior expertise")`, accepts `ENGAGEMENT`, `tools(new ConsultingTools())`, `maxIterations(5)`

### Test for User Story 2

- [x] T011 [US2] Add handoff test in `src/test/java/demo/consulting/ConsultingIntegrationTest.java` — register `TestModelProvider` for `SeniorConsultant`; test `shouldHandoffComplexProblem`: mock coordinator to call `assessProblem` then `checkComplexity` (returns COMPLEX) then `handoffToSeniorConsultant`, mock senior to call `complete_task` with ConsultingResult(escalated=true); submit via HTTP POST, poll via GET, verify escalated=true

**Checkpoint**: `mvn verify` passes. Both delegation and handoff flows work.

---

## Phase 5: Edge Cases

**Purpose**: Handle edge cases from the spec

- [x] T012 Add edge case tests in `src/test/java/demo/consulting/ConsultingIntegrationTest.java` — test: POST with empty/blank problem returns 400; GET with unknown task ID returns error

**Checkpoint**: `mvn verify` passes. All edge cases handled.

---

## Phase 6: Polish

**Purpose**: Documentation and final validation

- [x] T013 Update `README.md` with curl examples for `POST /engagements` and `GET /engagements/{taskId}` under the consulting section
- [x] T014 Run local service with `akka local` and manually verify both flows

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Phase 1; T002–T005 can all run in parallel; BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2; T006 and T007 need SeniorConsultant.class to exist for coordinator's `canHandoffTo` — but T007 only needs the class compiled, so T010 must be created first OR T007 uses a forward reference. **Resolution**: Create T010 (SeniorConsultant) before T007 (Coordinator) since coordinator references it.
- **US2 (Phase 4)**: T010 can be done in parallel with T006; T011 depends on T008 (endpoint) and T010
- **Edge Cases (Phase 5)**: Depends on Phase 3 + Phase 4
- **Polish (Phase 6)**: Depends on all phases

### Adjusted Execution Order

Because `ConsultingCoordinator` references both `ConsultingResearcher.class` and `SeniorConsultant.class`, all three agent classes should be created before the endpoint and tests:

1. T001 (setup)
2. T002, T003, T004, T005 (foundational — parallel)
3. T006, T010 (researcher + senior — parallel, both independent)
4. T007 (coordinator — needs T006 + T010 compiled)
5. T008 (endpoint — needs T007)
6. T009, T011 (tests — can be written together after T008)
7. T012, T013, T014

### Parallel Opportunities

- T002, T003, T004, T005 — all different files, no dependencies
- T006, T010 — different agent files, no dependencies between them
- T009, T011 — different test methods but same file; write sequentially

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

Both US1 and US2 are P1 — the sample requires both flows to demonstrate the delegation+handoff pattern.

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002–T005)
3. Create all 3 agents: T006, T010, T007 (researcher, senior, then coordinator)
4. Create endpoint: T008
5. Add tests: T009, T011
6. **STOP and VALIDATE**: `mvn verify`

### Incremental Delivery

1. Setup + Foundational → compile passes
2. All agents + endpoint → compile passes
3. US1 test → delegation flow verified
4. US2 test → handoff flow verified
5. Edge cases → error handling complete
6. Polish → documentation

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 14 |
| US1 tasks | 4 (T006–T009) |
| US2 tasks | 2 (T010–T011) |
| Edge case tasks | 1 (T012) |
| Setup/foundational | 5 (T001–T005) |
| Polish | 2 (T013–T014) |
| Parallel opportunities | 3 groups |
| MVP scope | Phases 1–4 (T001–T011) |
