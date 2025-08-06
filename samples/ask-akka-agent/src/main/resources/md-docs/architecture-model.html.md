<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Service structure and layers](architecture-model.html)

<!-- </nav> -->

# Service structure and layers

Akka allows developers to focus on domain modeling and Application Programming Interfaces (APIs) without needing to manage low-level persistence or concurrency concerns. The organization of an Akka service into clear layers makes this possible.

## <a href="about:blank#_layered_structure"></a> Layered structure

Akka services follow a layered model where each layer has a distinct role. The layers are arranged concentrically, like an onion. The innermost layer holds business logic. Outer layers coordinate and expose that logic to the outside world.

![Layered Structure of a Service](_images/docs-onion_architecture-v1.min.svg)
Each layer should live in its own package:

- `domain`: for the domain model
- `application`: for Akka components
- `api`: for external-facing endpoints
Try not to expose domain types directly to the outside world. The API layer should not call the domain layer. Inner layers should not depend on or be aware of outer layers.

## <a href="about:blank#_layer_overview"></a> Layer overview

### <a href="about:blank#_domain"></a> Domain

This layer contains business rules and domain concepts. It does not depend on Akka or other runtime concerns. These are plain Java classes, often using `record` to reduce boilerplate. Examples include logic to enforce limits, compute totals, or apply rules.

You can write unit tests for this layer without needing to start Akka or the runtime. The domain package remains isolated, focused, and easy to change.

### <a href="about:blank#_application"></a> Application

This layer connects the domain model to the Akka runtime. It contains the components that handle persistence, coordination, and external interaction. These components follow event-driven patterns and manage state in a way that supports consistency and responsiveness.

Most classes in this layer are based on Akka-provided building blocks. The domain logic remains in the inner layer. This layer makes it operational.

### <a href="about:blank#_api"></a> API

This layer connects your service to the outside world. It defines endpoints that expose application functionality over HTTP or gRPC. Requests are handled here and passed on to the application layer.

Endpoints use <a href="../java/component-and-service-calls.html#_component_client">`ComponentClient`</a> to call Akka components in the application layer. This maintains separation of concerns and ensures runtime boundaries are respected.

The API layer may also expose public event models over Kafka or other channels. External systems should interact with your service only through this layer.

Access control and request validation also belong here. For HTTP-specific guidance, see [Designing HTTP Endpoints](../java/http-endpoints.html).

## <a href="about:blank#_mapping_layers_to_project_structure"></a> Mapping layers to project structure

Each layer described above corresponds to a distinct package in the source tree. This structure helps reinforce separation of concerns and makes it easy to locate different types of logic in a project.

A typical Akka service might have a layout like the following:

```txt
src/
 └── main/
     └── java/acme/petclinic/
         ├── domain/        # Business logic
         ├── application/   # Akka components
         └── api/           # External endpoints
```

- The `domain` directory holds plain Java classes that represent business rules and models. These are free of Akka-specific concerns.
- The `application` directory contains the building blocks provided by Akka. This is where components such as entities, views, workflows, and consumers are defined.
- The `api` directory exposes functionality to the outside world. This includes HTTP or gRPC endpoints that delegate to components in the application layer.
By keeping these directories distinct, the codebase becomes easier to navigate and evolve over time. This layering also supports clear testing strategies and runtime isolation.

## <a href="about:blank#_akka_components"></a> Akka components

You use <a href="../reference/glossary.html#component">Akka *Components*</a> to build your application. These Components are crucial for ensuring responsiveness. Here is a brief overview of each. Except endpoints, Akka components will live in your `application` package.

Akka components are marked with a `@ComponentId` or `@HttpEndpoint` annotation to identify them to the runtime.

### <a href="about:blank#_agents"></a> Agents

![Agent](../_images/agent.png)
An *Agent* interacts with an AI model to perform a specific task. It is typically backed by a large language model (LLM). It maintains contextual history in a session memory, which may be shared between multiple agents that are collaborating on the same goal. It may provide function tools and call them as requested by the model.

### <a href="about:blank#_entities"></a> Entities

