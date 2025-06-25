<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Concepts](concepts.html)

<!-- </nav> -->

# Concepts

Akka is a framework, runtime, and memory store for autonomous, adaptive agentic systems. Akka is delivered as an SDK and platform that can execute on any infrastructure, anywhere.

![Akka Agentic Platform](_images/akka-agentic-platform.png)


Developers create services built with Akka components that - when deployed - become agentic systems. Services can be tested locally or within a Continuous Integration/Continuous Delivery (CI/CD) practice using a Testkit that is available with each Akka component. Your services are compiled into a binary that includes the Akka Runtime which enables your services to self-cluster for scale and resilience. Akka clusters are able to execute on any infrastructure whether bare metal, Kubernetes, Docker or edge. You can optionally deploy your services into an Akka-managed cloud environment which automates most Day 2 concerns. We support Serverless, Bring Your Own Cloud (BYOC), and Self-Hosted cloud-hosted environments.

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

## <a href="about:blank#_composability"></a> Composability

The services you build with Akka components are composable, which can be combined to design agentic, transactional, analytics, edge, and digital twin systems. You can create services with one component or many. Let Akka unlock your distributed systems artistry!

![Akka Agentic Platform](_images/component-composition.png)


## <a href="about:blank#_akkas_design_goals"></a> Akka’s design goals

Akka’s design principles are influenced by decades of distributed systems research.

