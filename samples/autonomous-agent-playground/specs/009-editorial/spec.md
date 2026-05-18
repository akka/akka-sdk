# Feature Specification: Editorial — Delegation Across Coordinator Stages

**Feature Branch**: `009-editorial`
**Created**: 2026-05-22
**Status**: Draft
**Input**: An editor-in-chief produces a deep-dive technology article by delegating a stage task to each of three section leads — a research editor, a writing lead, and a review editor. Each section lead is itself a coordinator using a different capability internally: delegation, team leadership, and moderation respectively.

## User Scenarios & Testing

### User Story 1 — Submit a Topic and Receive an Article (Priority: P1)

A user submits a technology topic via HTTP. The system creates an ARTICLE task assigned to an EditorInChief. The EditorInChief delegates a stage task to each section lead — research, writing, and review — feeding each result into the next stage. Each lead performs its own inner coordination to fulfil its stage task. The EditorInChief synthesises a final Article (title, body, key points) from the returned results.

**Why this priority**: This is the core flow. Without it the sample produces no output.

**Independent Test**: Submit a topic via HTTP POST, poll for the ARTICLE result, and verify it has a title, a body, and a non-empty key points list.

**Acceptance Scenarios**:

1. **Given** the system is running, **When** a user submits a topic via HTTP POST, **Then** the system accepts the request and returns an identifier for the ARTICLE task.
2. **Given** an editorial task is in progress, **When** the user polls for the result, **Then** the system indicates the result is not yet available.
3. **Given** the editorial task completes, **When** the user retrieves the result, **Then** the response contains a structured Article with a title, body, and key points.

---

### User Story 2 — Research Stage Delegates to Reporters (Priority: P2)

The EditorInChief delegates a RESEARCH task to the ResearchEditor. The ResearchEditor delegates FINDINGS tasks to one or more Reporters on different angles. Reporters save their findings to the shared workspace and return ResearchFindings results. The ResearchEditor consolidates the findings into a ResearchDigest — a summary plus document references — and returns it to the EditorInChief.

**Why this priority**: Demonstrates a delegation stage that is itself a delegation coordinator — the first level of nesting.

**Independent Test**: Submit a topic and verify at least one FINDINGS task is created under a Reporter, and that the ResearchEditor's RESEARCH task completes with a ResearchDigest.

**Acceptance Scenarios**:

1. **Given** the EditorInChief processes the article, **When** it delegates the research stage, **Then** a RESEARCH task is created and assigned to a ResearchEditor.
2. **Given** the ResearchEditor processes its task, **When** it delegates research, **Then** it creates at least one FINDINGS sub-task assigned to a Reporter.
3. **Given** a Reporter receives a FINDINGS task, **When** it works on the task, **Then** it saves findings to the shared workspace and returns a ResearchFindings result with a document id.

---

### User Story 3 — Writing Stage Leads a Team (Priority: P2)

The EditorInChief delegates a DRAFT task to the WritingLead, passing the research digest. The WritingLead leads a team of a SectionWriter and a CopyEditor, who claim and complete SECTION tasks from a shared backlog. The WritingLead assembles the section drafts into an ArticleDraft and returns it.

**Why this priority**: Demonstrates a delegation stage that runs a team internally. Because the DRAFT task is held while the team works, the team's work does not exhaust the WritingLead's or the EditorInChief's processing limits.

**Independent Test**: Submit a topic and verify a writing team is created with at least one SECTION task completed by a team member, and that the DRAFT task completes with an ArticleDraft.

**Acceptance Scenarios**:

1. **Given** the EditorInChief delegates the writing stage, **When** the WritingLead processes its task, **Then** it forms a writing team with at least one member.
2. **Given** the writing team is active, **When** members process work, **Then** at least one SECTION task is claimed and completed by a team member, producing a SectionDraft result.

---

### User Story 4 — Review Stage Runs a Moderated Review (Priority: P2)

The EditorInChief delegates a REVIEW task to the ReviewEditor, passing the draft. The ReviewEditor moderates a review between an AccuracyReviewer and a ReadabilityReviewer, collects their input, and returns a ReviewReport.

**Why this priority**: Demonstrates a delegation stage that runs a moderated conversation internally — the deepest nesting in the sample.

**Independent Test**: Submit a topic and verify a review conversation involves both reviewers, and that the REVIEW task completes with a ReviewReport.

**Acceptance Scenarios**:

1. **Given** the EditorInChief delegates the review stage, **When** the ReviewEditor processes its task, **Then** it runs a review conversation involving both the AccuracyReviewer and the ReadabilityReviewer.
2. **Given** the review conversation completes, **When** the ReviewEditor returns its result, **Then** the ReviewReport includes notes informed by both reviewers.

---

### User Story 5 — Shared Workspace for Bulky Artifacts (Priority: P3)

Bulky artifacts (research findings, section drafts) are saved to a shared workspace and referenced by document id across agents. Agents that need upstream work look up documents by id; agents that produce bulky content save it and pass the id forward. Typed task results carry structure and pointers; the shared workspace carries the content.

**Why this priority**: Demonstrates how tools and tasks complement each other — document references for bulky content, task results for structure.

**Independent Test**: After a run, the shared workspace contains at least one document created by a Reporter and at least one created by a writing-team member.

**Acceptance Scenarios**:

