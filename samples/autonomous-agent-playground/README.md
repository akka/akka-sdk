# Autonomous Agent Samples

Samples demonstrating the `AutonomousAgent` component — an LLM-driven component with built-in durable execution and multi-agent coordination. Unlike request-based `Agent` (which handles single request-response interactions), an Autonomous Agent runs as a process: iterating through an LLM decision loop until its assigned tasks are complete.

Each sample focuses on a specific capability or coordination pattern. They progress from minimal usage to sophisticated multi-agent systems.

## Web UI

A browser UI is bundled with the service. Boot the service (`mvn compile exec:java`) and open <http://localhost:9000/>.

## Overview

| Sample | Capabilities | Description |
| --- | --- | --- |
| **helloworld** | None | Simplest usage — single agent, single task, no coordination |
| **pipeline** | None (task dependencies) | 3-phase dependency chain: collect, analyze, report |
| **docreview** | None (attachments) | Document review with text content attachments |
| **dynamic** | None (runtime configuration) | One generic agent class configured per request with different instructions and capabilities |
| **research** | Delegation | Coordinator delegates to researcher and analyst, synthesises findings |
| **consulting** | Delegation + handoff | Delegate to specialists, hand off complex cases |
| **support** | Handoff | Triage classifies request, hands off to billing or technical specialist |
| **publishing** | Task dependencies + external input | Draft → human approval gate → publish, wired via task dependencies |
| **compliance** *(not yet implemented)* | Handoff + external input | Triage risk level, hand off high-risk, human approval |
| **debate** | Moderation | Moderator runs structured rounds between advocate and critic, synthesises a conclusion |
| **negotiation** | Moderation | Facilitator runs multi-round offers and counteroffers between buyer and seller |
| **peerreview** | Moderation | Moderator coordinates a panel of technical, style, and compliance reviewers |
| **devteam** | Team | Team lead decomposes project into tasks, developers self-coordinate |
| **brainstorm** *(not yet implemented)* | Team (emergent) | Team generates ideas on shared board, lead curates |
| **editorial** | Delegation + team + moderation | Editor-in-chief delegates stage tasks to section leads; each lead uses a different coordination capability internally |

---

## helloworld

The simplest autonomous agent sample. A single agent answers a question and returns a typed result.

**Agents:** QuestionAnswerer

**Tasks:** ANSWER → `Answer(answer, confidence)`

**Flow:** A user submits a question via HTTP. The endpoint creates a QuestionAnswerer instance and runs a single ANSWER task with the question as instructions. The agent processes the question and produces a structured answer with a confidence score. The user polls a separate endpoint to retrieve the result.

**Demonstrates:** Basic autonomous agent lifecycle — task creation, agent execution, typed result retrieval. No coordination, no tools, no multi-agent interaction. The minimum viable autonomous agent.

---

## pipeline

A single agent processes three tasks in a dependency chain: collect data, analyze it, then write a report. Task dependencies enforce execution order.

**Agents:** ReportAgent

**Tasks:**
- COLLECT → `ReportResult(phase, content)` — gather data on a topic
- ANALYZE → `ReportResult(phase, content)` — analyze collected data (depends on COLLECT)
- REPORT → `ReportResult(phase, content)` — write final report (depends on ANALYZE)

**Flow:** The endpoint creates all three tasks up front with explicit dependency relationships, then assigns them to a single ReportAgent instance. The agent processes them in dependency order — it cannot start ANALYZE until COLLECT completes, and cannot start REPORT until ANALYZE completes. The agent has domain tools (`collectData`, `analyzeData`) for the first two phases.

**Demonstrates:** Task dependencies as an ordering mechanism. Multiple tasks assigned to a single agent instance. Pre-created tasks (as opposed to `runSingleTask`). Sequential pipeline without multi-agent coordination — the ordering comes from task dependencies, not from handoff between agents.

---

## docreview

A single agent reviews a document for compliance, receiving the document content as a task attachment rather than inline in the instructions.

**Agents:** DocumentReviewer

**Tasks:** REVIEW → `ReviewResult(assessment, findings, compliant)`

**Flow:** A user submits a document and review instructions via HTTP. The endpoint creates a REVIEW task with the review instructions as task instructions and the document text attached as `TextMessageContent`. The agent reviews the attached document against the instructions and produces a structured compliance assessment with specific findings and an overall compliance verdict.

