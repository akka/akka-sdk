<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Components](components/index.html)
- [Autonomous Agents](autonomous-agents.html)

<!-- </nav> -->

# Implementing autonomous agents

An Autonomous Agent is an LLM-driven component with built-in durable execution and multi-agent collaboration. Unlike a request-based [Agent](agents.html), which handles a single request-response interaction (and requires a [Workflow](workflows.html) wrapper for multi-step orchestration), an Autonomous Agent runs as a process — iterating through an LLM decision loop until its assigned tasks are complete.

The developer configures a *strategy* — instructions, tools, coordination capabilities, and execution constraints — and the LLM drives the execution. The agent iterates: call the LLM, execute tools, check task status, repeat — until all tasks are complete or the iteration limit is reached. The execution is durable, surviving restarts and failures.

A key strength of the Autonomous Agent is its coordination capabilities. Multiple agents can work together — delegating subtasks to specialist workers, handing off work to peers, or collaborating as teams with shared task lists and messaging. These coordination patterns are built into the component model: the developer declares which patterns an agent participates in, and the framework provides the corresponding tools to the LLM automatically. This means multi-agent systems can be assembled from focused, single-purpose agents without writing orchestration code.

## <a href="about:blank#_when_to_use"></a> When to use an autonomous agent

Use a request-based `Agent` when:
- The interaction is request-response — one question, one answer
- You need fine-grained control over the prompt and effect chain
- The orchestration is handled externally (e.g., by a `Workflow`)

Use an `AutonomousAgent` when:
- The work requires multiple LLM iterations with tool use
- Tasks have their own lifecycle and may be handed off between agents
- You need durable multi-step execution without writing workflow orchestration
- You want coordination between multiple agents (delegation, handoff, teams)

| | Agent | Autonomous Agent |
| --- | --- | --- |
| **Execution model** | Request-response | Durable process loop |
| **LLM interaction** | Single round-trip (with tool calls) | Multiple iterations until task complete |
| **Orchestration** | External (Workflow) | Built-in (LLM-driven) |
| **Unit of work** | Command handler return value | Task entity with typed result |
| **Multi-agent** | Via Workflow steps | Via coordination capabilities |
| **State** | Stateless (session memory for context) | Stateless (tasks carry state) |
| **Streaming** | Supported | Not applicable |

## <a href="about:blank#_identify_the_agent"></a> Identify the agent

Like all components, an Autonomous Agent needs a **component ID** — a stable identifier for the component class — supplied via the `@Component` annotation.

Unlike a request-based Agent (which uses a session ID), an Autonomous Agent uses an **instance ID** supplied when the agent is started via the `ComponentClient`. Each instance runs independently with its own task queue and execution loop. A common pattern is to use a `UUID` for one-off work, or a meaningful business identifier when the agent instance should be reused.

## <a href="about:blank#_skeleton"></a> Skeleton

An Autonomous Agent extends `AutonomousAgent` and implements a single `strategy()` method that returns the agent's configuration.

<!-- TODO: code snippet — minimal QuestionAnswerer agent with @Component, strategy(), goal, accepts, maxIterations -->

| **1** | Create a class that extends `AutonomousAgent`. |
| **2** | Annotate the class with `@Component` and pass a unique identifier for this agent type. |
| **3** | Implement the `strategy()` method to define the agent's behaviour. |

There are no command handlers. The agent is a process — it runs, works on assigned tasks, and stops.

## <a href="about:blank#_strategy_configuration"></a> Strategy configuration

The `strategy()` method returns a `Strategy` built with a fluent builder API. `Strategy.autonomous()` starts the builder.

### Goal

The goal is the agent's high-level purpose — what it achieves, not how it coordinates. The runtime combines the goal with capability-specific context and tool descriptions to build the system message sent to the LLM.

<!-- TODO: code snippet — strategy with goal text -->

### Accepted task types

An agent declares which task types it can work on with `accepts()`. The agent will only process tasks whose definition matches one of the accepted types.

<!-- TODO: code snippet — .accepts(MyTasks.REVIEW, MyTasks.SUMMARIZE) -->

### Tools

Domain tools are added with `tools()`. Each element can be an object instance or a `Class`. If a `Class` is provided, it is instantiated at runtime using the configured `DependencyProvider`, which means it can receive injected dependencies like `ComponentClient`.

Methods on tool objects must be annotated with `@FunctionTool`. The description is included in the LLM context to guide tool selection.

