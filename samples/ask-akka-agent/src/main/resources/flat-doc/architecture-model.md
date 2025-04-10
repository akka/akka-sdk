# Architecture model

Akka simplifies application development by allowing developers to focus on their domain models and APIs without worrying about how the data is stored. The architectural model is the key to making this work.

## Architecture

Akka applications are built with an _Onion Architecture_, where services are organized in concentric layers. The core of the application is at the center, with additional layers surrounding it. Each layer has a specific role.

![Service Onion Architecture](docs-onion_architecture-v1.min.svg)

### Layers in the architecture

The layers of the Akka programming architecture are outlined below.

#### Domain

At the center is the _Domain Model_ which encapsulates your business logic. The domain should be mostly pure Java: that is not Akka components. In Akka projects and samples, this should be a package called `domain`.

#### Application

The middle layer is the _Application layer_. This layer uses the domain model and coordinates domain objects. This layer is where your Akka components will live. In this sense, an Akka Component acts as the glue between your domain model and the Akka runtime. This package should be called `application` and only contains Akka components like Entities and Views.

#### API

The outermost layer is the _API layer_, which connects your application to the outside world. This is the layer where you will define endpoints that expose your application layer. This package should be called `api`.

## Mechanics of each layer

Like most onion architectures, each layer only calls to the layers directly inside of it. This means an endpoint (API) will not directly call a domain object. It also means the outside world should never directly call domain objects or application components. If you want to call an Akka component like an entity or view, do so through an endpoint (`HttpEndpoint`). This separation is meant to contain the inner implementation and makes it easier to evolve applications over time.

These layers should be implemented as the three packages outlined above: `domain`, `application`, `api`. The mechanics of these layers are as follows.

### Domain

Domain objects are independent of the other layers. As these are pure Java objects they are the place for implementing business logic. Examples of business logic include checking credit limits for a loan or return policy enforcement. You should write unit tests for this code that tests the business logic. 

Much of the inner loop of the developer experience will be spent here. Using Java’s `record` type declaration simplifies the amount of ceremony involved in creating domain objects that are understood and used by Akka. This keeps your domain model clean and free of any dependencies - including Akka. 

### Application

The application layer is where Akka becomes a first class participant as the glue between your domain code and the runtime. Most classes in this layer will extend an Akka component class (e.g., within the `akka.javasdk` package), making them event-based and actor-based behind the scenes, while abstracting those details from you.

These components include [Event Sourced Entities](java:event-sourced-entities.adoc), [Key Value Entities](java:key-value-entities.adoc),  [Views](java:views.adoc), [Workflows](java:workflows.adoc), [Timers](java:timed-actions.adoc) and [Consumers](java:consuming-producing.adoc). Each component type provides specific functionalities to handle state, events, and interactions efficiently.

### API

The outermost layer is the API layer which is how the outside world interacts with your application or service. In this layer, you define endpoints that expose your application. Each endpoint is marked with an `@HttpEndpoint` annotation, which allows the runtime to build the appropriate endpoint URLs for uniquely identifying the components. Each public method on the endpoint that is annotated with method `@Get`, `@Post`, `@Put`, `@Patch` or `@Delete` serves those respective HTTP methods.