**Demonstrates:** Task attachments for passing large content to agents without embedding it in instruction text. Structured result types with multiple fields. Single-agent, single-task pattern with richer input than helloworld.

---

## dynamic

A single generic agent class is configured per request with different instructions and task capabilities. The same `DynamicAgent` code runs both the summarize and translate flows.

**Agents:** DynamicAgent — declared with no static instructions or capabilities; configured at runtime via `AgentSetup` before each task is assigned

**Tasks:**
- SUMMARIZE → `String` — produces a concise summary of the input content
- TRANSLATE → `String` — translates the input content to French

**Flow:** Two HTTP routes (`POST /dynamic/summarize`, `POST /dynamic/translate`) each create a fresh DynamicAgent instance, configure its instructions and accepted capability dynamically, then assign a single task. The summarize route sets summarization instructions and accepts only the SUMMARIZE task; the translate route sets translation instructions and accepts only the TRANSLATE task — same agent class, two different runtime specialisations.

**Demonstrates:** Runtime agent configuration. The same `AutonomousAgent` subclass with no static instructions or capabilities can be specialised per request via `AgentSetup`. Useful when many task variants share the same execution shape and the differences are best expressed as data rather than as separate agent classes.

---

## research

A coordinator agent delegates research to two specialist agents, then synthesises their findings into a unified brief. The first multi-agent sample, demonstrating the delegation (fan-out/fan-in) pattern.

**Agents:**
- ResearchCoordinator — receives the research topic, delegates to specialists, synthesises results
- Researcher — gathers facts and sources on a topic
- Analyst — identifies trends, implications, and actionable insights

**Tasks:**
- BRIEF → `ResearchBrief(title, summary, keyFindings)` — the top-level research output
- FINDINGS → `ResearchFindings(topic, facts, sources)` — factual research from Researcher
- ANALYSIS → `AnalysisReport(topic, assessment, trends)` — trend analysis from Analyst

**Flow:** A user submits a research topic. The endpoint creates a BRIEF task and assigns it to a ResearchCoordinator. The coordinator decides to delegate: it creates a FINDINGS task for the Researcher and an ANALYSIS task for the Analyst. Both specialists work in isolated contexts — they see only their own task. When both complete, their results flow back to the coordinator, which synthesises the facts and trends into a unified ResearchBrief.

**Demonstrates:** Delegation capability (`canDelegateTo`). Context partitioning — each specialist sees only its slice of the problem. Fan-out to parallel workers and fan-in for synthesis. The coordinator maintains full context and is responsible for coherence. Delegated agents shut down after their task completes.

---

## consulting

A coordinator that can both delegate routine research to a subordinate and hand off complex problems to a senior specialist. Demonstrates combining delegation and handoff in a single agent.

**Agents:**
- ConsultingCoordinator — assesses client problems, routes to appropriate expertise level
- ConsultingResearcher — performs targeted research on specific aspects (delegation target)
- SeniorConsultant — handles complex, high-stakes issues (handoff target)

**Tasks:**
- ENGAGEMENT → `ConsultingResult(assessment, recommendation, escalated)` — the client problem
- RESEARCH → `ResearchSummary(topic, findings)` — sub-task for routine investigation

**Flow:** A client submits a consulting problem. The coordinator assesses complexity using shared tools (`assessProblem`, `checkComplexity`). For standard problems, the coordinator delegates a RESEARCH task to the ConsultingResearcher, waits for findings, and synthesises a recommendation (escalated=false). For complex problems (regulatory, M&A), the coordinator hands off the entire ENGAGEMENT task to the SeniorConsultant, who takes full ownership and completes it (escalated=true).

**Demonstrates:** Composing delegation and handoff in a single agent. The key distinction: delegation creates a child task and retains ownership of the parent — the coordinator synthesises results. Handoff transfers ownership of the current task to another agent — the coordinator steps back entirely. Shared tools across agents for consistent assessment. Routing logic driven by the LLM using domain tools.

---

## support

A triage agent classifies customer support requests and hands off to the appropriate specialist. The pure handoff pattern — no delegation, just routing.

**Agents:**
- TriageAgent — classifies requests and routes to the right specialist
- BillingSpecialist — resolves billing disputes, payment issues, invoice queries
- TechnicalSpecialist — diagnoses and resolves technical problems, bugs, outages

**Tasks:** RESOLVE → `SupportResolution(category, resolution, resolved)`