Components — Workflows, Event Sourced Entities, Key Value Entities, and Views — can also be used as tools. Pass the component `Class` (not an instance) to `tools()`.

<!-- TODO: code snippet — .tools(new MyTools()) with @FunctionTool methods -->

Tools can also be defined directly on the agent class as `@FunctionTool` methods, just like with request-based Agents.

<!-- TODO: code snippet — @FunctionTool method on agent class -->

### MCP tools

Remote MCP (Model Context Protocol) tool endpoints are added with `mcpTools()`.

<!-- TODO: code snippet — .mcpTools(RemoteMcpTools.create(...)) -->

### Guardrails

Request and response guardrails constrain the LLM interaction at each iteration. Request guardrails evaluate prompts before they are sent to the LLM. Response guardrails evaluate responses received from the LLM.

<!-- TODO: code snippet — .requestGuardrails(MyGuardrail.class).responseGuardrails(MyOtherGuardrail.class) -->

See [Guardrails](agents/guardrails.html) for details on implementing guardrail classes.

### Memory

Session memory configuration controls how conversation history across iterations is managed. By default, memory accumulates across iterations. Use `MemoryProvider` to limit the window or disable memory.

<!-- TODO: code snippet — .memory(MemoryProvider.limitedWindow().readLast(20)) -->

### Iteration limit

`maxIterations()` sets the maximum number of LLM iterations before the agent fails the current task. The default is 10. Set this based on the expected complexity of the work — simple tasks may need only 3 iterations, while complex coordination may need more.

<!-- TODO: code snippet — .maxIterations(5) -->

### Model

