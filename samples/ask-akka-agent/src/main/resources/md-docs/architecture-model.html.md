<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Architecture model](architecture-model.html)

<!-- </nav> -->

# Architecture model

Akka simplifies application development by allowing developers to focus on their domain models and APIs without worrying about how the data is stored. The architectural model is the key to making this work.

## <a href="about:blank#_architecture"></a> Architecture

Akka applications are built with an *Onion Architecture*, where services are organized in concentric layers. The core of the application is at the center, with additional layers surrounding it. Each layer has a specific role.

![Service Onion Architecture](_images/docs-onion_architecture-v1.min.svg)


### <a href="about:blank#_layers_in_the_architecture"></a> Layers in the architecture

The layers of the Akka programming architecture are outlined below.

#### <a href="about:blank#_domain"></a> Domain

At the center is the *Domain Model* which encapsulates your business logic. The domain should be mostly pure Java: that is not Akka components. In Akka projects and samples, this should be a package called `domain`.

#### <a href="about:blank#_application_layer"></a> Application

The middle layer is the *Application layer*. This layer uses the domain model and coordinates domain objects. This layer is where your Akka components will live. In this sense, an Akka Component acts as the glue between your domain model and the Akka runtime. This package should be called `application` and only contains Akka components like Agents, Entities and Views.

#### <a href="about:blank#_api"></a> API

The outermost layer is the *API layer*, which connects your application to the outside world. This is the layer where you will define endpoints that expose your application layer. This package should be called `api`.

## <a href="about:blank#_mechanics_of_each_layer"></a> Mechanics of each layer

Like most onion architectures, each layer only calls to the layers directly inside of it. This means an endpoint (API) will not directly call a domain object. It also means the outside world should never directly call domain objects or application components. If you want to call an Akka component like an entity or view, do so through an endpoint (`HttpEndpoint` or `GrpcEndpoint`). This separation is meant to contain the inner implementation and makes it easier to evolve applications over time.

These layers should be implemented as the three packages outlined above: `domain`, `application`, `api`. The mechanics of these layers are as follows.

### <a href="about:blank#_domain_2"></a> Domain

Domain objects are independent of the other layers. As these are pure Java objects they are the place for implementing business logic. Examples of business logic include checking credit limits for a loan or return policy enforcement. You should write unit tests for this code that tests the business logic.

Much of the inner loop of the developer experience will be spent here. Using Java’s `record` type declaration simplifies the amount of ceremony involved in creating domain objects that are understood and used by Akka. This keeps your domain model clean and free of any dependencies - including Akka.

### <a href="about:blank#_application"></a> Application

The application layer is where Akka becomes a first class participant as the glue between your domain code and the runtime. Most classes in this layer will extend an Akka component class (e.g., within the `akka.javasdk` package), making them event-based and actor-based behind the scenes, while abstracting those details from you.

These components include [Agents](../java/agents.html), [Event Sourced Entities](../java/event-sourced-entities.html), [Key Value Entities](../java/key-value-entities.html), [Views](../java/views.html), [Workflows](../java/workflows.html), [Timers](../java/timed-actions.html) and [Consumers](../java/consuming-producing.html). Each component type provides specific functionalities to handle state, events, and interactions efficiently.

### <a href="about:blank#_api_2"></a> API

The outermost layer is the API layer which is how the outside world interacts with your application or service. In this layer, you define endpoints that expose your application. Each HTTP endpoint is marked with an `@HttpEndpoint` annotation, which allows the runtime to build the appropriate endpoint URLs for uniquely identifying the components. Each public method on the endpoint that is annotated with method `@Get`, `@Post`, `@Put`, `@Patch` or `@Delete` serves those respective HTTP methods.

A strict API specification is required for gRPC endpoints enabling a protocol-first approach. Akka translates the gRPC description into classes and methods that provide the gRPC endpoints for implementation.

Having received requests, the `api` layer interacts with the `application` layer through the <a href="../java/component-and-service-calls.html#_component_client">`ComponentClient`</a> which makes calls in a type safe way. This is the layer boundary that keeps the isolation necessary to remain resilient that is core to an Akka application.

Additionally, this layer is the place for a public event model that a service exposes, often via Kafka or other messaging capabilities. This allows the event driven nature of Akka to be easily integrated into the rest of your information space. In Akka you don’t reach into the database to get state, you use the event stream itself.

The API layer also uses other annotations to [control access](../java/access-control.html). For more information on endpoints see [Designing HTTP Endpoints](../java/http-endpoints.html).

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
A *Service* is the base deployment unit in Akka. It includes the layers and packages described above. *Services* are deployed to *Projects*. A project can contain multiple services, which can be deployed to one or more regions. For more about multi-region operations, see [Multi-region operations](multi-region.html).

## <a href="about:blank#_next_steps"></a> Next steps

Now that you understand the overall architecture of Akka you are ready to learn more about the [Akka Deployment Model](deployment-model.html).

The following topics may also be of interest.

- [Development process](development-process.html)
- [State model](state-model.html)
- [Developer best practices](../java/dev-best-practices.html)
Start building your own Akka Service using the [Akka SDK](../java/index.html).

<!-- <footer> -->
<!-- <nav> -->
[A foundation of fundamental distributed systems principles and patterns](distributed-systems.html) [Deployment model](deployment-model.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->