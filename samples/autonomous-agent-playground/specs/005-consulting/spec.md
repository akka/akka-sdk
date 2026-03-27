# Feature Specification: Consulting — Delegation + Handoff

**Feature Branch**: `005-consulting`
**Created**: 2026-03-11
**Status**: Draft
**Input**: Coordinator that can both delegate routine research to a subordinate and hand off complex problems to a senior specialist. Demonstrates combining delegation and handoff in a single agent.

## User Scenarios & Testing

### User Story 1 - Standard Problem: Delegate Research and Synthesise (Priority: P1)

A client submits a consulting problem via HTTP. The ConsultingCoordinator assesses the problem's complexity using domain tools. For standard (non-complex) problems, the coordinator delegates a RESEARCH task to the ConsultingResearcher, waits for findings, and synthesises a recommendation. The final result has escalated=false, indicating the coordinator handled it directly.

**Why this priority**: This is the primary flow demonstrating the delegation half of the pattern — the coordinator retains ownership of the ENGAGEMENT task, creates a child RESEARCH task, and synthesises the result.

**Independent Test**: Can be tested by submitting a standard problem (e.g., market analysis), polling for the ENGAGEMENT result, and verifying escalated=false with a recommendation informed by the research findings.

**Acceptance Scenarios**:

1. **Given** the system is running, **When** a client submits a standard consulting problem via HTTP POST, **Then** the system accepts the request and returns an identifier for the ENGAGEMENT task.
2. **Given** a standard problem has been submitted, **When** the coordinator assesses the problem, **Then** it determines the problem is not complex and delegates a RESEARCH task to the ConsultingResearcher.
3. **Given** the ConsultingResearcher has completed the RESEARCH task, **When** the coordinator receives the findings, **Then** it synthesises a recommendation and completes the ENGAGEMENT task with escalated=false.
4. **Given** the engagement is complete, **When** the client retrieves the result, **Then** it contains an assessment, a recommendation, and escalated=false.

---

### User Story 2 - Complex Problem: Hand Off to Senior Consultant (Priority: P1)

A client submits a complex consulting problem (e.g., regulatory compliance, M&A). The ConsultingCoordinator assesses the problem and determines it requires senior expertise. The coordinator hands off the entire ENGAGEMENT task to the SeniorConsultant, who takes full ownership and completes it. The coordinator steps back entirely. The final result has escalated=true.

**Why this priority**: This is equally critical — it demonstrates the handoff half of the pattern. Without this flow, the sample only shows delegation, which is already covered by the research sample.

**Independent Test**: Can be tested by submitting a complex problem (e.g., regulatory compliance issue), polling for the ENGAGEMENT result, and verifying escalated=true with a recommendation from the senior consultant.

**Acceptance Scenarios**:

1. **Given** a complex consulting problem has been submitted, **When** the coordinator assesses the problem, **Then** it determines the problem is complex and hands off the ENGAGEMENT task to the SeniorConsultant.
2. **Given** the coordinator has handed off, **When** the SeniorConsultant processes the ENGAGEMENT task, **Then** it takes full ownership and produces the final result directly.
3. **Given** the SeniorConsultant has completed the engagement, **When** the client retrieves the result, **Then** it contains an assessment, a recommendation, and escalated=true.
4. **Given** the coordinator has handed off, **Then** the coordinator is no longer involved in producing the result — the SeniorConsultant completes the task independently.

---

### User Story 3 - Shared Assessment Tools (Priority: P2)

Both the coordinator and the senior consultant use the same domain tools (assessProblem, checkComplexity) for consistent problem evaluation. This ensures that assessment logic is shared, not duplicated with different behavior.

**Why this priority**: Demonstrates shared tools across agents, which is an important pattern for consistent behavior in multi-agent systems.

**Independent Test**: Can be verified by checking that both agent types produce consistent assessments when given the same problem input.

**Acceptance Scenarios**:

1. **Given** any problem is submitted, **When** the coordinator uses the assessment tools, **Then** the tools provide consistent complexity classification.
2. **Given** a handoff has occurred, **When** the SeniorConsultant uses the same assessment tools, **Then** the tools produce consistent results with what the coordinator would have seen.