**Flow:** A customer submits a support request. The TriageAgent receives a RESOLVE task, analyzes the request to determine its category (billing or technical), and hands off to the appropriate specialist. The specialist takes ownership of the same RESOLVE task, resolves the issue, and completes it with a typed resolution.

**Demonstrates:** Handoff capability (`canHandoffTo`). Sequential/relay pattern where control transfers between agents. All agents share the same task type — the task moves between agents rather than new tasks being created. The triage agent is lightweight (3 iterations) while specialists have more room to work (5 iterations). Clear role separation: classifier vs. resolver.

---

## publishing

A 3-task pipeline drafts a blog post, gates on human approval, and publishes — wired together with task dependencies and an unassigned task that a human completes through the API. There is no orchestrator agent; the dependency graph plus the human-completion endpoints provide the gating.

**Agents:**
- ContentAgent — drafts a blog post on the requested topic
- PublishingAgent — publishes an approved post (assigns URL and timestamp)

**Tasks:**
- DRAFT → `DraftPost` — produced by ContentAgent
- APPROVAL → `ApprovalDecision` — unassigned; depends on DRAFT; completed (or failed) by a human via the API
- PUBLISH → `PublishedPost` — depends on APPROVAL; produced by PublishingAgent

**Flow:** A user submits a topic. The endpoint creates all three tasks up front. DRAFT is assigned to a fresh ContentAgent. APPROVAL is created unassigned and depends on DRAFT — once the draft is ready, a human reads it via `GET /publishing/draft/{id}` and either approves it via `POST /publishing/approve/{id}` or rejects it via `POST /publishing/reject/{id}`; the endpoint assigns the approval task to the human and then completes or fails it. PUBLISH depends on APPROVAL and is assigned to a fresh PublishingAgent — it only runs if approval succeeds. If approval is rejected, the dependency chain causes PUBLISH to be cancelled.

**Demonstrates:** Task dependencies as the orchestration mechanism (same as `pipeline`), combined with an *unassigned task that a human completes through the API*. Shows that human-in-the-loop gating doesn't need a coordinator agent — the dependency graph plus an HTTP endpoint that assigns and completes the task is enough.

---

## compliance

*Not yet implemented.*

A triage agent assesses risk level and routes accordingly — low-risk requests are resolved directly, high-risk requests are handed off to a specialist that requires human approval before completing.

**Agents:** Compliance triage, compliance specialist

**Demonstrates:** Handoff with external input. Risk-based routing where the approval requirement depends on the classification. Combining automated triage with human oversight for high-stakes decisions.

---

## debate

A moderator orchestrates a structured debate between an advocate and a critic across multiple rounds, then synthesises a balanced conclusion.

**Agents:**
- DebateModerator — orchestrates rounds, synthesises the final conclusion
- Advocate — argues in favor of the position
- Critic — argues against / surfaces weaknesses

**Tasks:** DEBATE → `DebateResult(topic, synthesis, keyArguments)`

**Flow:** A user submits a debate topic. The DebateModerator receives the DEBATE task and runs up to 5 moderated rounds, alternating turns between Advocate and Critic. Each participant sees the running argument history. Once the rounds complete (or the moderator decides to stop early), the moderator returns a synthesis of the topic plus the key arguments raised on each side.

**Demonstrates:** Moderation capability — a built-in pattern where a moderator agent shepherds a fixed set of participants through structured rounds of exchange. The participants don't coordinate freely; the moderator drives the cadence and assembles the synthesis. Distinct from team self-coordination (`devteam`) and from delegation (`research`) — moderation gives the moderator full structural control over turns.

---

## negotiation

A facilitator coordinates a multi-round negotiation between a buyer and a seller until they converge on terms.

**Agents:**
- Facilitator — directs the negotiation, decides when to stop, declares the final outcome
- Buyer — negotiates from the buyer's perspective
- Seller — negotiates from the seller's perspective

**Tasks:** NEGOTIATE → `NegotiationResult(topic, outcome, finalOffer)`

**Flow:** A user submits a negotiation topic. The Facilitator runs up to 10 moderated rounds of offers and counteroffers between Buyer and Seller. Each party reads the prior offers and responds with their own move. The Facilitator stops when terms converge or the round limit hits, and returns the outcome plus the final offer.

**Demonstrates:** Moderation capability with two adversarial participants. The same structural pattern as `debate`, applied to converging negotiation rather than divergent argument. Shows that round-limited moderation generalises across different turn-taking domains.

