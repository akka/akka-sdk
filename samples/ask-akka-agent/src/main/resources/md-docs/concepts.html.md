<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Concepts](concepts.html)

<!-- </nav> -->

# Concepts

Akka is a framework, runtime, and memory store for autonomous, adaptive agentic systems. Akka is delivered as an SDK and platform that can execute on any infrastructure, anywhere.

![Akka Agentic Platform](_images/akka-agentic-platform.png)


Developers create services built with *Akka components* that - when deployed - become agentic systems. Services can be tested locally or within a Continuous Integration/Continuous Delivery (CI/CD) practice using a *Testkit* that is available with each Akka component. Your services are compiled into a binary that includes the *Akka Runtime* which enables your services to self-cluster for scale and resilience. *Akka clusters* are able to execute on any infrastructure whether bare metal, Kubernetes, Docker or edge. Optionally, add *Akka Automated Operations* to gain multi-region failover, auto-elasticity, persistence oversight, multi-tenant services, and certificate rotation. *Akka Automated Operations* has two deployment options: our serverless cloud or your virtual private cloud (VPC).

| Product | Where To Start |
| --- | --- |
| Akka Orchestration | Akka provides a durable execution engine which automatically captures state at every step, and in the event of failure, can pick up exactly where they left off. No lost progress, no orphaned processes, and no manual recovery required.

You implement orchestration by creating an Akka service with the [Workflow](../java/workflows.html) component. |
| Akka Agents | Akka provides a development framework and runtime for agents. Agents can be stateful (durable memory included) or stateless. Agents can be invoked by other Akka components or run autonomously. Agents can transact with embedded tools, MCP servers, or any 3rd party data source with 100s of Akka connectors.

You implement an agent by creating an Akka service with the [Agent](../java/agents.html) component.

You implement a tool in a regular Java class or embedded within the [Agent](../java/agents.html) component.

You implement an MCP server with the [MCP Endpoint](../java/mcp-endpoints.html) component.

You implement APIs that can front an agent with the [HTTP Endpoint](../java/http-endpoints.html) and [gRPC Endpoint](../java/grpc-endpoints.html) components. |
| Akka Memory | Akka provides an in-memory, durable store for stateful data. Stateful data can be scoped to a single agent, or made available system-wide. Stateful data is persisted in an embedded event store that tracks incremental state changes, which enables recovery of system state (resilience) to its last known modification. State is automatically sharded and rebalanced across Akka nodes running in a cluster to support elastic scaling to terabytes of memory. State can also be replicated across regions for failover and disaster recovery.

Short-term (traced and episodic) memory is included transparently within the [Agent](../java/agents.html) component.

You implement long-term memory with the [Event Sourced Entity](../java/event-sourced-entities.html) and [Key Value Entity](../java/key-value-entities.html) components.

You implement propagations of cross-system state with the [View](../java/views.html) component. Views implement the Command Query Responsibility Segregation (CQRS) pattern. |
| Akka Streaming | Akka provides a continuous stream processing engine which can synthesize, aggregate, and analyze windows of data without receiving a terminating event. Data streams can be sourced from other Akka services or a 3rd party messaging broker or coming in through an Akka Endpoint. Your services can either store intermediate processing results into *Akka Memory* or trigger commands to other Akka components that take action on the data.

