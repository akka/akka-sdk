# Feature Specification: Research — Multi-Agent Delegation

**Feature Branch**: `004-research`
**Created**: 2026-03-11
**Status**: Draft
**Input**: Coordinator agent delegates research to two specialist agents (Researcher, Analyst), then synthesises their findings into a unified brief. First multi-agent sample demonstrating fan-out/fan-in delegation.

## User Scenarios & Testing

### User Story 1 - Submit a Research Topic and Get a Brief (Priority: P1)

A user submits a research topic via HTTP. The system creates a BRIEF task and assigns it to a ResearchCoordinator agent. The coordinator delegates work by creating a FINDINGS task for a Researcher specialist and an ANALYSIS task for an Analyst specialist. Both specialists work in isolation — each sees only its own task. When both complete, their results flow back to the coordinator, which synthesises the facts and trends into a unified ResearchBrief containing a title, summary, and key findings. The user retrieves the final brief by polling a result endpoint.

**Why this priority**: This is the core flow demonstrating the complete delegation pattern — coordinator creates child tasks, specialists execute in parallel isolation, coordinator synthesises results.

**Independent Test**: Can be fully tested by submitting a topic via HTTP POST, then polling for the BRIEF task result, verifying it contains a title, summary, and key findings that reflect both factual research and trend analysis.

**Acceptance Scenarios**:

1. **Given** the system is running, **When** a user submits a research topic via HTTP POST, **Then** the system accepts the request and returns an identifier for the BRIEF task.
2. **Given** a topic has been submitted, **When** the coordinator processes the BRIEF task, **Then** it delegates by creating a FINDINGS task for the Researcher and an ANALYSIS task for the Analyst.
3. **Given** both specialists have completed their tasks, **When** the coordinator synthesises results, **Then** the final ResearchBrief contains a title, summary, and key findings informed by both the factual research and the trend analysis.
4. **Given** the research is complete, **When** the user retrieves the BRIEF result, **Then** the response contains all three fields (title, summary, keyFindings).

---

### User Story 2 - Context Isolation Between Specialists (Priority: P2)

Each specialist agent operates in isolation — the Researcher sees only the FINDINGS task and the Analyst sees only the ANALYSIS task. Neither has access to the other's context or results. Only the coordinator has full context.

**Why this priority**: Demonstrates the context partitioning property of delegation, which is a key architectural distinction from shared-context patterns.

**Independent Test**: Can be verified by inspecting that each specialist's task contains only its own instructions and that the coordinator is the sole agent that accesses both results.

**Acceptance Scenarios**:

1. **Given** the coordinator has delegated, **When** the Researcher processes the FINDINGS task, **Then** it has access only to its own task instructions, not the Analyst's task or the coordinator's full context.
2. **Given** the coordinator has delegated, **When** the Analyst processes the ANALYSIS task, **Then** it has access only to its own task instructions, not the Researcher's task or the coordinator's full context.
3. **Given** both specialists complete, **When** the coordinator accesses results, **Then** it sees both the FINDINGS and ANALYSIS results to synthesise.

---

### User Story 3 - Specialist Agents Shut Down After Completion (Priority: P3)

Delegated specialist agents are ephemeral — they start to process their assigned task and shut down once the task is complete. They do not persist or accept further work.

**Why this priority**: Demonstrates the lifecycle property of delegated agents — they are one-shot workers, not long-running processes.

**Independent Test**: Can be verified by observing that after the FINDINGS and ANALYSIS tasks complete, the Researcher and Analyst agent instances are no longer active.

**Acceptance Scenarios**:

1. **Given** the Researcher has completed the FINDINGS task, **Then** the Researcher agent instance shuts down and is not available for further tasks.
2. **Given** the Analyst has completed the ANALYSIS task, **Then** the Analyst agent instance shuts down and is not available for further tasks.

---

### Edge Cases

- What happens when the user polls for a result that does not exist (invalid identifier)? The system returns an appropriate error response.
- What happens when the user submits an empty or blank topic? The system rejects the request with a validation error.
- What happens if one specialist fails (e.g., Researcher fails but Analyst succeeds)? The coordinator cannot complete synthesis and the BRIEF task reflects the failure.

## Requirements

### Functional Requirements

- **FR-001**: System MUST accept a research topic via HTTP and create a BRIEF task assigned to a ResearchCoordinator agent.
- **FR-002**: System MUST return an identifier for the BRIEF task upon submission.
- **FR-003**: The ResearchCoordinator MUST delegate work by creating a FINDINGS task for the Researcher and an ANALYSIS task for the Analyst.
- **FR-004**: The Researcher MUST process the FINDINGS task and produce a typed result containing a topic, facts, and sources.
- **FR-005**: The Analyst MUST process the ANALYSIS task and produce a typed result containing a topic, assessment, and trends.
- **FR-006**: Each specialist MUST operate in isolation — seeing only its own task, not the other specialist's context.
- **FR-007**: The ResearchCoordinator MUST synthesise both specialist results into a unified ResearchBrief containing a title, summary, and key findings.
- **FR-008**: System MUST provide a polling endpoint that returns the BRIEF task result by its identifier.
- **FR-009**: Delegated specialist agents MUST shut down after completing their task.

### Key Entities

- **Research Topic**: The user's input text describing the subject to research.
- **ResearchBrief**: The final typed result produced by the coordinator, containing a title (string), summary (string), and key findings (list of strings).
- **ResearchFindings**: The typed result from the Researcher, containing a topic (string), facts (list of strings), and sources (list of strings).
- **AnalysisReport**: The typed result from the Analyst, containing a topic (string), assessment (string), and trends (list of strings).
- **Task (BRIEF)**: The top-level task assigned to the coordinator, producing a ResearchBrief.
- **Task (FINDINGS)**: Delegated to the Researcher, producing ResearchFindings.
- **Task (ANALYSIS)**: Delegated to the Analyst, producing an AnalysisReport.
- **ResearchCoordinator**: The coordinating agent that receives the BRIEF task, delegates to specialists, and synthesises results. Can delegate to Researcher and Analyst.
- **Researcher**: Specialist agent that gathers facts and sources. Accepts FINDINGS tasks only.
- **Analyst**: Specialist agent that identifies trends and insights. Accepts ANALYSIS tasks only.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users can submit a research topic and retrieve a unified brief through a two-step HTTP interaction (submit, then poll).
- **SC-002**: The coordinator successfully delegates to two specialists and synthesises their results into a coherent brief for every valid topic submitted.
- **SC-003**: Each specialist operates in isolation — no cross-contamination of context between Researcher and Analyst.
- **SC-004**: The sample demonstrates the delegation (fan-out/fan-in) pattern — distinct from the single-agent pipeline and handoff patterns.
- **SC-005**: All three result types conform to their expected typed structures.

## Assumptions

- A single ResearchCoordinator instance is created per research request (one-off, not reused).
- The coordinator decides when and how to delegate — delegation is driven by the agent's LLM reasoning, not hardcoded.
- Both specialists can work in parallel since neither depends on the other's output.
- No authentication or authorization is required for this sample.
- No persistence beyond the task lifecycle is needed.
- The polling model is sufficient; no push notifications or streaming is required.
- The keyFindings field in ResearchBrief is a list of strings.
- The facts and sources fields in ResearchFindings are lists of strings.
- The trends field in AnalysisReport is a list of strings.