---

### Edge Cases

- What happens when the client polls for a result that does not exist (invalid identifier)? The system returns an appropriate error response.
- What happens when the client submits an empty or blank problem? The system rejects the request with a validation error.
- What happens if the ConsultingResearcher fails during a delegated RESEARCH task? The coordinator cannot complete synthesis and the ENGAGEMENT task reflects the failure.
- What happens if the SeniorConsultant fails after handoff? The ENGAGEMENT task reflects the failure since the coordinator is no longer involved.

## Requirements

### Functional Requirements

- **FR-001**: System MUST accept a consulting problem via HTTP and create an ENGAGEMENT task assigned to a ConsultingCoordinator agent.
- **FR-002**: System MUST return an identifier for the ENGAGEMENT task upon submission.
- **FR-003**: The ConsultingCoordinator MUST assess problem complexity using domain tools (assessProblem, checkComplexity).
- **FR-004**: For standard problems, the coordinator MUST delegate a RESEARCH task to the ConsultingResearcher, wait for findings, and synthesise a recommendation with escalated=false.
- **FR-005**: For complex problems, the coordinator MUST hand off the entire ENGAGEMENT task to the SeniorConsultant, who takes full ownership and completes it with escalated=true.
- **FR-006**: The ConsultingResearcher MUST process RESEARCH tasks and produce a typed result containing a topic and findings.
- **FR-007**: The SeniorConsultant MUST process handed-off ENGAGEMENT tasks and produce a ConsultingResult with escalated=true.
- **FR-008**: Domain assessment tools MUST be shared across agents that need them (coordinator and senior consultant) for consistent evaluation.
- **FR-009**: System MUST provide a polling endpoint that returns the ENGAGEMENT task result by its identifier.
- **FR-010**: After handoff, the coordinator MUST NOT be involved in producing the final result — the SeniorConsultant completes the task independently.

### Key Entities

- **Consulting Problem**: The client's input describing the issue to be addressed.
- **ConsultingResult**: The typed result for the ENGAGEMENT task, containing an assessment (string), recommendation (string), and escalated flag (boolean).
- **ResearchSummary**: The typed result for the RESEARCH task, containing a topic (string) and findings (string).
- **Task (ENGAGEMENT)**: The top-level task representing the client's consulting engagement. Owned initially by the coordinator; may be handed off to the senior consultant.
- **Task (RESEARCH)**: A child task delegated by the coordinator to the ConsultingResearcher for routine investigation.
- **ConsultingCoordinator**: The coordinating agent that assesses problems, delegates research for standard problems, and hands off complex problems. Can delegate to ConsultingResearcher and hand off to SeniorConsultant.
- **ConsultingResearcher**: Specialist agent that performs targeted research. Accepts RESEARCH tasks only. Delegation target.
- **SeniorConsultant**: Senior specialist that handles complex, high-stakes issues. Accepts ENGAGEMENT tasks via handoff. Handoff target.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Clients can submit a consulting problem and retrieve a structured result through a two-step HTTP interaction (submit, then poll).
- **SC-002**: Standard problems are resolved via delegation (escalated=false) — the coordinator delegates research and synthesises the recommendation.
- **SC-003**: Complex problems are resolved via handoff (escalated=true) — the coordinator transfers ownership to the senior consultant who completes the task.
- **SC-004**: The sample demonstrates both delegation and handoff in a single agent, clearly showing the distinction: delegation retains ownership, handoff transfers it.
- **SC-005**: Domain assessment tools produce consistent results regardless of which agent invokes them.

## Assumptions

- A single ConsultingCoordinator instance is created per engagement (one-off, not reused).
- The routing decision (delegate vs. handoff) is driven by the LLM using domain tools, not hardcoded.
- The assessProblem and checkComplexity tools are simple domain tools returning string assessments — not external services.
- The SeniorConsultant also has access to the shared assessment tools for consistency.
- No authentication or authorization is required for this sample.
- No persistence beyond the task lifecycle is needed.
- The polling model is sufficient; no push notifications or streaming is required.
- The findings field in ResearchSummary is a string (not a list), keeping it simpler than the research sample.
