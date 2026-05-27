# Feature Specification: Hello World Autonomous Agent

**Feature Branch**: `001-helloworld`
**Created**: 2026-03-11
**Status**: Draft
**Input**: Simplest autonomous agent sample — single agent answers a question and returns a typed result with confidence score.

## User Scenarios & Testing

### User Story 1 - Submit a Question and Get an Answer (Priority: P1)

A user submits a question to the system via an HTTP request. The system creates a QuestionAnswerer autonomous agent instance, assigns it an ANSWER task with the question as instructions, and the agent processes the question to produce a structured answer with a confidence score. The user retrieves the result by polling a separate endpoint.

**Why this priority**: This is the core and only flow of the helloworld sample. It demonstrates the complete autonomous agent lifecycle from task creation through execution to typed result retrieval.

**Independent Test**: Can be fully tested by submitting a question via HTTP POST, then polling the result endpoint until the answer is available, verifying the response contains both an answer string and a confidence score.

**Acceptance Scenarios**:

1. **Given** the system is running, **When** a user submits a question via HTTP POST, **Then** the system accepts the request and returns an identifier that can be used to retrieve the result.
2. **Given** a question has been submitted, **When** the user polls the result endpoint with the identifier, **Then** the system returns the agent's answer containing a text answer and a numeric confidence score.
3. **Given** a question has been submitted, **When** the agent has not yet completed processing, **Then** the polling endpoint indicates the result is not yet available.

---

### User Story 2 - Retrieve a Typed Result (Priority: P2)

A user retrieves the completed result of a previously submitted question. The result is a structured object containing the answer text and a confidence score, not a raw string.

**Why this priority**: Demonstrates the typed result capability of autonomous agents, which is foundational for all other samples that build on structured outputs.

**Independent Test**: Can be tested by submitting a known question, waiting for completion, and verifying the response deserializes into the expected structure with both fields populated.

**Acceptance Scenarios**:

1. **Given** a question has been answered by the agent, **When** the user retrieves the result, **Then** the response contains an `answer` field (non-empty string) and a `confidence` field (integer, e.g. 0–100).
2. **Given** a question has been answered, **When** the user retrieves the result multiple times, **Then** the same result is returned consistently.

---

### Edge Cases

- What happens when the user polls for a result that does not exist (invalid identifier)? The system returns an appropriate error response.
- What happens when the user submits an empty or blank question? The system rejects the request with a validation error.

## Requirements

### Functional Requirements

- **FR-001**: System MUST accept a question submitted via HTTP and create an autonomous agent instance to process it.
- **FR-002**: System MUST return an identifier upon question submission that can be used to retrieve the result later.
- **FR-003**: The QuestionAnswerer agent MUST process the question and produce a typed result containing an answer (string) and a confidence score (integer, e.g. 0–100).
- **FR-004**: System MUST provide a polling endpoint that returns the task result when the agent has completed processing.
- **FR-005**: System MUST indicate when a result is not yet available (task still in progress).
- **FR-006**: System MUST return an appropriate error when a result is requested for an unknown identifier.

### Key Entities

- **Question**: The user's input text submitted for the agent to answer.
- **Answer**: The structured result produced by the agent, containing an answer string and a confidence score.
- **Task (ANSWER)**: The single task type that carries the question as instructions and produces an Answer as its typed result.
- **QuestionAnswerer**: The autonomous agent that accepts ANSWER tasks and processes them.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users can submit a question and retrieve a structured answer through a two-step HTTP interaction (submit, then poll).
- **SC-002**: The agent produces a result that conforms to the expected typed structure (answer + confidence) for every valid question submitted.
- **SC-003**: The system correctly reports task status (in-progress vs. completed) when polled.
- **SC-004**: The sample serves as a minimal, understandable starting point — no coordination, no tools, no multi-agent interaction.

## Assumptions

- A single autonomous agent instance is created per question (one-off, not reused).
- The confidence score is determined by the LLM and expressed as an integer (e.g. 0–100).
- No authentication or authorization is required for this sample.
- No persistence of questions or answers beyond the task lifecycle is needed.
- The polling model is sufficient; no push notifications or streaming of results is required.