| Research | Publications |
| --- | --- |
| Patterns | [Principles and Patterns for Distributed Application Architecture](https://www.oreilly.com/library/view/principles-and-patterns/9781098181260/) |
| Principles | [The Reactive Principles](https://www.reactiveprinciples.org/) |
| Approach | [The Reactive Manifesto](https://www.reactivemanifesto.org/) |
The Akka Agentic Platform contains an SDK for development, the Akka runtime for scalable and resilient execution, and multiple operating modes. The platform, from development to production, has its own design goals.

| Property | Our Design Philosophy |
| --- | --- |
| Simple | Development approachable by anyone with (and eventually without) coding skills. |
| Adaptable | Runtime that adapts to environment or system changes by embracing failure and uncertainty. |
| Elastic | Scale processing and data (i.e. memory) to any level by distributing compute and state across Akka nodes. |
| Resilient | Recover from any failure, whether hardware, network, or hallucination. |
| Interoperable | Across all Akka components, any 3rd party system, protocol, broker, or Application Programming Interface (API). |
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

## <a href="about:blank#_akka_components"></a> Akka components

Akka provides a basket of interoperable components that can be used to design and implement any potential autonomous agentic AI (or distributed) system.

| Component | Description |
| --- | --- |
| Agents | Performs one focused AI task using a selected model and prompt. Can hold context with session memory (stateful) or run stateless with no retained history. |
| Workflows | Durable execution with support for sequential, parallel, looping, retry, and failure logic. |
| HTTP Endpoints | Exposes APIs over Hypertext Transfer Protocol (HTTP). Processes input, triggers components, shapes responses, and returns results. |
| gRPC Endpoints | Exposes APIs over gRPC Remote Procedure Calls (gRPC). Uses Protobuf contracts to ensure compatibility. Handles input, triggers logic, and returns results. |
| MCP Endpoints | Exposes tools, resources, and prompts over Model Context Protocol (MCP) protocol. Enables agents to invoke logic, retrieve data, and establish context. |
| Entities (memory) | Long term durable memory accessible by multiple agents, users, APIs, or 3rd party systems. |
| Views | Indexes and queries entity data across IDs or attributes. Built from entity state or events. Enables efficient lookups, filtering, and real-time updates. |
| Consumers (streaming) | Subscribes to and processes event streams or messages from entities, workflows, or external systems. |
| Timers | Schedules future calls with at-least-once delivery. Useful for deferred actions, retries, and timeouts. |

## <a href="about:blank#_component_interoperability"></a> Component interoperability

Akka components are able to interoperate with one another regardless of where the component may be executing. While you create systems that define the relationships between each of the components in code, at runtime instances of each component can run on different nodes. The Akka runtime provides location transparency, automating the routing of a call from one component to another even if they reside in different locations on different networks.

There are two ways to achieve interoperability between Akka components and the outside world.

| Client Type | Description |
| --- | --- |
| [ComponentClient](../java/component-and-service-calls.html) | One component can directly invoke another component. Akka treats these direct invocations as non-blocking, asynchronous messages, meaning that the invoking component immediately returns after making the invocation and can invoke any component regardless of its location.

For example, a Workflow component handling a long-running business transaction can invoke an Agent component to perform a task, and the Workflow can continue processing without waiting for the Agent to complete its work. |
| Events | Components can emit events that can be subscribed to by other components. For example, an entity can emit an event when its state is updated. Other components can subscribe to this event, similar to how clients subscribe to Kafka topics. Events are propagated transparently between components through reliable, brokerless messaging.

Components can also subscribe to events that are incoming from external sources, which can be 3rd party brokers, APIs, or real-time streams of data. |
Akka is designed so that components remain loosely coupled and can communicate with each other regardless of where they run. Inter-component communication runs on virtual threads managed by the Akka runtime. These threads are lightweight and isolated in a way that ensures system resources remain available for other tasks. This allows components to interact without interfering with each other or affecting overall responsiveness. This basic behavior is one reason why Akka services, whether stateful or stateless, can scale to 10M transaction per second (TPS).

| Example Interoperability | Description |
| --- | --- |
| Endpoint → Workflow → Agent
Endpoint → Entity → View | A user triggers an HTTP request to start a long-running Workflow. The Workflow manages file processing and passes relevant data or control to an Agent, which will later use it for answering questions. Meanwhile, another Endpoint saves user interaction history into an Entity. A View component then reads from that Entity to reconstruct the user’s conversation history for display or retrieval. |
| Endpoint → Agent → Entity → View
Endpoint → Workflow → Entity | A user sends a query to an Endpoint. An Agent processes the query and stores the interaction in an Entity. A View then reads this data to reconstruct the conversation history for that user. Meanwhile, an Endpoint starts a Workflow. The Workflow processes input data and stores results into an Entity. |
| Stream → Consumer → Entity
Agent → Endpoint → Entity | A data stream is received by a Consumer, which writes the structured data to an Entity for long-term storage and future retrieval. Meanwhile, an Agent invokes some logic exposed by an Endpoint, and persists the result in an Entity. |

## <a href="about:blank#_agentic_runtimes"></a> Agentic runtimes

Autonomous AI systems require three types of runtimes:

| Runtime | Description |
| --- | --- |
| Durable Execution | Long-lived, where the call-stack is persisted after every invocation to enable recovery and retries.

This is utilized when you implement the [Workflow](../java/workflows.html) component. |
| Transactional | Short-lived, high volume, concurrent execution.

This is utilized when you implement [Endpoint](grpc-vs-http-endpoints.html), [View](../java/views.html), [Entity](state-model.html) and [Timer](../java/timed-actions.html) components. |
| Streaming | Continuous, never-ending processes that handle streams of data.

This is utilized when you implement the [Consumer](../java/consuming-producing.html) component or [SSE / gRPC streaming extension of an endpoint](grpc-vs-http-endpoints.html). |
Akka provides support for all three runtimes within the same SDK. The runtime behavior is automatic within your service based upon the components that you use during development. All of these runtimes leverage an actor-based core, which is a concurrency model with strong isolation and asynchronous message passing between actors. When running a service that executes multiple runtimes, Akka maximizes efficiency of the underlying compute by executing actors for different runtimes concurrently, enabling node resource utilization up to 95%.

## <a href="about:blank#_shared_distributed_state_memory"></a> Shared, distributed state (memory)

There are a variety of shared data (memory) use cases within an agentic system.

| Use Case | Provided by | Description |
| --- | --- | --- |
| Short-term | Agent component | Also called “episodic” and “traced” memory, this memory is an auditable record of each input and output that occurs between an agent and its client throughout a single “user” session. Agent clients may or may not be human.

Akka also captures the input and output of every interaction between an agent and an LLM in a single enrichment loop, sometimes called “traced” memory. A single invocation of an agent from a client may cause that agent to invoke an LLM, function tools, or MCP tools many times. Akka’s short-term memory captures all of these interactions in an event log.

Short-term memory is also automatically included when you create [an agent](../java/agents.html). Short-term memory can be compressed, optimized, replicated, and audited. |
| Long-term | Entity component | Also called “shared” and “external” memory, this memory is an auditable record of state that is available to multiple agents, sessions, users, or Akka components.

Use long-term memory to capture the history (often summarized or aggregated) of interactions for a single user across many sessions.

Shared state is represented through an [Entity](state-model.html) component. Entities are event-sourced, making all of their changes published through an event stream and accessible by [Agents](../java/agents.html), [Endpoints](grpc-vs-http-endpoints.html), [Workflows](../java/workflows.html) or [Views](../java/views.html). |
Akka treats all stateful memory as event-sourced objects. Event sourcing is a technique to capture sequential state changes. Akka’s persistence engine transparently persists each event into a durable store. Since all state is represented as an event, Akka’s event engine enables transparent import, export, broadcast, subscription, replication, and replay of events. These behaviors enable Akka to offer a resilience guarantee and multi-region replication, which enables real-time failover with a Recovery Time Objective (RTO) of <200ms.

All events are stored in an event journal which can be inspected, analyzed, and replayed where appropriate.

Akka’s runtime enables scaling memory across large numbers of nodes that can handle terabytes of data. At runtime, you create 1..n instances of your stateful services. The Akka runtime ensures that there is only a single copy of your data within any particular instance. Your service’s data is sharded across the various instances based upon the amount of RAM space available. Data that cannot fit within RAM is durably available on disk, and can be activated to memory when needed. The Akka runtime automatically routes requests for data to the node that has the data instance requested. For example, if a user “John” were interacting with an agent, “John’s” conversational history would have a unique identifier and exist within one of the instances that is executing the agent service.

As an operator adds or removes physical nodes to the Akka runtime cluster, Akka will automatically rebalance all the stateful data to take advantage of the additional RAM. The clients or users that are interacting with the agent do not need to be aware of the rebalancing as Akka automatically routes each request to the instance with the correct data.

![Sharded and Rebalanced Data](_images/shard-rebalance-data.png)


## <a href="about:blank#_service_packaging"></a> Service packaging

The services you build with Akka components are composable, which can be combined to design agentic, transactional, analytics, edge, and digital twin systems. You can create services with one component or many.

Your services are packed into a single binary. You create instances of Akka that you can operate on any infrastructure: Platform as a Service (PaaS), Kubernetes, Docker Compose, virtual machines (VMs), bare metal, or edge.

Akka services self-cluster without you needing to install a service mesh. Akka clustering provides elasticity and resilience to your agentic services. In addition to data sharding, data rebalancing, and traffic routing, Akka clustering has built-in support for addressing split brain networking disruptions.

Optionally, you can deploy your agentic services into Akka Cloud, which provides a global control plane, multi-tenancy, multi-region operations (for compliance data pinning, failover, and disaster recovery), auto-elasticity based upon traffic load, and persistence management (memory auto-scaling).

![Akka Packaging](_images/packed-services.png)


<!-- <footer> -->
<!-- <nav> -->
[Understanding](index.html) [A foundation of fundamental distributed systems principles and patterns](distributed-systems.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->