By default, the agent uses the model configured in `application.conf` (see [Configuring the model](agents.html#model)). Override with `modelProvider()` to use a different model for this agent.

<!-- TODO: code snippet — .modelProvider(ModelProvider.openAi()...) -->

### Complete strategy example

<!-- TODO: code snippet — full strategy() method showing goal, accepts, tools, maxIterations, and a coordination capability together -->

## <a href="about:blank#_tasks"></a> Tasks

Two design decisions shape the Autonomous Agent model. First, an agent is a **process** — it runs, has a strategy, handles work. It doesn't have a result type. Second, a **task** is a separate persistent entity — a typed unit of work with its own identity, result schema, and lifecycle, independent of any agent.

This separation is deliberate. Because tasks exist independently, they can be handed off between agents, queried by external clients, and managed with their own lifecycle. An agent can be stopped and restarted without affecting task state. Multiple tasks can be assigned to a single agent, or a single task can move through several agents via handoff. The task carries the typing (what the expected result looks like), while the agent carries the capability (how to produce it).

Tasks are the coordination primitive — they flow between agents, and the coordination patterns (delegation, handoff, teams) all operate through task creation, assignment, and completion.

### Defining tasks

Task definitions are immutable constants, typically declared as `static final` fields. A definition specifies the task name, a description of what kind of work it represents, and the expected result type.

<!-- TODO: code snippet — TaskDefinition constants with .define(), .description(), .resultConformsTo() -->

The result type is a Java record. The agent's LLM output is validated against this schema, and the typed result is available when querying the completed task.

<!-- TODO: code snippet — result record (e.g. ResearchBrief with title, summary, keyFindings) -->

### Creating task instances

Task definitions are templates. To create an actual task instance, add per-request details — instructions and optional attachments — to a definition. The definition itself is unchanged (all methods return new instances).

<!-- TODO: code snippet — REVIEW.instructions("Review this document").attach(TextMessageContent.from(document)) -->

### Task lifecycle

A task progresses through these statuses:

| Status | Description |
| --- | --- |
| `PENDING` | Created but not yet started by an agent |
| `IN_PROGRESS` | An agent is actively working on it |
| `COMPLETED` | Finished successfully with a typed result |
| `FAILED` | Failed (iteration limit exceeded, unrecoverable error) |

<!-- TODO: WAITING_FOR_INPUT status will be added with the external input capability -->


### Task snapshots

Query a task's current state with `ComponentClient`. The snapshot includes the status, description, instructions, typed result (if completed), and failure reason (if failed).

<!-- TODO: code snippet — componentClient.forTask(MyTasks.REVIEW).get(taskId) -->

### Task dependencies

Tasks can declare dependencies on other tasks. A task with dependencies will not be started by the agent until all dependencies have completed. This enables pipeline patterns where work flows through ordered phases.

<!-- TODO: code snippet — ANALYZE.instructions(...).dependsOn(collectTaskId) -->

Dependencies are specified by task ID, which means tasks must be created (via `TaskClient.create()`) before they can be referenced as dependencies.

### Task notifications

<!-- TODO: Task notification stream API is defined in design notes but not yet implemented. The following describes the planned design. -->

Tasks publish a live notification stream for async observation. Notification types include `TaskCreated`, `TaskAssigned`, `TaskStarted`, `TaskCompleted`, `TaskFailed`, `TaskHandedOff`, `DecisionRequested`, and `InputProvided`.

## <a href="about:blank#_client_api"></a> Client API

Autonomous agents and tasks are managed through `ComponentClient`.

### Running a single task

The simplest pattern: create a task, start an agent, and automatically stop the agent when done. `runSingleTask` handles all of this in one call.

<!-- TODO: code snippet — componentClient.forAutonomousAgent(MyAgent.class, UUID.randomUUID().toString()).runSingleTask(task) -->

This returns the task ID for later status checks. Each call spins up an independent agent instance.

### Managing tasks and agents separately

For more control — multiple tasks, pipelines, or long-lived agents — create tasks and assign them separately.

**Create tasks:**

<!-- TODO: code snippet — componentClient.forTask(MyTasks.COLLECT).create(task) returning taskId -->

**Assign tasks to an agent:**

<!-- TODO: code snippet — componentClient.forAutonomousAgent(MyAgent.class, agentId).assignTasks(taskId1, taskId2, taskId3) -->

Tasks are queued if the agent is busy. The agent processes them in order, respecting dependencies.

**Stop an agent:**

<!-- TODO: code snippet — componentClient.forAutonomousAgent(MyAgent.class, agentId).stop() -->

### Querying task results

Task results are typed based on the task definition's `resultConformsTo` type.

<!-- TODO: code snippet — TaskSnapshot<ResearchBrief> snapshot = componentClient.forTask(ResearchTasks.BRIEF).get(taskId); snapshot.result().keyFindings() -->

## <a href="about:blank#_coordination_patterns"></a> Coordination patterns

LLMs perform best with focused context. As tasks grow more complex, a single agent's context becomes diluted — too much information, too many concerns, competing objectives. Multi-agent patterns address this by scoping context so that each agent can operate with clarity.

The coordination pattern shapes system behaviour — not just efficiency, but coherence, predictability, error propagation, and the kinds of solutions a system can discover. Parallelism provides a second motivation — when work can be decomposed into independent subtasks, multiple agents can run simultaneously for direct speedup or greater thoroughness. Multi-agent patterns also enable model specialisation — using a smaller, faster model for triage, a stronger model for complex reasoning, or a model fine-tuned for a specific domain. Each agent can use the model best suited for its responsibilities.

Four patterns cover the design space. Each has a distinct model for how context is scoped and flows between agents, a corresponding single-agent approach, and an analogy to established concurrency models. In practice, these patterns are conceptual tools for understanding the design space — real systems often blend them.

### Sequential (handoff)

Control transfers between agents. One agent is active at a time. Context accumulates or transforms as it moves through the chain.

*Group:* Relay — passing along a chain.

*Single-agent counterpart:* An agent with a plan, executing steps in order.

*Concurrency model analogy:* Continuations — each agent picks up where the previous one left off.

*Parallelism:* None — only one agent active at a time.

*Context flow:* Forward. Each agent receives context from the previous stage (accumulated, summarised, or transformed), adds its contribution, and passes it on.

*Behaviour:* The simplest multi-agent pattern, and the most coherent, with a single thread of reasoning throughout. But this creates path dependency: early decisions constrain later ones, and errors compound rather than correct. The sequence order matters — research-then-plan produces different results than plan-then-research. Real workflows are often graphs rather than linear chains, which is where sequential composes with delegative.

*When to use:* Workflow processes with clear stages and specialisation at each stage. Better for refinement, where each stage improves on the last, than exploration. The linear sequence is easy to trace and interpret.

*Example:* A triage agent receives a customer request, determines the category, and hands off control to the appropriate specialist agent. The specialist now owns the interaction.

### Delegative (fan-out / fan-in)

A coordinator assigns subtasks to workers. Workers operate in isolated contexts. Results flow back for synthesis.

*Group:* Hierarchy — coordinator and workers.

*Single-agent counterpart:* An agent with skills, loading focused capabilities as needed.

*Concurrency model analogy:* Fork/join, futures — the coordinator fans out work and collects results.

*Parallelism:* High — subtasks can run simultaneously.

*Context flow:* Partitioned. Each worker sees only its slice. Workers are deliberately isolated from each other. The coordinator sees the original task and the results that come back, but not the internal reasoning of each worker.

*Behaviour:* Context isolation is the defining feature. Each worker gets a focused context and can go deep on its subproblem without distraction or influence from the others. But isolation also means no cross-pollination. The responsibility for coherence falls entirely on the coordinator — it needs to perform well at both decomposition and synthesis. A variant is competitive delegation, where the coordinator assigns the same task to multiple workers and the best result is selected. At large scale, with many agents and statistical selection, this blurs into the emergent pattern.

*When to use:* Tasks that decompose into distinct subtasks benefiting from isolated, focused contexts, or when independent perspectives are needed. Good for parallel execution to reduce latency, or broad exploration with many workers generating independent attempts.

*Example:* A research coordinator delegates fact-gathering to a researcher and trend analysis to an analyst. Both work in parallel with isolated contexts. The coordinator synthesises their findings into a brief.

### Collaborative (team)

Peer agents share context and communicate directly. They work together on a common problem.

*Group:* Team — cooperation between peers.

*Single-agent counterpart:* An agent with internal debate or chain-of-thought verification.

*Concurrency model analogy:* Actor model — independent agents exchanging messages.

*Parallelism:* Medium — agents work simultaneously but can be limited by coordination.

*Context flow:* Exchanged. Agents communicate as they work, sending messages that shape each other's reasoning. Each agent has its own context, influenced by the messages it receives — more like a conversation than a shared view.

*Behaviour:* The strength of this pattern is mutual awareness — agents can build on, question, and correct each other. Debate can surface better answers than any single agent would find. But shared context also enables groupthink: agents can converge on ideas too early, reinforce each other's biases, or defer to whichever agent communicates first or most confidently. The more interdependent the collaboration, the more it resembles a single agent rather than multiple agents working in parallel.

*When to use:* Interdependent subtasks where agents need to see each other's work, or when quality benefits from debate or review between peers. Good for error-catching through challenge, or when different expertise needs to be actively integrated — not just combined afterward.

*Example:* A team lead decomposes a project into tasks. Developer agents claim tasks from a shared list, work on them independently, and message peers when coordination is needed. The lead monitors progress and disbands the team when done.

### Emergent (swarm)

Many agents operate in parallel with minimal individual context, following simple rules. Complex behaviour arises through stigmergy — modifying a shared environment that influences other agents.

*Group:* Swarm — independent action, collective effect.

*Single-agent counterpart:* An agent with sampling diversity (multiple completions, best-of-n).

*Concurrency model analogy:* Tuple spaces, blackboard systems — agents interact through a shared data space rather than direct communication.

*Parallelism:* High — agents operate completely independently.

*Context flow:* Indirect. Agents see the environment, not each other. They influence each other only through what they leave behind in the shared state.

*Behaviour:* This is a pattern for scale and exploration, and the most difficult to implement effectively. The system's behaviour is statistical — the aggregate of many simple actions. This makes it resilient (no single point of failure) but also unpredictable, since behaviour emerges from interactions that weren't explicitly designed. Selection is important — to find the signal in the noise. Selection can be emergent (better contributions naturally get reinforced) or external (something outside evaluates and curates the output). In practice, you may want both.

*When to use:* Large-scale problems with many similar agents and tolerance for probabilistic outcomes. Good for broad exploration, resilience to individual failure, and avoiding early convergence on a solution.

*Example:* A brainstorm team generates ideas on a shared board. Each agent contributes independently. A lead curates the results, with the final output emerging from accumulation and refinement rather than explicit coordination.

## <a href="about:blank#_coordination_capabilities"></a> Coordination capabilities

The coordination patterns above are implemented through capabilities — each adds tools to the agent's LLM loop. The agent's LLM sees domain tools alongside coordination tools and decides which to call. Capabilities map to the patterns: `canHandoffTo` enables sequential patterns, `canDelegateTo` enables delegative patterns, and team capabilities enable collaborative and emergent patterns.

### Delegation

A coordinator declares delegation targets with `canDelegateTo()`. The framework provides a tool for each target that the LLM can call. For example, if the coordinator can delegate to a `Researcher`, the LLM sees a `delegateToResearcher` tool. When called, the tool creates a task, spawns a `Researcher` agent, assigns the task, awaits the result, and returns it to the coordinator's tool loop.

<!-- TODO: code snippet — .canDelegateTo(Researcher.class, Analyst.class) -->

The coordinator pauses while workers execute, then resumes with their results. Delegated agents shut down after their task completes. The coordinator maintains full context and is responsible for synthesising the results.

**Context flow:** Partitioned. Each worker sees only its assigned task. Workers are isolated from each other. The coordinator sees the original task and the results that come back.

**When to use:** Tasks that decompose into distinct subtasks benefiting from isolated, focused contexts. Good for parallel execution and when independent perspectives are needed.

<!-- TODO: code snippet — full ResearchCoordinator example with canDelegateTo -->

### Handoff

An agent declares handoff targets with `canHandoffTo()`. The framework provides a tool that transfers the current task to another agent. Unlike delegation, handoff transfers ownership — the current agent is done and the target agent takes over.

<!-- TODO: code snippet — .canHandoffTo(BillingSpecialist.class, TechnicalSpecialist.class) -->

Unlike delegation, handoff is peer-to-peer — the handing-off agent directly reassigns the task, closer to the actor `forward` pattern. The task entity updates its assignee and records the handoff context. The new agent picks up the task with the accumulated context from the handoff.

**Context flow:** Forward. Context accumulates or transforms as it moves through the chain.

**When to use:** Routing and triage patterns where a classifier determines which specialist should handle a request. Clear stages with specialisation at each stage.

<!-- TODO: code snippet — full TriageAgent example with canHandoffTo -->

### Teams

<!-- TODO: Team capability is defined in design notes but not yet implemented in the SDK API. The following describes the planned design. -->

A team lead forms a team with a shared task list. Members run autonomously — claiming tasks, working on them, messaging peers, and completing work independently. The lead monitors progress and disbands the team when done.

The team capability provides tools for the lead to create teams, add members, create tasks in the shared list, check team status, send messages, and disband the team. Team members get task list and messaging tools injected automatically. Members iterate in a loop: discover tasks, claim, work, complete, check for more. They stop when the team is disbanded.

**Context flow:** Exchanged. Agents communicate as they work, sending messages that shape each other's reasoning. Each agent has its own context, influenced by the messages it receives.

**When to use:** Interdependent work where agents need to see each other's contributions, or when quality benefits from peer review. Good for collaborative problem-solving where different expertise needs to be actively integrated.

### External input

<!-- TODO: External input capability is defined in design notes but not yet implemented in the SDK API. The following describes the planned design. -->

When an agent needs external input to proceed — human approval, clarification, or any gated decision — a task guard rule evaluates the task result and determines whether it requires input, should be blocked, or can be accepted.

When input is required, the task transitions to a waiting state. The input response is delivered back into the agent's context and processing resumes.

**When to use:** Human-in-the-loop patterns where certain decisions require external approval or additional information before proceeding.

### Composing capabilities

Capabilities compose freely. An agent can combine:
- **Handoff with external input** — triage low-risk directly, hand off high-risk to a specialist that requests human approval
- **Delegation with handoff** — delegate to specialists for most work, hand off edge cases to a different agent type
- **Delegation with external input** — delegate writing and editing to specialists, request editorial approval for the final output
- **Teams with external input** — team members collaborate, with human approval required for final publication

<!-- TODO: code snippet — strategy with combined .canDelegateTo() and .canHandoffTo() -->

## <a href="about:blank#_context_management"></a> Context management

Context management is the primary motivation for multi-agent systems. An agent's context shapes its behaviour — what it sees determines what it attends to, how it reasons, and what solutions it considers.

As tasks grow more complex, a single agent's context becomes diluted. Multi-agent patterns address this by scoping context so that each agent operates with clarity. The coordination pattern you choose is a context management strategy.

Think about context scoping in terms of these aspects:

**Focus.** Narrow the task. An agent with a narrow context concentrates on its subproblem — attending to details that matter, reasoning more deeply, selecting better solutions. This applies to tools too: agents with narrowly scoped tools select more accurately than agents with broad toolsets.

**Relevance.** Keep context current. As work progresses, context accumulates with the residue of earlier steps. When each agent gets a fresh context for its portion of the work, with history summarised at communication points, attention stays on what's current.

**Isolation.** Separate concerns. When an agent works across multiple concerns, context from one can interfere with another. Giving each concern its own agent avoids this cross-contamination.

**Independence.** Think without influence. When agents see each other's work, they converge — anchoring on early ideas, falling into groupthink. When agents work without access to each other's reasoning, they produce diverse approaches rather than early consensus.

## <a href="about:blank#_when_to_use_multiple_agents"></a> When to use multiple agents

A well-designed single agent with appropriate tools can accomplish a lot. Multi-agent patterns introduce real overhead — in tokens, in coordination, and in complexity.

Consider multiple agents when:
- Accumulated context is degrading performance
- Subtask context is contaminating other work
- A single perspective is limiting solution quality
- The agent's toolset or prompt is too broad to be effective
- Subtasks are genuinely independent and could benefit from parallel execution
- Explainability and auditability are requirements — distinct agents with clear roles make reasoning auditable

Multi-agent systems can also enable more dynamic behaviour. Each execution is shaped by the interplay between agents rather than a single initial prompt. A delegative coordinator can tailor its decomposition per execution. Collaborative agents adapt to each other's contributions as they work. Emergent agents respond to evolving shared state. The tradeoff is predictability — the more dynamic the coordination, the harder it is to anticipate and test the system's behaviour for any given input.

A reasonable starting point is: use the simplest approach that works, and add multi-agent complexity when there's evidence that a single agent is hitting its limits. At the same time, it can be worth experimenting with multi-agent approaches early to understand the tradeoffs — particularly when explainability or auditability are requirements.

### Decomposing into multiple agents

The intuitive approach is to split by work type — one agent plans, another implements, a third reviews. But this often means each agent needs most of the same context, making the separation artificial while adding real coordination costs.

Boundaries are more effective when they follow context rather than problem structure — what Anthropic describes as the distinction between [context-centric and problem-centric decomposition](https://www.anthropic.com/engineering/building-effective-agents). Effective boundaries are where focus narrows to a different task, where isolation prevents contamination of concerns, or where independence produces better diversity than shared attention.

### Composing patterns

The coordination patterns compose at different levels. Some examples:

**Sequential pipeline with delegative stages.** A document flows through stages: research, analysis, writing, review. Each stage can internally delegate to multiple workers. The analysis stage fans out to workers examining different aspects in parallel, then synthesises before passing forward.

**Delegative coordinator with collaborative teams.** A coordinator delegates to specialist teams. Within each team, agents collaborate — debating approaches, catching errors, building on each other's work. The coordinator only sees the final output from each team.

**Collaborative debate with delegative research.** Agents debate an approach, but when they need evidence they delegate fact-gathering to workers. Workers return findings and the debate resumes with new information. Useful when a decision process needs to be informed by research that can happen in parallel.

**Delegative coordinator with sequential workers.** A coordinator fans out to workers, but each worker internally runs a multi-step pipeline (research, draft, self-review) before returning results.

## <a href="about:blank#_testing"></a> Testing

Autonomous agents are tested using `TestModelProvider` to mock LLM responses, following the same pattern as request-based agents.

<!-- TODO: code snippet — TestKitSupport test with TestModelProvider for AutonomousAgent, showing task creation and result assertion -->

Key points:
- Create a `TestModelProvider` instance as a field
- Register it in `testKitSettings()` with `.withModelProvider(MyAgent.class, modelProvider)`
- Use `.fixedResponse()` to control what the LLM returns
- Create tasks and assign them to agents using `componentClient`
- Use `Awaitility.await()` to poll for task completion since execution is asynchronous

## <a href="about:blank#_samples"></a> Samples

| Sample | Capabilities | Description |
| --- | --- | --- |
| **helloworld** | None | Simplest usage — single agent, single task, no coordination |
| **pipeline** | None (task dependencies) | 3-phase dependency chain: collect, analyze, report |
| **docreview** | None (attachments) | Document review with text content attachments |
| **research** | Delegation | Coordinator delegates to researcher and analyst, synthesises findings |
| **consulting** | Delegation + handoff | Delegate to specialists, hand off complex cases |
| **support** | Handoff | Triage classifies request, hands off to billing or technical specialist |
| **publishing** | Delegation + external input | Delegate writing and editing, request editorial approval |
| **compliance** | Handoff + external input | Triage risk level, hand off high-risk, human approval |
| **debate** | Team | Moderator with debaters, collaborative argumentation |
| **devteam** | Team | Team lead decomposes project into tasks, developers self-coordinate |
| **brainstorm** | Team (emergent) | Team generates ideas on shared board, lead curates |
| **editorial** | Team + external input | Team lead with writers, human approval of final publication |

<!-- <footer> -->
<!-- <nav> -->
[Agents](agents.html) [Choosing the prompt](agents/prompt.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
