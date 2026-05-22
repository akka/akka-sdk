# Feature Specification: Pipeline — Task Dependency Chain

**Feature Branch**: `002-pipeline`
**Created**: 2026-03-11
**Status**: Draft
**Input**: Single agent processes three tasks in a dependency chain (collect, analyze, report) with task dependencies enforcing execution order.

## User Scenarios & Testing

### User Story 1 - Submit a Topic and Get a Report (Priority: P1)

A user submits a research topic via HTTP. The system creates three tasks — collect data, analyze it, and write a report — with explicit dependency relationships so they execute in order. A single ReportAgent instance processes all three tasks sequentially. The user retrieves the final report by polling a result endpoint.

**Why this priority**: This is the core flow demonstrating the complete pipeline pattern — task creation with dependencies, sequential execution by a single agent, and multi-task result retrieval.

**Independent Test**: Can be fully tested by submitting a topic via HTTP POST, then polling for the final REPORT task result, verifying the pipeline completed all three phases in order.

**Acceptance Scenarios**:

1. **Given** the system is running, **When** a user submits a topic via HTTP POST, **Then** the system creates three tasks with dependency relationships and returns identifiers for all three tasks.
2. **Given** a topic has been submitted, **When** the agent processes the pipeline, **Then** the COLLECT task completes before ANALYZE begins, and ANALYZE completes before REPORT begins.
3. **Given** the pipeline has completed, **When** the user retrieves the REPORT task result, **Then** the result contains the phase name ("report") and the synthesized report content.

---

### User Story 2 - Monitor Pipeline Progress (Priority: P2)

A user checks the status of individual pipeline phases to understand how far the pipeline has progressed. Each task can be queried independently to see if it is pending, in progress, or completed.

**Why this priority**: Demonstrates observability of individual tasks within a multi-task pipeline, which is important for understanding the dependency mechanism.

**Independent Test**: Can be tested by submitting a topic, then polling each of the three task identifiers in sequence, observing the status transitions as the pipeline progresses.

**Acceptance Scenarios**:

1. **Given** a pipeline has been started, **When** the user queries the COLLECT task while it is being processed, **Then** the system indicates the task is in progress.
2. **Given** COLLECT has completed but ANALYZE has not started, **When** the user queries the ANALYZE task, **Then** the system indicates it is pending (blocked by dependency).
3. **Given** the pipeline has fully completed, **When** the user queries any of the three tasks, **Then** each returns a completed result with its phase name and content.

---

### Edge Cases

- What happens when the user polls for a task that does not exist (invalid identifier)? The system returns an appropriate error response.
- What happens when the user submits an empty or blank topic? The system rejects the request with a validation error.
- What happens if the agent fails during one phase (e.g., ANALYZE)? Dependent tasks (REPORT) remain pending and do not execute.

## Requirements

### Functional Requirements

- **FR-001**: System MUST accept a research topic via HTTP and create three tasks (COLLECT, ANALYZE, REPORT) with explicit dependency relationships.
- **FR-002**: System MUST return identifiers for all three tasks and the agent instance identifier upon topic submission.
- **FR-003**: The ANALYZE task MUST depend on COLLECT — it cannot begin until COLLECT completes.
- **FR-004**: The REPORT task MUST depend on ANALYZE — it cannot begin until ANALYZE completes.
- **FR-005**: All three tasks MUST be assigned to a single ReportAgent instance.
- **FR-006**: The ReportAgent MUST have domain tools for collecting data and analyzing data, used in the COLLECT and ANALYZE phases respectively.
- **FR-007**: Each task MUST produce a typed result containing a phase name (string) and content (string).
- **FR-008**: System MUST provide a polling endpoint that returns the result of any individual task by its identifier.
- **FR-009**: System MUST indicate when a task result is not yet available (task still pending or in progress).

### Key Entities

- **Topic**: The user's input text describing the subject to research.
- **ReportResult**: The typed result produced by each task, containing a phase name and content string.
- **Task (COLLECT)**: Gathers data on the topic. No dependencies. Uses the collectData tool.
- **Task (ANALYZE)**: Analyzes the collected data. Depends on COLLECT. Uses the analyzeData tool.
- **Task (REPORT)**: Writes a final report synthesizing the analysis. Depends on ANALYZE. No special tools.
- **ReportAgent**: The single autonomous agent that accepts and processes all three task types in dependency order.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users can submit a topic and retrieve a completed report through a multi-step HTTP interaction (submit, then poll individual task results).
- **SC-002**: The three pipeline phases always execute in the correct dependency order (COLLECT → ANALYZE → REPORT) with no out-of-order execution.
- **SC-003**: Each task produces a typed result conforming to the expected structure (phase + content).
- **SC-004**: The sample demonstrates pre-created tasks with dependencies assigned to a single agent — distinct from the single-task `runSingleTask` pattern.

## Assumptions

- A single ReportAgent instance is created per pipeline run (one-off, not reused).
- All three tasks share the same result type (ReportResult) differentiated by phase name.
- The collectData and analyzeData tools are simple domain tools on the agent (not external services).
- No authentication or authorization is required for this sample.
- No persistence beyond the task lifecycle is needed.
- The polling model is sufficient; no push notifications or streaming is required.