Having received requests, the `api` layer interacts with the `application` layer through the [`ComponentClient`](java:component-and-service-calls.adoc#_component_client) which makes calls in a type safe way. This is the layer boundary that keeps the isolation necessary to remain resilient that is core to an Akka application.

Additionally, this layer is the place for a public event model that a service exposes, often via Kafka or other messaging capabilities. This allows the event driven nature of Akka to be easily integrated into the rest of your information space. In Akka you don’t reach into the database to get state, you use the event stream itself.

The API layer also uses other annotations to [control access](java:access-control.adoc). For more information on endpoints see [Designing HTTP Endpoints](java:http-endpoints.adoc).

## Akka components

You use [Akka _Components_](reference:glossary.adoc#component) to build your application. These Components are crucial for ensuring responsiveness. Here is a brief overview of each. Except endpoints, Akka components will live in your `application` package.

The list of components are:

* [Event Sourced Entities](java:event-sourced-entities.adoc)
* [Key Value Entities](java:key-value-entities.adoc)
* [HTTP Endpoints](java:http-endpoints.adoc)
* [Views](java:views.adoc)
* [Workflows](java:workflows.adoc)
* [Timers](java:timed-actions.adoc)
* [Consumers](java:consuming-producing.adoc)

Akka components are marked with a `@ComponentId` or `@HttpEndpoint` annotation to identify them to the runtime.

### Entities

_Entities_ are the core components of Akka and provide persistence and state management. They map to your [_domain aggregates_, window="new"](https://martinfowler.com/bliki/DDD_Aggregate.html). If you have a "Customer" domain aggregate, you almost certainly will have a `CustomerEntity` component to expose and manipulate it. This separation of concerns allows the domain object to remain purely business logic focused while the Entity handles runtime mechanics. Additionally, you may have other domain objects that are leafs of the domain aggregate. These do not need their own entity if they are just a leaf of the aggregate. An address is a good example.

There are two types of entities in Akka. Their difference lies in how they internally function and are persisted.

#### Key Value Entities

_Key Value Entities_ are, as the name implies, an object that is stored and retrieved based on a key - an identifier of some sort. The value is the entire state of the object. Every write to a Key Value Entity persists the entire state of the object. Key Value Entities are similar in some ways to database records. They write and effectively lock the whole row. They still use an underlying event-based architecture so other components can subscribe to the stream of their updates. For more information see [Key Value Entities](java:key-value-entities.adoc).

#### Event Sourced Entities

_Event Sourced Entities_ persist events instead of state in the event [journal](reference:glossary.adoc#journal)   . The current state of the entity is derived from these events. Readers can access the event journal independently of the active entity instance to create read models, known as [_Views_](java:views.adoc), or to perform business actions based on the events via [Consumers](java:consuming-producing.adoc). For more information, see [Event Sourced Entities](java:event-sourced-entities.adoc).

### Views

_Views_ provide a way to materialize read only state from multiple entities based on a query. You can create views from Key Value Entities, Event Sourced Entities, and by subscribing to a topic. For more information see [Views](java:views.adoc).

### Consumers

_Consumers_ listen for and process events or messages from various sources, such as Event Sourced Entities, Key Value Entities and external messaging systems. They can also produce messages to topics, facilitating communication and data flow between different services within an application. For more information see [Consuming and producing](java:consuming-producing.adoc).

### Workflows

_Workflows_ enable the developer to implement long-running, multi-step business processes while focusing exclusively on domain and business logic. Technical concerns such as delivery guarantees, scaling, error handling and recovery are managed by Akka. For more information see [Workflows](java:workflows.adoc).

### Timed actions

_Timed Actions_ allow for scheduling calls in the future. For example, to verify that some process have been completed or not. For more information see [Timed actions](java:timed-actions.adoc).

### Endpoints

_Endpoints_ are defined points of interaction for services that allow external clients to communicate via standard HTTP. They facilitate the integration and communication between the other types of internal Akka components. Unlike other Akka components, endpoints will live in your `api` package. For more information on endpoints see [HTTP Endpoints](java:http-endpoints.adoc).

## Akka Services

A _Service_ is the base deployment unit in Akka. It includes the layers and packages described above. _Services_ are deployed to _Projects_. A project can contain multiple services, which can be deployed to one or more regions. For more about multi-region operations, see [multi-region.adoc](multi-region.adoc).

## Next steps

Now that you understand the overall architecture of Akka you are ready to learn more about the [Akka Deployment Model](deployment-model.adoc). 

The following topics may also be of interest.

* [Development process](development-process.adoc)
* [State model](state-model.adoc)
* [java:dev-best-practices.adoc](java:dev-best-practices.adoc)

Start building your own Akka Service using the [Akka SDK](java:index.adoc).
