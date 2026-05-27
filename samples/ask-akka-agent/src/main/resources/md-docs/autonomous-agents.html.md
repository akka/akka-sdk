<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Components](components/index.html)
- [Autonomous Agents](autonomous-agents.html)

<!-- </nav> -->

# Autonomous Agents

## <a href="about:blank#_overview"></a> Overview

![Autonomous Agent](../_images/agent.png)
An Autonomous Agent is a model-driven component that runs as a durable process. It works on typed tasks, each with its own identity, instructions, and result schema. The developer declares the agent’s description (via `@Component`), the task types it accepts, the tools it can use, and any coordination capabilities. The runtime drives the model through a decision loop until each task is complete. Agent and task state is persisted along the way, so work survives crashes and restarts.

A task ends when the model decides its work on it is done. Success means the model produces a result that conforms to the task’s declared result schema. Failure means the model reports it cannot make progress, and the runtime records a failure reason. As a safety net, an iteration limit terminates a task that reaches neither outcome. The agent’s description, together with the task’s result schema and any optional definition-level instructions, shape what "done" means for each task type.

A task exists independently of any agent. It can be created up front with dependencies, queried by external clients while running, or handed between agents. Its typed result outlives the agent that produced it. The agent itself is stateless from the developer’s perspective, while task and agent state are recovered automatically after crashes or restarts.

Models perform best with focused context. A single agent loaded with too many concerns, competing objectives, and unrelated information loses clarity. Autonomous Agents are designed around this constraint: each agent has its own description, its own accepted task types, and its own iteration loop, so context stays scoped to what one agent needs. Coordination then composes focused agents rather than overloading one.

Coordination is part of the component model. An agent can declare that it delegates subtasks to specialist workers, hands off work to peers, leads a team that shares a task list, or moderates a turn-taking conversation. The runtime exposes these patterns to the model as tools, so the agent invokes them like any other tool. Multi-agent systems can be assembled from focused, single-purpose agents without writing orchestration code.

### <a href="about:blank#_when_to_use_an_autonomous_agent"></a> When to use an Autonomous Agent

Use an `AutonomousAgent` when:

- The work is investigative or open-ended: the model decides what to consult, ask, or do next based on what previous steps revealed, rather than following a predefined sequence of steps.
- You need coordination between multiple agents (delegation, handoff, teams, moderation) without writing the orchestration yourself as workflow steps.
- You need durable multi-step execution that survives crashes and restarts.
- The work produces typed task results that have their own lifecycle: they can outlive a single request, be queried later, or move between agents.
- You want each agent to focus on a narrow context, with isolation between agents and shared context only where it helps.
Use a request-based [Agent](agents.html) when:

- The interaction is request-response, possibly over multiple turns in a session.
- You need fine-grained control over the prompt and effect chain for each call.
- The orchestration is a fixed sequence of explicit steps, handled by a [Workflow](workflows.html). Each step is at most one model round-trip, and the model does not decide what step to take next.
Each Autonomous Agent instance carries durable orchestration state, a task queue, and lifecycle bookkeeping. A standalone request-based Agent is the lighter choice for simple request-response interactions. Once a request-based Agent is combined with a Workflow for orchestration, the two are equally heavy and the choice rests on the criteria above, not on weight.

When a coordinating `AutonomousAgent` delegates to other agents, both alternatives are valid. A request-based `Agent` invoked through the coordinator’s delegation capability is the lighter choice when that agent takes a question and produces a response in a single model call. Promote it to its own `AutonomousAgent` when it benefits from its own iteration loop, parallel workers, handoff, or a separately observable task lifecycle.

### <a href="about:blank#_comparison_with_request_based_agents"></a> Comparison with request-based Agents

A [request-based Agent](agents.html) handles a single request and returns a single response. It has session memory for context and can call tools within that one request. To run multiple steps or anything that outlives a single request, you compose it with a [Workflow](workflows.html).

An Autonomous Agent runs the multi-step loop itself. It owns the iteration and the typed result, and drives the coordination with peer agents according to the declared capabilities. There is no command handler. The agent is described declaratively by its `definition()` and started through the `ComponentClient`.

The two share much of the underlying machinery. Model provider configuration, function tools, MCP integration, and guardrails all work the same way as for a request-based [Agent](agents.html). The comparison below focuses on the execution model, not on what either component can do.

|  | Agent | Autonomous Agent |
| --- | --- | --- |
| Execution model | Request-response | Durable process loop |
| Model interaction | Single round-trip (with tool calls) | Multiple iterations until task complete |
| Orchestration | External (Workflow) | Built-in (model-driven) |
| Unit of work | Command handler return value | Task entity with typed result |
| Multi-agent | Via Workflow steps | Via coordination capabilities |
| Persistent state | Session memory for conversation context | Durable agent process and persistent task entities |
| Streaming | Token stream | Notification stream |

### <a href="about:blank#_how_autonomous_agents_relate_to_other_components"></a> How Autonomous Agents relate to other components