You produce events to a message broker with the [Producer](../java/consuming-producing.html#_event_producer) annotation.

You create a continuous incoming stream of events with the [HTTP Endpoint](../java/http-endpoints.html) or the [gRPC Endpoint](../java/grpc-endpoints.html) components.

You create a stream processor to analyze and act against a stream of data with the [Consumer](../java/consuming-producing.html) component. |

## <a href="about:blank#_components"></a> Components

You build your application using <a href="../reference/glossary.html#component">Akka *Components*</a>. These offer structure and maintain responsiveness. All components except Endpoints are placed in your `application` package. Endpoints live in the `api` package. Use `@Component` or `@HttpEndpoint` to identify them to the runtime.

| Component | Description |
| --- | --- |
| Agents | - Performs one focused AI task using a selected model and prompt.
  - Maintains session memory for context.
  - Stateless agents also possible.
  - See [Agents](../java/agents.html). |
| Workflows | - Durable execution with support for sequential, parallel, retry, and failure logic.
  - Akka manages delivery, scaling, and recovery.
  - See [Workflows](../java/workflows.html). |
| HTTP Endpoints | - Exposes APIs over HTTP.
  - Accepts input, triggers logic, and returns output.
  - See [HTTP Endpoints](../java/http-endpoints.html). |
| gRPC Endpoints | - Exposes APIs over gRPC using Protobuf contracts.
  - Facilitates compatibility and structured communication.
  - See [gRPC Endpoints](../java/grpc-endpoints.html). |
| MCP Endpoints | - Exposes tools, resources and prompts over the MCP protocol.
  - Used by agents to invoke logic and establish context.
  - See [MCP Endpoints](../java/mcp-endpoints.html). |
| Event Sourced Entities | - Durable memory component.
  - Stores a sequence of events that represent state changes.
  - The current state is reconstructed by replaying these events.
  - Suitable for audit trails and event-driven logic.
  - See [Event Sourced Entities](../java/event-sourced-entities.html). |
| Key Value Entities | - Durable memory component.
  - Stores full state snapshots indexed by a key.
  - Each write replaces the entire object.
  - Simpler to reason about and similar to row-based records.
  - See [Key Value Entities](../java/key-value-entities.html). |
| Views | - Indexes and queries entity data across IDs or attributes.
  - Built from entity state, workflow state, or events.
  - Enables efficient lookups, filtering, and real-time updates.
  - See [Views](../java/views.html). |
| Consumers (streaming) | - Listens for and processes events or messages from entities, workflows, or external systems.
  - May emit output messages.
  - See [Consuming and producing](../java/consuming-producing.html). |
| Timers | - Schedules future actions with delivery guarantees.
  - Used for reminders, retries, or timeout logic.
  - See [Timed actions](../java/timed-actions.html). |

## <a href="about:blank#_composability"></a> Composability

The services you build with Akka components are composable, which can be combined to design agentic, transactional, analytics, edge, and digital twin systems. You can create services with one component or many. Let Akka unlock your distributed systems artistry!

![Akka Agentic Platform](_images/component-composition.png)


## <a href="about:blank#_delegation_with_effects"></a> Delegation with effects

In Akka, the behavior of your services is decoupled from the execution. This decoupling allows Akka to determine how a service is executed without being constrained by how your system’s behavior is defined. Delegation removes you from worrying about distributed systems, persistence, elasticity, or networking. With Akka’s hosted services, we use delegation to enable swapping out new, improved runtimes while your services are running without a recompilation or redeployment!

In Akka, you specify *what* the system should do, while the Akka runtime decides *how* it should be executed. For example, you define an agent by specifying the model it uses, its session memory, and the user prompt. This represents the *what*. The Akka runtime then determines the *how* by managing processes, virtual threads, persistence, and actor-based concurrency.

Your services define the *what* using `Effects`, which are Application Programming Interfaces (APIs) provided by each Akka component. When you write a component method, you return an `Effect<…​>` object that describes, in a declarative way, what you want Akka to do.

For example, when using Akka’s [Agent](../java/agents.html) component, you might return an `Effect` that tells the runtime to execute the agent with a system message, a user message, and then send the AI model’s response back to the requester:

```java
public Effect<String> query(String question) {
  return effects()
    .systemMessage("You are a helpful...")
    .userMessage(question)
    .thenReply();
}
```

## <a href="about:blank#_akkas_design_goals"></a> Akka’s design goals

Akka’s design principles are influenced by decades of distributed systems research.

| Research | Publications |
| --- | --- |
| Approach | [The Reactive Manifesto](https://www.reactivemanifesto.org/): Defines the four fundamental high-level traits of a well-architected distributed system - responsiveness, elasticity, resilience, and message-driven. |
| Principles | [The Reactive Principles](https://www.reactiveprinciples.org/): Distills the four traits into a set of foundational guiding principles for great distributed systems design. |
| Patterns | [O’Reilly Principles and Patterns for Distributed Application Architecture](https://www.oreilly.com/library/view/principles-and-patterns/9781098181260/): This guide outlines architectural patterns that are essential for building robust systems, including how to leverage event-sourcing, CQRS, message-driven communications, consistency boundaries, location transparency, stateful services with temporal guarantees, backpressure, flow control, and failure supervision. |
The Akka Agentic Platform contains an SDK for development, the Akka runtime for scalable and resilient execution, and multiple operating modes. The platform, from development to production, has its own design goals.

| Property | Our Design Philosophy |
| --- | --- |
| Simple | Development approachable by anyone with (and eventually without) coding skills. |
| Adaptable | Runtime that adapts to environment or system changes by embracing failure and uncertainty. |
| Elastic | Scale processing and data (i.e. memory) to any level by distributing compute and state across Akka nodes. |
| Resilient | Recover from any failure, whether hardware, network, or hallucination. |
| Interoperable | Across all Akka components, any 3rd party system, protocol, broker, or API. |
| Composable | Akka services and components can be combined to create systems of any complexity. |
| Production-ready | Akka services should never require code changes when moving into production. |

## <a href="about:blank#_anatomy_of_an_agentic_system"></a> Anatomy of an Agentic system

An agentic system is a distributed system that requires a variety of behaviors and infrastructure.

![Akka Agentic Platform](_images/agentic-ai-system-anatomy.png)


| Aspect | AI Role and Responsibility |
| --- | --- |
| Agents | Components that integrate with AI to perceive their environment, make decisions and take actions toward a specific goal

You implement agents in Akka with the [Agent](../java/agents.html) component. |
| Tools | Functionality, local or remote, that agents may call upon to perform tasks beyond their core logic.

You invoke tools in Akka through *embedded agent function calls* or by *invoking a remote MCP tool*. You can implement MCP servers with the [MCP Endpoints](../java/mcp-endpoints.html) component. |
| Endpoints | Externally accessible entry points through which agents are launched and controlled.

You implement Endpoints in Akka using either [HTTP](../java/http-endpoints.html), [gRPC](../java/grpc-endpoints.html) or [MCP](../java/mcp-endpoints.html) Endpoint components. |
| Goals | Clear objectives or outcomes that agents continuously work toward by making decisions and taking actions on their own.

You implement goals in Akka by implementing a multi-agent system *with a planner agent* using a [Workflow](../java/workflows.html) component to orchestrate the cross-agent interactions. |
| Guardians | Components that monitor, protect and evaluate the system against its goals and constraints.

You will soon be able to implements guardians in Akka with an [Agent evaluation workbench](../java/agents.html#_evaluating_ai_model_quality). |
| Adaptation | Continuous, real-time streams from users or the environment which can alter the context, memory or semantic knowledge used by an agentic system.

You implement adaptation in Akka by processing a stream of data from external sensors, either with the [Consumer](../java/consuming-producing.html) component or through streaming HTTP or gRPC interfaces. [Consumers](../java/consuming-producing.html) can modify an agent’s goals, memory, or guardians to affect the behavior of the system. |
| Orchestration | The ability to execute, persist and recover long-running tasks made possible through *durable execution*.

You implement orchestration in Akka with the [Workflow](../java/workflows.html) component. |
| Memory | Data that enables agents to reason over time, track context, make correct decisions and learn from experience.

You inherit agentic and episodic (short-term) durable memory automatically when you implement a stateful [Agent](../java/agents.html) component. You can get long-term, multi-agent memory by implementing [Event Sourced Entity](../java/event-sourced-entities.html) or [Key Value Entity](../java/key-value-entities.html) components. |
| Registry | A built-in directory that stores information about all agents so they can be discovered and called upon in multi-agent systems.

You use the registry provided by Akka by [annotating each agent](../java/agents.html#_creating_dynamic_plans), which allows Akka to automatically register and use them as needed. |

## <a href="about:blank#_properties_of_a_distributed_system"></a> Properties of a distributed system

A distributed system is any system that distributes logic or state. Distributed systems embody certain principles that when combined together create a system that achieves responsiveness. Distributed systems are capable of operating in any location: locally on your development machine, in the cloud, at the edge, embedded within a device, or a blend of all.

| Property | Definition |
| --- | --- |
| Elasticity | The system can automatically adjust its resources, scaling up or down to efficiently handle changes in workload. |
| Resilience | The system continues to function and recover quickly, even when parts of it fail, ensuring ongoing availability. |
| Agility | The system can easily adapt to new requirements or changes in its environment with minimal effort. |
| Responsiveness | Most importantly, the system consistently responds to users and events in a timely manner, maintaining a reliable experience. |

## <a href="about:blank#_agentic_runtimes"></a> Agentic runtimes

Autonomous AI systems require three types of runtimes:

| Runtime | Description |
| --- | --- |
| Durable Execution | Long-lived, where the call-stack is persisted after every invocation to enable recovery and retries.

This is utilized when you implement the [Workflow](../java/workflows.html) component. |
| Transactional | Short-lived, high volume, concurrent execution.

This is utilized when you implement [Endpoint](grpc-vs-http-endpoints.html), [View](../java/views.html), [Entity](state-model.html#_entity_state_models) and [Timer](../java/timed-actions.html) components. |
| Streaming | Continuous, never-ending processes that handle streams of data.

This is utilized when you implement the [Consumer](../java/consuming-producing.html) component or [SSE / gRPC streaming extension of an endpoint](grpc-vs-http-endpoints.html). |
Akka provides support for all three runtimes within the same SDK. The runtime behavior is automatic within your service based upon the components that you use during development. All of these runtimes leverage an actor-based core, which is a concurrency model with strong isolation and asynchronous message passing between actors. When running a service that executes multiple runtimes, Akka maximizes efficiency of the underlying compute by executing actors for different runtimes concurrently, enabling node resource utilization up to 95%.

## <a href="about:blank#_shared_distributed_state_memory"></a> Shared, distributed state (memory)

There are a variety of shared data (memory) use cases within an agentic system.

| Use Case | Provided by | Description |
| --- | --- | --- |
| Short-term | [Agent](../java/agents.html) component | Also called “episodic” and “traced” memory, this memory is an auditable record of each input and output that occurs between an agent and its client throughout a single “user” session. Agent clients may or may not be human.

Akka also captures the input and output of every interaction between an agent and an LLM in a single enrichment loop, sometimes called “traced” memory. A single invocation of an agent from a client may cause that agent to invoke an LLM, function tools, or MCP tools many times. Akka’s short-term memory captures all of these interactions in an event log.

Short-term memory is also automatically included when you create [an agent](../java/agents.html). Short-term memory can be compressed, optimized, replicated, and audited. |
| Long-term | [Entity](../java/event-sourced-entities.html) component | Also called “shared” and “external” memory, this memory is an auditable record of state that is available to multiple agents, sessions, users, or Akka components.

Use long-term memory to capture the history (often summarized or aggregated) of interactions for a single user across many sessions.

Shared state is represented through an [Entity](../java/event-sourced-entities.html) component. Entities are event-sourced, making all of their changes published through an event stream and accessible by [Agents](../java/agents.html), [Endpoints](grpc-vs-http-endpoints.html), [Workflows](../java/workflows.html) or [Views](../java/views.html). |
Akka treats all stateful memory as event-sourced objects. Event sourcing is a technique to capture sequential state changes. Akka’s persistence engine transparently persists each event into a durable store. Since all state is represented as an event, Akka’s event engine enables transparent import, export, broadcast, subscription, replication, and replay of events. These behaviors enable Akka to offer a resilience guarantee and multi-region replication, which enables real-time failover with a Recovery Time Objective (RTO) of <200ms.

All events are stored in an event journal which can be inspected, analyzed, and replayed where appropriate.

Akka’s runtime enables scaling memory across large numbers of nodes that can handle terabytes of data. At runtime, you create 1..n instances of your stateful services. The Akka runtime ensures that there is only a single copy of your data within any particular instance. Your service’s data is sharded across the various instances based upon the amount of RAM space available. Data that cannot fit within RAM is durably available on disk, and can be activated to memory when needed. The Akka runtime automatically routes requests for data to the node that has the data instance requested. For example, if a user “John” were interacting with an agent, “John’s” conversational history would have a unique identifier and exist within one of the instances that is executing the agent service.

As an operator adds or removes physical nodes to the Akka runtime cluster, Akka will automatically rebalance all the stateful data to take advantage of the additional RAM. The clients or users that are interacting with the agent do not need to be aware of the rebalancing as Akka automatically routes each request to the instance with the correct data.

![Sharded and Rebalanced Data](_images/shard-rebalance-data.png)


## <a href="about:blank#_component_interoperability"></a> Component interoperability

Systems often rely on distributed components that need to work together. In Akka, components such as Agents, Workflows and Entities interact in ways that support flexibility, scale, and resilience. The aim is to help you build systems that are easy to reason about and maintain, even when deployed across different environments.

In Akka, component relationships are defined in code. At runtime, the platform handles the details. Messages are routed automatically, without requiring you to manage network paths or address resolution. This is known as *location transparency*. It means components can communicate without knowing where the other components are running.

Akka supports two primary ways for components to interact, either with each other or with the outside world.

| Client Type | Description |
| --- | --- |
| [ComponentClient](../java/component-and-service-calls.html) | One component can invoke another using a direct call. The Akka runtime handles this communication in a non-blocking way using lightweight virtual threads. Although a call may wait for a reply before continuing, the code remains simple and synchronous. There is no need to use futures, callbacks or other asynchronous programming techniques.

A common example is a Workflow that invokes an Agent to perform a specific task, then waits for the Agent to finish. The syntax is simple and resembles a regular method call. |
| Events | Components can emit events to signal that something has occurred. Other components may subscribe to these events. This model resembles traditional publish-subscribe systems but does not require external brokers.

For example, when an Entity updates its state, it will emit an event. A View can subscribe to that event to stay in sync. Events can also come from external sources, such as APIs or streaming services. |
Akka encourages building systems with loosely coupled components. Communication between them is handled in a way that avoids contention and keeps the system responsive, even under heavy load. Blocking operations are managed in a controlled and efficient way, allowing developers to focus on business logic without worrying about low-level concurrency concerns.

This approach supports systems that need to handle large volumes of traffic. Some production environments have processed up to 10 million transactions per second.

The examples below show common patterns for how components interact in an Akka system.

| Example Interoperability | Description |
| --- | --- |
| Endpoint → Workflow → Agent
Endpoint → Entity → View | An HTTP request starts a Workflow to process a file. The Workflow invokes an Agent that will later use the file’s content to answer questions. Another Endpoint records user interaction history into an Entity. A View reads from that Entity to reconstruct the conversation. |
| Endpoint → Agent → Entity → View
Endpoint → Workflow → Entity | A user sends a query to an Endpoint. An Agent handles the query and stores the result in an Entity. A View builds the conversation history from that data. Separately, another Endpoint starts a Workflow, which also stores its results in an Entity. |
| Stream → Consumer → Entity
Agent → Endpoint → Entity | A stream of data is processed by a Consumer, which writes to an Entity for long-term use. At the same time, an Agent invokes logic through an Endpoint and stores the result in an Entity. |
Akka provides a way to connect components that is simple to use and reliable in production. By relying on message passing, virtual threads, and transparent routing, the platform helps you focus on what the system should do, rather than how its parts should reach each other.

## <a href="about:blank#_background_execution"></a> Background execution

In Akka, [effects](../reference/glossary.html#effect) are processed in the background. When you call a component or a service within Akka, the default mode is synchronous, but you can opt-in to asynchronous for more control. You do not need to implement any asynchronous libraries, queues, promises, callbacks, await/async, futures, or event loops for Akka to behave this way.

Akka handles background processing using actors. Actors offer a lightweight model for concurrency, relying on asynchronous messaging rather than locks. This helps avoid shared mutable state and sidesteps many of the typical issues seen in multi-threaded programming, such as blocking, deadlocks, and race conditions.

Because the Actor runtime manages concurrency, you can write simple, synchronous code within your Akka components. There is little need to worry about performance or resource contention.

This "share nothing" approach also makes it easier to reason about concurrent systems. It helps reduce the chance of deadlocks and supports the creation of systems that are more stable and easier to scale. Akka also includes built-in supervision and fault tolerance, so if an actor fails, the issue is contained and resolved locally. This avoids broader system failure and reduces the need for complex manual error handling, which is often required elsewhere.

When you use the [component client](../reference/glossary.html#component_client) to call another component, your code remains synchronous and returns regular objects. If those calls involve effects, the Akka runtime takes care of them in the background. Depending on where the component is located, the runtime may even do so across different locations.

## <a href="about:blank#_synchronous_vs_asynchronous_component_invocation"></a> Synchronous vs asynchronous component invocation

You decide how the [component client](../reference/glossary.html#component_client) invokes the component, and the Akka runtime handles the request in the background. The following table summarizes the key differences between synchronous and asynchronous component invocation.

|  | Synchronous | Asynchronous |
| --- | --- | --- |
| When the component method returns | After the method finishes | Immediately |
| Client behavior | Waits for the result before continuing | Continues immediately, must handle the result later |
| Return type | Whatever the component method returns directly | A `CompletionStage<T>` representing the result at a later time |
| Component execution | Always runs in the background | Always runs in the background |
| Common use case | Calling a method and using the result in the next line of code | Starting multiple async tasks or implementing background, always-on processes (Ambient AI) |
| Ideal for | Simple flows where the result is needed immediately | Parallel task execution, deferred response handling, or long-running background logic |
For implementation guidance on invoking components, see [Component and service calls](../java/component-and-service-calls.html).

## <a href="about:blank#_service_packaging"></a> Service packaging

The services you build with Akka components are composable, which can be combined to design agentic, transactional, analytics, edge, and digital twin systems. You can create services with one component or many.

Your services are packed into a single binary. You create instances of Akka that you can operate on any infrastructure: Platform as a Service (PaaS), Kubernetes, Docker Compose, virtual machines (VMs), bare metal, or edge.

Akka services self-cluster without you needing to install a service mesh. Akka clustering provides elasticity and resilience to your agentic services. In addition to data sharding, data rebalancing, and traffic routing, Akka clustering has built-in support for addressing split brain networking disruptions.

Optionally, you can deploy your agentic services into [Akka Automated Operations](../operations/akka-platform.html), which provides a global control plane, multi-tenancy, multi-region operations (for compliance data pinning, failover, and disaster recovery), auto-elasticity based upon traffic load, and persistence management (memory auto-scaling).

![Akka Packaging](_images/packed-services.png)


<!-- <footer> -->
<!-- <nav> -->
[Understanding](index.html) [Distributed systems](distributed-systems.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->