<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Use cases](index.html)
- [Multi-agent systems](multi-agent-systems.html)

<!-- </nav> -->

# Multi-agent systems

Compose specialized agents into systems that decompose complex problems, isolate context, and coordinate naturally. The [Autonomous Agent](../autonomous-agents.html) component provides four built-in coordination capabilities: each agent declares which patterns it participates in, and the framework wires the corresponding tools into the agent’s tool loop automatically.

## <a href="about:blank#_overview"></a> Overview

### <a href="about:blank#_when_to_use_this_pattern"></a> When to Use This Pattern

- Your problem benefits from specialist agents with focused contexts rather than one generalist with a diluted context.
- Subtasks are independent enough to run in parallel.
- You need triage or routing where a classifier hands off to the right specialist.
- You want peer agents that collaborate, debate, or review each other’s work.
- You want moderated turn-taking conversations such as panel reviews or negotiations.

### <a href="about:blank#_coordination_patterns"></a> Coordination patterns

Four coordination patterns cover the design space, each implemented by a capability:

- **Sequential / handoff.** Control transfers between agents. Use `canHandoffTo(…​)` on a `TaskAcceptance` capability. Examples: triage routing, escalation chains.
- **Delegative / fan-out and fan-in.** A coordinator dispatches subtasks to isolated workers. Use the `Delegation` capability. Examples: research with multiple specialists, parallel analysis.
- **Collaborative / team.** Peer agents share a task list and message each other. Use the `TeamLeadership` capability. Examples: dev teams that self-coordinate, brainstorm groups.
- **Moderated conversation.** A moderator orchestrates turn-taking participants in scripted or directed mode. Use the `Moderation` capability. Examples: peer reviews, negotiations, debates.
See [Coordination patterns](../autonomous-agents/coordination.html) for the conceptual background and [Coordination capabilities](../autonomous-agents/capabilities.html) for the API details.

### <a href="about:blank#_akka_components_involved"></a> Akka Components Involved

- **[Autonomous Agents](../autonomous-agents.html)** declare coordination capabilities that the framework wires into the agent’s tool loop.
- **[HTTP Endpoints](../http-endpoints.html)** trigger top-level agents and surface task results.
- **[Entities](../event-sourced-entities.html)** and **[Views](../views.html)** can be exposed as function tools or queried through `ComponentClient`.

## <a href="about:blank#_sample_projects"></a> Sample Projects

The [autonomous-agent-playground](../autonomous-agents.html#samples) includes one sample per coordination pattern:

- `research`: delegation. Coordinator delegates to a researcher and an analyst, then synthesizes a brief.
- `support`: handoff. Triage classifies a request and hands off to a billing or technical specialist.
- `consulting`: composed delegation and handoff in one coordinator.
- `peerreview`: moderation in scripted mode (panel of specialist reviewers).
- `negotiation`: moderation in directed mode (multi-round buyer/seller).
- `debate`: moderation with two adversarial participants.
- `devteam`: team. A team lead decomposes a project; developer agents claim tasks from a shared list and message peers.

## <a href="about:blank#_workflow_supervised_alternative"></a> Workflow-supervised alternative

For request-based [Agents](../agents.html) coordinated through a [Workflow](../workflows.html) supervisor, see [Orchestrating multiple agents](../agents/orchestrating.html). The supervisor pattern keeps coordination logic outside the model in deterministic workflow steps, which suits cases where you want explicit step ordering, retry policies per step, and external compensation.

Choose the workflow approach when you want explicit, deterministic ordering. Choose Autonomous Agent capabilities when you want the model to decide what happens next based on the work in flight.

## <a href="about:blank#_see_also"></a> See Also

- [Autonomous Agents](../autonomous-agents.html)
- [Coordination patterns](../autonomous-agents/coordination.html)
- [Coordination capabilities](../autonomous-agents/capabilities.html)
- [Orchestrating multiple agents (workflow-based)](../agents/orchestrating.html)
- [Inter-agent communications](../../concepts/inter-agent-comms.html)

<!-- <footer> -->
<!-- <nav> -->
[Autonomous agents](autonomous-agents.html) [Memory & state](memory-and-state.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->