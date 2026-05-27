# Feature Specification: Support — Handoff Routing

**Feature Branch**: `006-support`
**Created**: 2026-03-11
**Status**: Draft
**Input**: Triage agent classifies customer support requests and hands off to the appropriate specialist. Pure handoff pattern — no delegation, just routing.

## User Scenarios & Testing

### User Story 1 - Billing Support Request (Priority: P1)

A customer submits a billing-related support request (e.g., payment dispute, invoice query). The TriageAgent receives a RESOLVE task, analyzes the request, classifies it as billing, and hands off the task to the BillingSpecialist. The BillingSpecialist takes ownership of the same RESOLVE task, resolves the issue, and completes it with category="billing" and resolved=true.

**Why this priority**: Demonstrates the complete handoff flow for one of the two specialist paths — the core pattern of the sample.

**Independent Test**: Can be tested by submitting a billing problem via HTTP POST, polling for the RESOLVE result, and verifying category="billing", a non-empty resolution, and resolved=true.

**Acceptance Scenarios**:

1. **Given** the system is running, **When** a customer submits a billing-related request via HTTP POST, **Then** the system accepts the request and returns an identifier for the RESOLVE task.
2. **Given** a billing request has been submitted, **When** the TriageAgent analyzes the request, **Then** it classifies it as billing and hands off the RESOLVE task to the BillingSpecialist.
3. **Given** the BillingSpecialist has received the task, **When** it resolves the issue, **Then** the result contains category="billing", a resolution description, and resolved=true.

---

### User Story 2 - Technical Support Request (Priority: P1)

A customer submits a technical support request (e.g., bug report, outage, configuration issue). The TriageAgent classifies it as technical and hands off to the TechnicalSpecialist, who resolves the issue with category="technical".

**Why this priority**: Equally critical — without both paths, the sample only demonstrates one-way routing and doesn't show the triage decision.

**Independent Test**: Can be tested by submitting a technical problem, polling for the result, and verifying category="technical" and resolved=true.

**Acceptance Scenarios**:

1. **Given** a technical support request has been submitted, **When** the TriageAgent analyzes the request, **Then** it classifies it as technical and hands off the RESOLVE task to the TechnicalSpecialist.
2. **Given** the TechnicalSpecialist has received the task, **When** it resolves the issue, **Then** the result contains category="technical", a resolution description, and resolved=true.

---

### User Story 3 - Task Moves Between Agents (Priority: P2)

The same RESOLVE task moves from the TriageAgent to the specialist — no new tasks are created. The triage agent is lightweight and only classifies; the specialist does the actual resolution work.

**Why this priority**: Demonstrates the key architectural property of handoff — task identity is preserved, ownership transfers. This distinguishes handoff from delegation where child tasks are created.

**Independent Test**: Can be verified by observing that the task identifier returned at submission is the same task that the specialist completes — no additional task IDs are created.

**Acceptance Scenarios**:

1. **Given** a support request has been submitted, **When** the triage agent hands off, **Then** the specialist completes the same task (same identifier) that was originally created.
2. **Given** the triage agent has handed off, **Then** the triage agent is no longer involved — the specialist completes the task independently.

---

### Edge Cases

- What happens when the customer polls for a result that does not exist (invalid identifier)? The system returns an appropriate error response.
- What happens when the customer submits an empty or blank request? The system rejects the request with a validation error.
- What happens if the specialist fails to resolve the issue? The result has resolved=false with an explanation in the resolution field.

## Requirements

### Functional Requirements

- **FR-001**: System MUST accept a customer support request via HTTP and create a RESOLVE task assigned to a TriageAgent.
- **FR-002**: System MUST return an identifier for the RESOLVE task upon submission.
- **FR-003**: The TriageAgent MUST analyze the request and classify it as either billing or technical.
- **FR-004**: The TriageAgent MUST hand off the RESOLVE task to the BillingSpecialist for billing requests.
- **FR-005**: The TriageAgent MUST hand off the RESOLVE task to the TechnicalSpecialist for technical requests.
- **FR-006**: The specialist MUST take full ownership of the handed-off RESOLVE task and complete it.
- **FR-007**: Each specialist MUST produce a typed result containing a category (string), resolution (string), and resolved flag (boolean).
- **FR-008**: All agents MUST share the same task type (RESOLVE) — no new tasks are created during handoff.
- **FR-009**: System MUST provide a polling endpoint that returns the RESOLVE task result by its identifier.
- **FR-010**: The TriageAgent MUST be lightweight (limited iterations) since it only classifies, while specialists have more iterations for resolution work.

### Key Entities

- **Support Request**: The customer's input describing the issue to be resolved.
- **SupportResolution**: The typed result for the RESOLVE task, containing a category (string — "billing" or "technical"), resolution (string description), and resolved flag (boolean).
- **Task (RESOLVE)**: The single task type shared by all agents. Created for the triage agent, handed off to a specialist, completed by the specialist.
- **TriageAgent**: Lightweight classifier that analyzes requests and routes to the appropriate specialist. Can hand off to BillingSpecialist and TechnicalSpecialist.
- **BillingSpecialist**: Resolves billing disputes, payment issues, and invoice queries. Accepts RESOLVE tasks via handoff.
- **TechnicalSpecialist**: Diagnoses and resolves technical problems, bugs, and outages. Accepts RESOLVE tasks via handoff.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Customers can submit a support request and retrieve a resolution through a two-step HTTP interaction (submit, then poll).
- **SC-002**: Billing requests are routed to the BillingSpecialist and resolved with category="billing".
- **SC-003**: Technical requests are routed to the TechnicalSpecialist and resolved with category="technical".
- **SC-004**: The same task moves between agents via handoff — no child tasks are created, distinguishing this from the delegation pattern.
- **SC-005**: The triage agent is lightweight (few iterations) while specialists have more capacity, reflecting the classifier-vs-resolver role separation.

## Assumptions

- A single TriageAgent instance is created per support request (one-off, not reused).
- The triage classification is driven by the LLM, not hardcoded rules.
- Only two specialist categories exist: billing and technical. Requests that don't clearly fit either are classified by the LLM's best judgment.
- The triage agent has limited iterations (3) since it only needs to classify and hand off.
- Specialists have more iterations (5) to do the actual resolution work.
- No authentication or authorization is required for this sample.
- No persistence beyond the task lifecycle is needed.
- The polling model is sufficient; no push notifications or streaming is required.
