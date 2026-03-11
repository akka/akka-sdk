# Feature Specification: TechDeepDive — Comprehensive Autonomous Agent Demo

**Feature Branch**: `007-deepdive`
**Created**: 2026-03-11
**Status**: Draft
**Input**: User description: "A comprehensive demo application exercising all AutonomousAgent coordination capabilities — handoff, delegation, collaborative teams, emergent coordination, task dependencies, external input, and LLM-driven looping — in a single coherent system that produces deep-dive technology articles."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Submit a Topic and Receive a Deep-Dive Article (Priority: P1)

A user submits a technology topic (e.g., "WebAssembly for server-side applications") and receives a thoroughly researched, debated, and reviewed article. The system autonomously coordinates 15 agents across brainstorming, research, debate, writing, and review phases. The user polls for status and retrieves the final article when complete.

**Why this priority**: This is the core end-to-end flow. Without it, there is no demo. Every other story adds depth to a phase within this flow.

**Independent Test**: Submit a topic via HTTP, wait for completion, verify the result contains a structured article with title, content, and summary derived from the topic.

**Acceptance Scenarios**:

1. **Given** the system is running, **When** a user submits `POST /deepdive` with a topic, **Then** the system returns a task ID and begins processing.
2. **Given** a task is in progress, **When** the user polls `GET /deepdive/{taskId}`, **Then** the response includes the current status and phase.
3. **Given** all phases complete and editorial approval is granted, **When** the user retrieves the result, **Then** the response contains a structured article (title, content, summary) that addresses the submitted topic.
4. **Given** a task is in progress, **When** the system reaches the editorial approval phase, **Then** the task pauses and waits for human input before completing.

---

### User Story 2 — Intake Routes Different Request Types (Priority: P2)

The intake agent classifies incoming requests and routes them to different orchestrators. A deep technical topic gets the full multi-phase pipeline. A simpler request (e.g., "quick take on the new React compiler") gets routed to a lightweight agent that produces a short opinion piece directly.

**Why this priority**: Demonstrates the handoff capability — ownership transfer between agents based on LLM classification. This is a distinct coordination pattern that must work independently.

**Independent Test**: Submit two different requests — one complex topic and one simple opinion request — and verify they are handled by different agents producing different result types.

**Acceptance Scenarios**:

1. **Given** a complex topic like "WebAssembly for server-side applications", **When** submitted, **Then** the intake agent hands off to the full deep-dive orchestrator.
2. **Given** a simple request like "quick take on the new React compiler", **When** submitted, **Then** the intake agent hands off to the quick-take agent, which produces a short opinion with a confidence score.
3. **Given** a handoff occurs, **When** the intake agent transfers the task, **Then** the intake agent stops processing and the target agent takes full ownership.

---

### User Story 3 — Brainstorm Generates Diverse Angles (Priority: P3)

Before research begins, multiple independent agents brainstorm angles on the topic. Each agent has a different perspective bias (contrarian, practical, historical). They write ideas to a shared space without coordinating directly. A curator agent selects the strongest angles as the research agenda.

**Why this priority**: Demonstrates the emergent/swarm coordination pattern — agents influencing each other indirectly through a shared environment rather than direct communication.

**Independent Test**: Submit a topic and verify the brainstorm phase produces a curated set of angles from multiple independent contributors, with diversity across the perspectives.

**Acceptance Scenarios**:

1. **Given** the brainstorm phase starts, **When** three angle generators run, **Then** each produces angles independently without seeing each other's direct output.
2. **Given** generators have different prompt biases, **When** the same topic is processed, **Then** the angles reflect contrarian, practical, and historical perspectives.
3. **Given** all generators have contributed, **When** the curator runs, **Then** it selects 3-4 strongest angles, merging overlapping ideas.
4. **Given** different topics are submitted, **When** brainstorming completes, **Then** different angle selections are produced — the output varies by topic.

---

### User Story 4 — Research Respects Task Dependencies (Priority: P3)

Four researchers investigate the topic. Two start immediately (trends, technical depth). Two others depend on the first pair — community sentiment research waits for trend data, comparison research waits for technical details. The dependency ordering is enforced by the framework, not by code logic.

**Why this priority**: Demonstrates task dependencies as a coordination mechanism — declarative ordering that the framework enforces without orchestration code.

**Independent Test**: Submit a topic and observe that dependent research tasks do not start until their prerequisites complete, while independent tasks run in parallel.

**Acceptance Scenarios**:

1. **Given** the research phase starts, **When** tasks are assigned, **Then** trend and technical researchers start immediately in parallel.
2. **Given** the trend researcher has not completed, **When** the community researcher is assigned, **Then** it remains pending until trend research completes.
3. **Given** the technical researcher has not completed, **When** the comparison researcher is assigned, **Then** it remains pending until technical research completes.
4. **Given** all four researchers complete, **When** results are collected, **Then** each produces structured findings with topic, facts, and sources.

---

### User Story 5 — Debate Team Exchanges Arguments (Priority: P3)

A moderator creates a debate team with an advocate and a skeptic. The debaters argue positions, read each other's arguments via peer messages, and refine their positions through multiple rounds. The moderator structures the rounds and synthesises conclusions.

**Why this priority**: Demonstrates the collaborative team pattern — peer-to-peer messaging where agents directly influence each other's reasoning, distinct from delegation where workers are isolated.

**Independent Test**: Submit a topic, observe the debate phase, and verify that debaters reference and respond to each other's arguments (not just generating independent positions).

**Acceptance Scenarios**:

1. **Given** the debate phase starts, **When** the moderator creates the team, **Then** advocate and skeptic are added as members with a shared task list.
2. **Given** debate tasks exist ("Opening arguments", "Rebuttals", "Final positions"), **When** members work, **Then** they claim tasks from the shared list.
3. **Given** the advocate has posted an argument, **When** the skeptic works on a rebuttal, **Then** the skeptic references or challenges the advocate's specific claims.
4. **Given** all debate rounds complete, **When** the moderator synthesises, **Then** the result identifies consensus points, disputes, and gaps in the research.

---

### User Story 6 — LLM-Driven Looping Between Phases (Priority: P2)

The orchestrator loops between phases based on LLM judgment, not coded conditions. If the debate reveals research gaps, the orchestrator sends targeted questions back to researchers. If reviewers flag issues, the orchestrator sends the article back to the writer. These loops are bounded (max 2 cycles each) but the decision to loop is the LLM's.

**Why this priority**: This is the defining characteristic of autonomous orchestration — LLM-driven control flow with no workflow steps or if-statements. Critical for the demo narrative.

**Independent Test**: Submit a topic and verify that when debate or review surfaces issues, the orchestrator loops back to the appropriate earlier phase with targeted context, and that loops are bounded.

**Acceptance Scenarios**:

1. **Given** the debate synthesis identifies gaps, **When** the orchestrator evaluates, **Then** it may delegate targeted research questions to researchers before proceeding to writing.
2. **Given** the fact-checker flags unsupported claims, **When** the orchestrator evaluates, **Then** it may send the article back to the writer with specific feedback.
3. **Given** a loop has occurred twice for the same phase pair, **When** the orchestrator evaluates again, **Then** it proceeds forward rather than looping indefinitely.
4. **Given** the debate finds no gaps, **When** the orchestrator evaluates, **Then** it proceeds directly to writing without looping.

---

### User Story 7 — Editorial Approval Gate (Priority: P2)

After the review phase passes, the system pauses for human editorial approval. A human reviewer can approve the article (completing it) or reject it with feedback (triggering another write-review cycle). This is the only point where the system requires external input.

**Why this priority**: Demonstrates the external input capability — human-in-the-loop gating. This is a distinct coordination feature and important for real-world applicability.

**Independent Test**: Submit a topic, let it reach the approval phase, verify the system pauses, then approve or reject and verify the system responds correctly.

**Acceptance Scenarios**:

1. **Given** reviews pass with no issues, **When** the editorial approval phase is reached, **Then** the task transitions to a waiting state and does not complete automatically.
2. **Given** the task is waiting for approval, **When** a human submits approval, **Then** the article is published as the final result.
3. **Given** the task is waiting for approval, **When** a human submits rejection with feedback, **Then** the orchestrator loops back to the writer with the feedback, then through review again.
4. **Given** a rejection-triggered revision completes review, **When** the approval phase is reached again, **Then** the system pauses for approval again.

---

### User Story 8 — Nested Orchestration Across Levels (Priority: P3)

The top-level orchestrator delegates to sub-teams that themselves use different coordination patterns internally. The debate phase is a delegation from the top orchestrator to a moderator, but the moderator internally runs a collaborative team. This creates 4 levels of nesting with different patterns at each level.

**Why this priority**: Demonstrates composability — coordination patterns nesting within each other. Important for showing that the framework supports realistic multi-level architectures.

**Independent Test**: Submit a topic and verify that the debate phase involves both delegation (top orchestrator → moderator) and team coordination (moderator → advocate + skeptic) within the same execution.