1. **Given** research is performed, **When** a Reporter completes a FINDINGS task, **Then** it has saved a document and the returned id appears in its ResearchFindings result.
2. **Given** writing is performed, **When** a SectionWriter or CopyEditor completes a SECTION task, **Then** it has saved a document and included the id in its SectionDraft result.
3. **Given** a reviewer needs to assess a draft, **When** it looks up a document id from the draft, **Then** it retrieves the saved content from the workspace.

---

### Edge Cases

- What happens when the user polls for a result that does not exist (invalid identifier)? The system returns an appropriate error response.
- What happens when a delegated stage fails (for example, a Reporter fails during research)? The stage task reflects the failure, and the EditorInChief decides whether to proceed with what it has or fail the article.
- What happens when the EditorInChief reaches a later stage before an earlier one has produced its input? The EditorInChief is expected to sequence the stages so each has its inputs, carrying prior results forward in the stage instructions.
- What happens when a stage produces a weak or empty result? The EditorInChief synthesises from what it has; the resulting article may be less complete.

## Requirements

### Functional Requirements

**Endpoint**

- **FR-001**: System MUST accept a topic via HTTP and create an ARTICLE task assigned to an EditorInChief, returning an identifier for the task.
- **FR-002**: System MUST provide a polling endpoint that returns the ARTICLE task result by its identifier, and MUST indicate when the result is not yet available.

**Orchestration**

- **FR-003**: The EditorInChief MUST delegate work to three section leads — a ResearchEditor (research stage), a WritingLead (writing stage), and a ReviewEditor (review stage) — and synthesise their results into the final article.
- **FR-004**: The EditorInChief MUST produce a typed Article result containing a title, a body, and a list of key points.
- **FR-005**: The EditorInChief MUST carry each stage's result forward into the next stage's instructions. Stage ordering and any revisiting of a stage MUST be the LLM's decision, not hardcoded control flow.

**Section leads**

- **FR-006**: The ResearchEditor MUST process a RESEARCH task by delegating FINDINGS tasks to one or more Reporters and consolidating their findings into a ResearchDigest.
- **FR-007**: The WritingLead MUST process a DRAFT task by leading a team of a SectionWriter and a CopyEditor and assembling their sections into an ArticleDraft.
- **FR-008**: The ReviewEditor MUST process a REVIEW task by moderating a review between an AccuracyReviewer and a ReadabilityReviewer and consolidating their input into a ReviewReport.

**Leaf workers**

- **FR-009**: The Reporter MUST process FINDINGS tasks, save findings to the shared workspace, and produce a ResearchFindings result containing an angle, a summary, and a document id.
- **FR-010**: The SectionWriter and CopyEditor MUST process SECTION tasks, reading source material from and saving sections to the shared workspace, and produce a SectionDraft result containing a section title, a summary, and a document id.
- **FR-011**: The AccuracyReviewer and ReadabilityReviewer MUST participate in the review conversation, reading the draft from the shared workspace.

**Shared workspace**

- **FR-012**: The system MUST provide a shared document workspace with a tool to save a document (returning a document id) and a tool to look up a document by id, available to the agents that read and write workspace content.

### Key Entities

- **Topic**: User-supplied text describing the article subject.
- **Article**: The typed result for the ARTICLE task. Contains a title, a body, and a list of key points.
- **ResearchDigest**: The typed result for the RESEARCH task. Contains a summary and document references to the full findings.
- **ResearchFindings**: The typed result for the FINDINGS task. Contains an angle, a one-sentence summary, and a document id.
- **ArticleDraft**: The typed result for the DRAFT task. Contains a title, a body, and document references to the source sections.
- **SectionDraft**: The typed result for the SECTION task. Contains a section title, a summary, and a document id.
- **ReviewReport**: The typed result for the REVIEW task. Contains an overall assessment and a list of review notes.
- **Document**: A stored artifact in the shared workspace. Contains a title, a summary, and the full content.
- **Tasks (ARTICLE / RESEARCH / FINDINGS / DRAFT / SECTION / REVIEW)**: ARTICLE is the top-level task. RESEARCH, DRAFT, and REVIEW are the stage tasks the EditorInChief delegates. FINDINGS and SECTION are the worker tasks created within stages.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A user can submit a topic and retrieve a structured Article through a two-step interaction (submit, then poll).
- **SC-002**: A run exercises delegation, team leadership, and moderation across the hierarchy — delegation at the top and in the research stage, team leadership in the writing stage, and moderation in the review stage.
- **SC-003**: At least one document is saved to the shared workspace by a Reporter and at least one by a writing-team member, demonstrating cross-agent content sharing.
- **SC-004**: Each stage runs as an independent sub-task, so a lead's internal team or review completes without exhausting the parent's processing limits.

## Assumptions

- The EditorInChief sequences the stages so each has the inputs it needs, passing prior results forward in the stage instructions. The order is the LLM's decision; the sample does not script it.
- The editorial stages run one at a time (sequentially), since each stage depends on the previous stage's output.
- The routing and synthesis decisions are driven by the LLM, not by hardcoded rules.
- A single editorial run is in-flight per EditorInChief instance; concurrent runs use distinct instances.
- No external input (human approval) gate is included; external input is covered by the `publishing` sample.
- No authentication or authorization is required for this sample.
- No persistence beyond the task lifecycle and the shared workspace is needed.
- The polling model is sufficient; no push notifications or streaming are required for the core flow.