---

## peerreview

A moderator coordinates a panel of specialist reviewers — technical, style, and compliance — to assess a document across multiple dimensions.

**Agents:**
- ReviewModerator — orchestrates the panel, synthesises findings
- TechnicalReviewer — assesses technical correctness
- StyleReviewer — assesses clarity and style
- ComplianceReviewer — assesses regulatory / policy compliance

**Tasks:** REVIEW → `ReviewResult(document, assessment, reviewerFindings)`

**Flow:** A user submits a document. The ReviewModerator coordinates the three specialists to review the document, gathers their findings, and synthesises an overall assessment with the per-reviewer findings called out separately.

**Demonstrates:** Moderation capability with a heterogeneous panel of three specialists. Where `debate` and `negotiation` use moderation for two adversarial parties, `peerreview` uses it for a multi-axis review. Compares against `research`'s delegation pattern: in delegation the coordinator decides what each specialist gets; here the moderator drives the protocol and aggregates a structured review.

---

## devteam

A team lead decomposes a software project into tasks. Developer agents claim tasks from a shared list, work on them independently, and message peers when coordination is needed. The lead monitors progress and disbands the team when done.

**Agents:** Team lead, developer agents (team members)

**Demonstrates:** Team capability with self-coordination. Shared task list where members autonomously claim and complete work. Peer messaging for coordination when tasks have dependencies. The team lead's role is decomposition and oversight, not micromanagement.

---

## brainstorm

*Not yet implemented.*

A team generates ideas on a shared board. Each agent contributes independently, building on or diverging from existing ideas. A lead curates the results — the final output emerges from accumulation and selection rather than explicit coordination.

**Agents:** Brainstorm lead, idea generators (team members)

**Demonstrates:** Team capability with emergent behavior. Indirect coordination through a shared environment (the idea board) rather than direct messaging. Agents influence each other through what they leave behind, not through conversation. The lead provides curation and selection, turning quantity into quality.

---

## editorial

An editor-in-chief coordinates three section leads by delegating a stage task to each. Each lead is itself a coordinator that uses a different capability internally — delegation, team leadership, and moderation — so the sample exercises capability mixing across a small hierarchy.

**Agents:**
- EditorInChief — top-level coordinator; delegates the research, writing, and review stages and synthesises the final article
- ResearchEditor — accepts a RESEARCH stage task; internally delegates to two Reporter instances on different angles, returns a digest
- Reporter — accepts a FINDINGS task; saves findings to the shared workspace
- WritingLead — accepts a DRAFT stage task; internally leads a writing team
- SectionWriter, CopyEditor — team members; accept SECTION tasks; share the workspace
- ReviewEditor — accepts a REVIEW stage task; internally runs a nested moderation over reviewers
- AccuracyReviewer, ReadabilityReviewer — review-panel participants

**Tasks:**
- ARTICLE → `Article(title, body, keyPoints)` — top-level, accepted by EditorInChief
- RESEARCH → `ResearchDigest(summary, documentIds)` — delegated to ResearchEditor
- FINDINGS → `ResearchFindings(angle, summary, documentId)` — delegated to Reporter
- DRAFT → `ArticleDraft(title, body, documentIds)` — delegated to WritingLead
- SECTION → `SectionDraft(sectionTitle, summary, documentId)` — claimed by writing-team members
- REVIEW → `ReviewReport(assessment, notes)` — delegated to ReviewEditor

**Flow:** A topic arrives at `POST /editorial`. The EditorInChief receives the ARTICLE task and delegates a stage task to each section lead — research, then writing, then review — feeding each result into the next stage's instructions. Each lead does its own inner coordination (delegate to researchers, lead a writing team, or moderate a review panel) to fulfil its stage task, then returns a typed result. The EditorInChief synthesises the final `Article` from the returned results. The model decides the order and whether to revisit a stage; nothing scripts the pipeline. Bulky artifacts (research notes, section drafts) live in a shared workspace via `DocumentTools` and are passed by document ID, while typed task results carry the structure between agent and worker.

**Demonstrates:** Coordination capabilities composed across a hierarchy, each in its natural role:

- Delegation at the top to drive the stages (each stage runs as its own held task)
- Delegation inside the research stage for parallel investigation
- Team leadership inside the writing stage for member self-coordination
- Moderation inside the review stage for structured turn-taking
- A shared workspace tool (`DocumentTools` over a Key-Value entity)