![Entities](../_images/entity.png)
*Entities* are the core components of Akka and provide persistence and state management. They map to your <a href="https://martinfowler.com/bliki/DDD_Aggregate.html">*domain aggregates*</a>. If you have a "Customer" domain aggregate, you almost certainly will have a `CustomerEntity` component to expose and manipulate it. This separation of concerns allows the domain object to remain purely business logic focused while the Entity handles runtime mechanics. Additionally, you may have other domain objects that are leafs of the domain aggregate. These do not need their own entity if they are just a leaf of the aggregate. An address is a good example.

There are two types of entities in Akka. Their difference lies in how they internally function and are persisted.

#### <a href="about:blank#_key_value_entities"></a> Key Value Entities

![Key Value Entities](../_images/key-value-entity.png)
*Key Value Entities* are, as the name implies, an object that is stored and retrieved based on a key - an identifier of some sort. The value is the entire state of the object. Every write to a Key Value Entity persists the entire state of the object. Key Value Entities are similar in some ways to database records. They write and effectively lock the whole row. They still use an underlying event-based architecture so other components can subscribe to the stream of their updates. For more information see [Key Value Entities](../java/key-value-entities.html).

#### <a href="about:blank#_event_sourced_entities"></a> Event Sourced Entities

![Event Sourced Entities](../_images/event-sourced-entity.png)
*Event Sourced Entities* persist events instead of state in the event [journal](../reference/glossary.html#journal). The current state of the entity is derived from these events. Readers can access the event journal independently of the active entity instance to create read models, known as <a href="../java/views.html">*Views*</a>, or to perform business actions based on the events via [Consumers](../java/consuming-producing.html). For more information, see [Event Sourced Entities](../java/event-sourced-entities.html).

### <a href="about:blank#_views"></a> Views

![Views](../_images/view.png)
*Views* provide a way to materialize read only state from multiple entities based on a query. You can create views from Key Value Entities, Event Sourced Entities, and by subscribing to a topic. For more information see [Views](../java/views.html).


### <a href="about:blank#_consumers"></a> Consumers

![Consumers](../_images/consumer.png)
*Consumers* listen for and process events or messages from various sources, such as Event Sourced Entities, Key Value Entities and external messaging systems. They can also produce messages to topics, facilitating communication and data flow between different services within an application. For more information see [Consuming and producing](../java/consuming-producing.html).

### <a href="about:blank#_workflows"></a> Workflows

![Workflows](../_images/workflow.png)
*Workflows* enable the developer to implement long-running, multi-step business processes while focusing exclusively on domain and business logic. Technical concerns such as delivery guarantees, scaling, error handling and recovery are managed by Akka. For more information see [Workflows](../java/workflows.html).

### <a href="about:blank#_timed_actions"></a> Timed actions

![Timed actions](../_images/timer.png)
*Timed Actions* allow for scheduling calls in the future. For example, to verify that some process have been completed or not. For more information see [Timed actions](../java/timed-actions.html).


### <a href="about:blank#_endpoints"></a> Endpoints

![Endpoints](../_images/endpoint.png)
*Endpoints* are defined points of interaction for services that allow external clients to communicate via HTTP or gRPC. They facilitate the integration and communication between the other types of internal Akka components. Unlike other Akka components, endpoints will live in your `api` package. For more information see [HTTP Endpoints](../java/http-endpoints.html) and [gRPC Endpoints](../java/grpc-endpoints.html).

## <a href="about:blank#_akka_services"></a> Akka Services

![Services](../_images/service.png)
A *Project* may contain multiple *Services*. Projects can be deployed to one or more regions to achieve geographic resilience. For details, see [Multi-region operations](multi-region.html).

## <a href="about:blank#_next_steps"></a> Next steps

Once familiar with the layered structure, continue with:

- [Akka Deployment Model](deployment-model.html)
- [Development process](development-process.html)
- [State model](state-model.html)
- [Development best practices](../java/dev-best-practices.html)
You may also begin development right away using the [Akka SDK](../java/index.html).

<!-- <footer> -->
<!-- <nav> -->
[Distributed systems principles](distributed-systems.html) [Deployment model](deployment-model.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->