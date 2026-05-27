<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Inter-agent communications](inter-agent-comms.html)

<!-- </nav> -->

# Inter-agent communications

Agents can collaborate in many ways, from orchestration, to direct communication, to other patterns like swarms and teams. Many of these communication patterns make for great demos, but don’t scale well or run safely and securely in production environments.

The patterns flagged below as "avoid" are about ad-hoc protocol-level wiring between agents, where there is no platform-level durability, retry, or audit. Coordination that goes through Akka’s component model, whether a [Workflow](../sdk/workflows.html) supervisor or an [Autonomous Agent](../sdk/autonomous-agents.html) coordination capability, is not in that category and is the recommended way to compose multi-agent systems.

Further, the emergence of [MCP](https://modelcontextprotocol.io/introduction), [A2A](https://a2aprotocol.ai/), and [ACP](https://agentcommunicationprotocol.dev/introduction/welcome) have made it (perhaps too) easy to enable direct communication between agentic services. This has led to many techniques for agent-to-agent communications: some extremely valuable and some deceptively dangerous. While Akka makes it simple to implement any of these techniques, it is worth exploring each of these patterns and anti-patterns in detail.

The following table provides a quick reference as to which communication paths are preferred patterns versus ones you should avoid. Additional details are provided thereafter.

| Paths | Description |
| --- | --- |
| Workflow → Agent(s)
Workflow → A2A/ACP → Agents
Workflow → Endpoint (API) | Workflows are engines of orchestration that can ensure the successful outcome of many invocations across agents that are local or remote. Workflows have built-in logic for handling retries and failure for long-running processes.

✅ **Adopt** |
| AutonomousAgent → Agent / AutonomousAgent
(via delegation, handoff, teams, or moderation) | Coordination is declared on the Autonomous Agent and driven by the runtime. The runtime persists task state, retries failed steps, enforces iteration limits, and emits notifications, so the durability, reliability, and audit guarantees are equivalent to a Workflow supervisor. Use this when the model should decide which agent runs next inside a single service.

✅ **Adopt** |
| Agent → Broker
Workflow → Broker
Entity → Broker | Leverage an event-driven architecture that completely disconnects the awareness of one service that is producing information from those that consume it. However, for agent-to-agent communications where agents are part of a single system working towards a common goal, broker-based architectures eliminate group-based goals, shared state, and system-wide guardians.
Event-driven architectures are promoted for their scale and autonomy, but create separation that makes it harder for autonomous systems to collaborate towards a common goal.

✅ **Adopt** |
| Broker → Consumer → Agent | Agents can be tasked to execute goals triggered by events that are sent through a broker. Provides independent intelligence and autonomy. Suffers from the same “unable to work towards a broader goal” problem.

✅ **Adopt** |
| Agent → Agent
Endpoint (API) → Agent | Avoid leveraging protocols such as HTTP, JSON-RPC, gRPC, A2A, and ACP from inside an agent’s own code path to reach another agent. This bypasses platform mediation and gives up durability, retries, and audit. If the model genuinely should decide which agent runs next, use an AutonomousAgent coordination capability (delegation, handoff, teams, moderation) instead, see the ✅ Adopt row above.

⚠️ **Avoid** |
| Agent → MCP Server → Agent | It is best to avoid using an agent’s ability to call tools or services as a way to reach other agents over a protocol such as MCP. This creates indirect connections that reduce clarity and make the system harder to reason about. (Note: this is distinct from the coordination tools the AutonomousAgent runtime exposes to the model for delegation, handoff, teams, or moderation, which are platform-mediated and durable.)

⚠️ **Avoid** |
| Endpoint (API) → Endpoint (API) | Avoid having an endpoint call functions directly on another endpoint. This can create side effects that aren’t captured by any of the durable event flows and can bypass the resiliency and scalability provided by Workflows.

⚠️ **Avoid** |

## <a href="about:blank#_why_avoid_certain_inter_agent_patterns"></a> Why avoid certain inter-agent patterns

Well-architected agents are built to do one thing: accomplish one overarching goal. Most agent frameworks, including Akka, make it simple to create multi-agent systems where one agent invokes another directly. These approaches often violate the “do one thing” rule.

Consider the example of a travel planning agent. This agent that makes travel recommendations might utilize other agents like an airline reservation agent, a weather agent, a traffic agent, and may optionally make use of half a dozen or more agents.

On the surface this seems like a good idea, and that’s what makes it so dangerous. If the top-level planning agent makes calls directly to the other agents, then that supervision structure is permanently fixed that way. The planning agent cannot be composed and used by other workflows.

The simple weather service can be used by any composition or arrangement of agents because it does one thing, and it does not call any other agents. If one agent relies on communicating with other specific agents, it has more consequences than just enforcing a rigid hierarchy.

Large language models (LLMs) are unpredictable and unreliable, as are network communication and practically everything else in enterprise and cloud software. Real-world scenarios need to deal with low-level failures at the protocol level and high level failures that might require retries, goal adjustments, context queries, and more. Putting this logic all inside an agent can make it near impossible to use, reuse, or compose in different flows.

Agentic flows need to deal with things like timeouts and network partition events, especially when talking to relatively slow, token-streaming LLMs.

The real solution to agent-to-agent communication may seem extreme: *never allow ad-hoc, protocol-level agent-to-agent communications*.

Agents need to do their one thing, and the work of composition, orchestration, and resilience should be left to a supervisor that runs on the Akka runtime: a Workflow when the orchestration steps are fixed in code (the application writes the steps, the Workflow component provides the durability and retry machinery), or an AutonomousAgent coordination capability when the model genuinely should decide which agent runs next (the framework itself drives the loop). Both approaches inherit the same composability, durability, and recovery guarantees from the runtime. The Workflow is the application’s mediator; the capability framework is the framework’s mediator. Either one is a sound answer to the question above, the choice rests on whether the sequence is fixed in code or decided by the model.

How do we actually communicate with other agents and do it the right way?

## <a href="about:blank#_workflow_orchestration"></a> Workflow orchestration

The structure, sequence, scale, and resilience of multi-agent collaborations should be done through the Akka [Workflow](https://doc.akka.io/sdk/workflows.html) component. See the [official documentation](https://doc.akka.io/sdk/workflows.html) for guides on general Workflow use as well as how to build agentic workflows with sequential, parallel, state-transition, and event-driven logic. Implement Workflows across long-running processes with an unknown stop time by incorporating retries and failure logic within the orchestration.

When you want the model to decide the next agent rather than encoding the sequence in workflow steps, an [Autonomous Agent](../sdk/autonomous-agents.html) with delegation, handoff, teams, or moderation is the framework-driven equivalent. See [Coordination capabilities](../sdk/autonomous-agents/capabilities.html).

## <a href="about:blank#_component_client"></a> Component client

There is an Akka component client for all Akka components, including Agents. While an Agent should not use this to talk to other Agents, Endpoints and Workflows will use this to trigger and orchestrate Agents.

The component client only works to communicate with components within the same service. Outside the service you’ll need to use service-to-service eventing or standard protocols like HTTP, gRPC, or even MCP.

## <a href="about:blank#_message_brokers"></a> Message brokers

Communication between Akka components and external entities can be done with message brokers. The Akka SDK currently supports Google Cloud PubSub and Kafka. We typically use message brokers like this to provide input and output integration with other services on other infrastructure that might not be running Akka.

Consumers and Workflows within the agent’s service can react to messages as well as publish them to external brokers.

## <a href="about:blank#_endpoint_client_intra_project"></a> Endpoint client (intra-project)

If you need to communicate with an endpoint that is defined *within* the same project, then Akka has a component client to make this easy. This saves you from having to write code that creates a client that understands the protocol of that endpoint, whether it’s HTTP, gRPC, or MCP.

## <a href="about:blank#_http_or_grpc_client_external"></a> HTTP or gRPC client (external)

If your orchestration workflow needs to communicate with external services that expose HTTP or gRPC APIs, then you can use Akka’s `HttpClientProvider` and `GrpcClientProvider` clients to communicate with those. As with all other communication methods, this shouldn’t be done from inside an agent, but from within consumers or orchestration workflows.

## <a href="about:blank#_mcp"></a> MCP

MCP, the Model Context Protocol, is a protocol that allows agents to use LLMs to convert intent described in a prompt into structured tool calls. These tools can be used to do anything from manipulate a file system to query customer-specific data from a private database.

AI assistants modify their system prompts so that they include a list of tool calls exposed by configured MCP servers. It’s very tempting to put calls to other agents inside these tool callbacks, but that too is dangerous.

There is no automatic failover or retry or timeout system for when an agent invokes a tool via MCP. This means any agent-to-agent communication done inside of an MCP tool is inherently unreliable and brittle. Be mindful about using MCP for agent-to-agent calls.

Typically the LLM will identify the MCP servers to use from prompt and context and will, if possible, gather the appropriate tool call parameters from that same prompt.

## <a href="about:blank#_a2a_acp_etc"></a> A2A, ACP, etc

While MCP is a “tool” protocol, there are other protocols designed specifically for direct communication between agents, such as A2A, ACP, and others. These protocols are misleading in that they seem to promote direct point-to-point communication between agents.

It might seem like trivial semantics, but when you see A2A and ACP, you should think of leveraging an Akka Workflow to make use of those protocols on behalf of the system, which is composed of two or more agents. It’s agent-to-agent communication through a responsible, reliable orchestrating partner defined as a `Workflow`.

<!-- <footer> -->
<!-- <nav> -->
[AI Agents](ai-agents.html) [AI orchestration patterns](ms-agent-patterns.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->