**Acceptance Scenarios**:

1. **Given** the debate phase starts, **When** the top orchestrator delegates to the moderator, **Then** the moderator internally creates a team rather than further delegating.
2. **Given** the team is active, **When** the advocate and skeptic work, **Then** they use team mechanics (shared task list, peer messaging) not delegation mechanics.
3. **Given** the debate completes, **When** the moderator returns results, **Then** the top orchestrator receives a synthesis and continues its own decision loop.

---

### Edge Cases

- What happens when a researcher's web search returns no useful results? The researcher should still produce findings (noting the lack of data) rather than failing silently.
- What happens when the LLM consistently decides to loop? Bounded by max iteration counts (max 2 research cycles, max 2 revision rounds) to prevent infinite loops.
- What happens when the intake agent cannot classify a request? It should default to the full deep-dive pipeline rather than failing.
- What happens when a team member (advocate/skeptic) fails to claim a debate task? The moderator should detect stalled tasks and either reassign or proceed with available contributions.
- What happens when the editorial reviewer never responds? The task remains in waiting state indefinitely — this is by design (human gates are not time-bounded).
- What happens when a brainstorm generator produces angles that overlap entirely with another generator's output? The curator merges duplicates and selects the most distinct set.
- What happens when a delegated agent fails (e.g., a researcher's LLM call errors)? The task fails with an error; the orchestrator's LLM sees the failure and decides whether to proceed without that input, retry, or fail the pipeline.

## Requirements *(mandatory)*

### Functional Requirements

**Intake & Routing**

- **FR-001**: System MUST accept topic submissions via HTTP and return a task identifier for tracking.
- **FR-002**: System MUST classify incoming requests and route them to either a full deep-dive orchestrator or a lightweight quick-take agent based on request complexity.
- **FR-003**: Routing MUST use the handoff pattern — the intake agent transfers task ownership completely and stops processing.

**Brainstorming**

- **FR-004**: System MUST run three independent brainstorm agents in parallel, each with a distinct perspective bias (contrarian, practical, historical).
- **FR-005**: Brainstorm agents MUST write ideas to a dedicated persistent shared board, isolated per execution, and MUST NOT coordinate directly with each other. The curator reads from this same board.
- **FR-006**: A curator agent MUST read all contributed ideas, merge overlaps, and select 3-4 strongest angles as the research agenda.

**Research**

- **FR-007**: System MUST delegate research to four specialist researchers: trend, technical, community, and comparison.
- **FR-008**: Trend and technical researchers MUST start immediately with no dependencies.
- **FR-009**: Community researcher MUST depend on trend researcher completion. Comparison researcher MUST depend on technical researcher completion. The framework MUST enforce this ordering.
- **FR-010**: All researchers MUST use web search and web fetch tools to gather data (URLs, excerpts, sources). Tools MUST support both live web access (for demos) and simulated/cached responses (for testing), with the same agent code in both modes.
- **FR-011**: Each researcher MUST produce structured findings including topic, facts, and sources.

**Debate**

- **FR-012**: System MUST create a debate team with a moderator (team lead), advocate, and skeptic as team members.
- **FR-013**: The moderator MUST create debate tasks in a shared task list and structure rounds via messages.
- **FR-014**: Advocate and skeptic MUST claim tasks from the shared list and exchange arguments via peer messages.
- **FR-015**: The moderator MUST synthesise debate conclusions identifying consensus, disputes, and research gaps.

**Writing & Review**

- **FR-016**: System MUST delegate article drafting to a writer agent with accumulated context (angles, research, debate synthesis).
- **FR-017**: System MUST delegate review to an editor (structure, clarity, style) and a fact-checker (claims vs. research).
- **FR-018**: Editor MUST return structured editorial feedback. Fact-checker MUST return a list of claim verifications with severity levels.

**Looping**

- **FR-019**: The orchestrator MUST loop between research and debate when the debate synthesis identifies research gaps, bounded at max 2 research cycles.
- **FR-020**: The orchestrator MUST loop between writing and review when reviewers flag issues, bounded at max 2 revision rounds.
- **FR-021**: Loop decisions MUST be made by the LLM based on the content of results, not by coded conditions.

**External Input**

- **FR-022**: After reviews pass, the system MUST pause for human editorial approval before completing the article.
- **FR-023**: System MUST accept approval (completing the task) or rejection with feedback (triggering a revision loop) via HTTP.

**Status & Results**

- **FR-024**: System MUST expose a status endpoint that returns the current top-level phase and a flat list of active and completed sub-tasks with their statuses.
- **FR-025**: The final article MUST be a structured result with title, content, and summary.
- **FR-026**: The quick-take result MUST be a structured result with title, opinion, and confidence score.

**Orchestration**

- **FR-027**: All orchestration decisions (phase ordering, looping, agent selection) MUST be driven by the LLM, not by coded workflow steps or conditional logic.
- **FR-028**: The system MUST support nested orchestration — the top orchestrator delegates to sub-orchestrators that use different coordination patterns internally.

**Failure Handling**

- **FR-029**: When a delegated agent or LLM call fails, the individual task MUST fail with an error. The orchestrator MUST NOT automatically fail the entire pipeline.
- **FR-030**: The orchestrator's LLM MUST decide how to handle a failed sub-task — continue without that input, retry via a new delegation, or fail the overall task.

### Key Entities

- **Article**: The top-level output of a deep-dive request. Contains title, content, and summary. Created at intake, completed after editorial approval.
- **QuickTake**: The output of a simple request. Contains title, opinion, and confidence score. Created and completed by the quick-take agent.
- **Angle**: A brainstormed perspective on the topic. Contains title, description, and priority. Multiple angles are generated and curated into a research agenda.
- **ResearchFindings**: Structured output from a researcher. Contains topic, facts, and sources. Four sets of findings are produced per deep-dive.
- **DebateSynthesis**: The moderator's summary of the debate. Contains consensus points, disputes, and identified gaps.
- **DebatePosition**: An individual debater's contribution. Contains position, arguments, and supporting evidence.
- **ArticleDraft**: A draft produced by the writer. Contains title, content, and word count.
- **EditReview**: Editorial feedback. Contains assessment, suggestions, and approval status.
- **FactCheckResult**: Fact-checking output. Contains a list of claim verifications and an overall verification status.
- **ClaimVerification**: An individual claim check within a fact-check result. Contains the claim, verification status, and supporting evidence reference.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can submit a topic and receive a completed deep-dive article through the full pipeline (brainstorm → research → debate → write → review → approval) in a single interaction.
- **SC-002**: The system exercises all six coordination capabilities (handoff, delegation, team, emergent, task dependencies, external input) within a single request.
- **SC-003**: Different topic submissions produce different brainstorm angles, different researcher selections by the LLM, and different debate outcomes — demonstrating that orchestration is LLM-driven, not hard-coded.
- **SC-004**: The system correctly enforces task dependencies — dependent researchers do not start until prerequisites complete.
- **SC-005**: Debate team members demonstrably reference each other's arguments — the exchange is interactive, not independent.
- **SC-006**: When debate or review surfaces issues, the orchestrator loops back at least once before proceeding — and does not loop more than the bounded maximum.
- **SC-007**: The system pauses at the editorial approval gate and does not complete until human input is provided.
- **SC-008**: Simple requests routed via handoff to the quick-take agent complete without invoking the full pipeline.
- **SC-009**: 15 distinct agent types participate across 4 nesting levels within a single deep-dive execution.

## Clarifications

### Session 2026-03-11

- Q: How should the brainstorm shared board be implemented? → A: Dedicated persistent shared store that generators write to and the curator reads from, isolated per execution.
- Q: Should researcher tools use live web access or simulated data? → A: Live for demos, simulated for tests — same agent code, tool implementation swapped per environment.
- Q: What is the failure handling strategy for agent/LLM failures? → A: Fail the individual task with an error; the orchestrator LLM decides whether to continue, retry, or fail the pipeline.
- Q: What level of detail should the status endpoint expose? → A: Top-level phase plus a flat list of active and completed sub-tasks with their statuses.
- Q: Should the system support concurrent deep-dive executions? → A: Yes, multiple deep-dives can run simultaneously, each fully isolated.

## Assumptions

- The system has access to a configured LLM provider for all agents.
- Web search and web fetch tools are available and functional for researcher agents.
- The "shared board" for the brainstorm swarm is a dedicated persistent store that generators write to and the curator reads from. Each execution's board is isolated.
- Editorial approval has no timeout — the task waits indefinitely for human input.
- The quick-take agent is a simpler, self-contained agent that does not invoke sub-teams.
- Max iteration limits (2 research cycles, 2 revision rounds) are configured in the orchestrator's goal/instructions, not enforced by framework constraints.
- Multiple deep-dive executions can run concurrently. Each execution is fully isolated — separate agent instances, separate tasks, separate shared board.