- **Entities** hold durable business state that an Autonomous Agent can expose to the model as function tools (read or write).
- **Views** are read models that an agent can query through function tools to bring relevant data into its context.
- **Endpoints** expose Autonomous Agents over HTTP or gRPC, both to start tasks and retrieve results, and to stream notification events to the client over Server-Sent Events or WebSocket.
- **Workflows** are the alternative for deterministic step ordering. Prefer a Workflow when the *sequence of steps* is fixed and the model is consulted at most once inside each step. Prefer an Autonomous Agent when the *sequence itself* is a model judgment: the agent decides what to consult, which specialist to ask, or whether to iterate, based on what previous steps returned.

## <a href="about:blank#_identify_the_autonomous_agent"></a> Identify the autonomous agent

Like all components, an Autonomous Agent needs a **component id**: a stable identifier for the component class, supplied via the `@Component` annotation.

Unlike a request-based Agent, which is bound to a session id, an Autonomous Agent uses an **instance id** supplied when the agent is started via `ComponentClient`. Each instance runs independently with its own task queue and execution loop. A common pattern is to use a `UUID` for the instance id.

## <a href="about:blank#_basic_structure"></a> Basic structure

An Autonomous Agent extends `AutonomousAgent` and implements a single `definition()` method that returns the agent’s configuration.

[QuestionAnswerer.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/helloworld/application/QuestionAnswerer.java)
```java
@Component( // (2)
  id = "question-answerer",
  description = "Answers questions clearly and concisely, showing reasoning step by step"
)
public class QuestionAnswerer extends AutonomousAgent { // (1)

  @Override
  public AgentDefinition definition() { // (3)
    return define()
      .capability(TaskAcceptance.of(QuestionTasks.ANSWER).maxIterationsPerTask(3));
  }
}
```

| **1** | Create a class that extends `AutonomousAgent`. |
| **2** | Annotate the class with `@Component` and pass a unique identifier for this agent type. |
| **3** | Implement the `definition()` method to define the agent’s behavior. |
There are no command handlers. The agent is a process: it runs, works on assigned tasks, and stops.

The accepted task types are declared as constants and reference a typed result:

[QuestionTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/helloworld/application/QuestionTasks.java)
```java
public class QuestionTasks {

  public static final Task<Answer> ANSWER = Task
    .name("Answer")
    .description("Answer a question")
    .resultConformsTo(Answer.class);
}
```
The result type is a Java record:

[Answer.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/helloworld/application/Answer.java)
```java
/** Typed result for question answering tasks. */
public record Answer(String answer, int confidence) {}
```
To run a task, ask the `ComponentClient` to spin up an instance and run a single task:

[QuestionEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/helloworld/api/QuestionEndpoint.java)
```java
var taskId = componentClient
  .forAutonomousAgent(QuestionAnswerer.class, agentInstanceId)
  .runSingleTask(QuestionTasks.ANSWER.instructions(request.question()));
```
`runSingleTask` returns the task id immediately, without waiting for the task to complete. The task runs asynchronously and is itself the durable record of the work: its status and typed result are recovered after restarts and queryable at any time with `componentClient.forTask(taskId).get(QuestionTasks.ANSWER)`. There is no need to wrap the call in a Workflow for durability or to await the result. See [Client API](autonomous-agents/client.html) for details.

## <a href="about:blank#_topics"></a> Topics

[Defining an autonomous agent](autonomous-agents/defining.html) Building an `AgentDefinition`: accepted task types, tools, MCP endpoints, guardrails, iteration limit, model selection, and optional instructions. Covers how the `@Component` description captures the agent’s purpose and expected outcome.

[Tasks](autonomous-agents/tasks.html) Defining task types, creating instances with instructions and attachments, the task lifecycle, querying typed results, declaring dependencies between tasks.

[Coordination patterns](autonomous-agents/coordination.html) The four design patterns for multi-agent systems (sequential, delegative, collaborative, emergent), and when each is appropriate.

[Coordination capabilities](autonomous-agents/capabilities.html) Implementing the patterns: `Delegation`, handoff, `TeamLeadership`, `Moderation`. How capabilities compose with each other.

[Client API](autonomous-agents/client.html) Starting and stopping agents, assigning tasks, pausing and resuming, querying agent state and task results.

[Notifications](autonomous-agents/notifications.html) The notification stream emitted by the runtime: lifecycle, task, handoff, delegation, team, conversation, messaging, and struggle events.

[Testing](autonomous-agents/testing.html) Mocking model responses with `TestModelProvider` and the `AutonomousAgentTools` factory methods for the built-in coordination tools.

## <a href="about:blank#_multi_region_replication"></a> Multi-region replication

Autonomous Agents are not replicated to other regions. The agent instances stay in the region where they were created and can’t be accessed directly from other regions. The tasks are replicated like [any other entity](event-sourced-entities.html#_replication). Multi-region replication of Autonomous Agent state might be added later.

## <a href="about:blank#_see_also"></a> See Also

- [Request-based Agents](agents.html)
- [Coordination patterns](autonomous-agents/coordination.html)
- [Coordination capabilities](autonomous-agents/capabilities.html)
- [AI Agents concepts](../concepts/ai-agents.html)
- [Autonomous agents samples](../getting-started/samples.html#autonomous_agents_playground)

<!-- <footer> -->
<!-- <nav> -->
[Testing](agents/testing.html) [Defining an autonomous agent](autonomous-agents/defining.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->