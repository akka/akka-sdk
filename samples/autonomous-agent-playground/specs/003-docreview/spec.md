# Feature Specification: Document Review with Attachments

**Feature Branch**: `003-docreview`
**Created**: 2026-03-11
**Status**: Draft
**Input**: Single agent reviews a document for compliance, receiving document content as a task attachment rather than inline in instructions.

## User Scenarios & Testing

### User Story 1 - Submit a Document for Compliance Review (Priority: P1)

A user submits a document along with review instructions via HTTP. The system creates a DocumentReviewer autonomous agent instance with a REVIEW task. The review instructions are passed as the task instructions and the document text is attached separately as content. The agent reviews the document against the instructions and produces a structured compliance assessment including an overall assessment, specific findings, and a compliance verdict (true/false).

**Why this priority**: This is the core and only flow — demonstrating task attachments for passing large content to agents separately from instruction text, and producing a multi-field structured result.

**Independent Test**: Can be fully tested by submitting a document and review instructions via HTTP POST, then polling the result endpoint to verify the response contains an assessment, findings list, and compliance boolean.

**Acceptance Scenarios**:

1. **Given** the system is running, **When** a user submits a document and review instructions via HTTP POST, **Then** the system accepts the request and returns an identifier for retrieving the result.
2. **Given** a review has been submitted, **When** the agent completes processing, **Then** the result contains an assessment (string), findings (list of specific observations), and a compliant flag (boolean).
3. **Given** a compliant document is submitted, **When** the agent reviews it, **Then** the compliant flag is true and findings reflect no critical issues.
4. **Given** a non-compliant document is submitted, **When** the agent reviews it, **Then** the compliant flag is false and findings identify the specific compliance issues.

---

### User Story 2 - Retrieve Review Result (Priority: P2)

A user retrieves the completed review result of a previously submitted document. The result is a structured object with multiple fields, not a raw string.

**Why this priority**: Demonstrates structured result retrieval with a richer type than the helloworld sample (three fields instead of two).

**Independent Test**: Can be tested by submitting a document, waiting for completion, and verifying the response contains all three fields with appropriate types.

**Acceptance Scenarios**:

1. **Given** a review has completed, **When** the user retrieves the result, **Then** the response contains a non-empty assessment string, a findings list, and a boolean compliant flag.
2. **Given** a review has not yet completed, **When** the user polls for the result, **Then** the system indicates the result is not yet available.

---

### Edge Cases

- What happens when the user polls for a result that does not exist (invalid identifier)? The system returns an appropriate error response.
- What happens when the user submits an empty document? The system accepts the request but the agent may flag it in findings.
- What happens when the user submits empty review instructions? The system rejects the request with a validation error since the agent needs criteria to review against.

## Requirements

### Functional Requirements

- **FR-001**: System MUST accept a document (text content) and review instructions via HTTP.
- **FR-002**: System MUST pass the review instructions as the task instructions and the document text as a separate attachment on the task.
- **FR-003**: System MUST return an identifier upon submission that can be used to retrieve the result later.
- **FR-004**: The DocumentReviewer agent MUST review the attached document against the provided instructions.
- **FR-005**: The agent MUST produce a typed result containing: an assessment (string summary), findings (list of specific observations), and a compliant flag (boolean).
- **FR-006**: System MUST provide a polling endpoint that returns the review result by its identifier.
- **FR-007**: System MUST indicate when a result is not yet available (task still in progress).

### Key Entities

- **Document**: The text content submitted for review, passed as a task attachment.
- **Review Instructions**: The criteria or guidelines the agent uses to evaluate the document, passed as task instructions.
- **ReviewResult**: The structured result containing an assessment (string), findings (list of strings), and compliant (boolean).
- **Task (REVIEW)**: The single task type that carries review instructions and a document attachment, producing a ReviewResult.
- **DocumentReviewer**: The autonomous agent that accepts REVIEW tasks and evaluates documents.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users can submit a document with review instructions and retrieve a structured compliance assessment through a two-step HTTP interaction (submit, then poll).
- **SC-002**: The agent produces a result conforming to the expected typed structure (assessment + findings + compliant) for every valid submission.
- **SC-003**: The document content is passed as a task attachment, separate from the instruction text — demonstrating the attachment capability.
- **SC-004**: The sample builds on helloworld by showing richer input (attachments) and richer output (multi-field result with a list).

## Assumptions

- A single DocumentReviewer instance is created per review (one-off, not reused).
- The document is submitted as plain text, not as a file upload or binary format.
- The findings field is a list of strings, each describing a specific observation.
- No authentication or authorization is required for this sample.
- No persistence beyond the task lifecycle is needed.
- The polling model is sufficient; no push notifications or streaming is required.
