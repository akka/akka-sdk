<!-- <nav> -->
- [Akka](../index.html)
- [Reference](index.html)
- [Glossary of terms](glossary.html)

<!-- </nav> -->

# Glossary of Terms

<a href=""></a> Agent A component that interacts with an AI model to perform a specific task. It is typically backed by a large language model (LLM). It maintains contextual history in a session memory, which may be shared between multiple agents that are collaborating on the same goal. It may provide function tools and call them as requested by the model.

<a href=""></a> CI/CD You can deploy [Service](about:blank#service) s using a Continuous Integration/Continuous Delivery service. See [Integrating with CI/CD tools](../operations/integrating-cicd/index.html) for instructions.

<a href=""></a> Component The SDK supports [Agent](about:blank#agent), [Endpoint](about:blank#endpoint), [Key Value Entity](about:blank#key_value_entity), [Event Sourced Entity](about:blank#event_sourced_entity), [Workflow](about:blank#workflow), [Consumer](about:blank#consumer), [View](about:blank#view) and [Timed actions](about:blank#timed_action) components. These components enable you to implement your business logic.

<a href=""></a> Consumer A component used to consume or produce a stream of changes.

<a href=""></a> Component client A utility to call Akka components without knowing where they are located.

<a href=""></a> Component id An id to identify components. Changing component id for [Stateful component](about:blank#stateful_component) s should be done with caution, because the id is used for persistence.

<a href=""></a> Command A command comes from a *sender*, and a reply may be sent to the sender. A command expresses the intention to alter the state or retrieve information based on the state of an [Entity](about:blank#entity) or [Workflow](about:blank#workflow). A command is materialized by a message received by a component implementation. Commands are not persisted and might fail.

<a href=""></a> Command handler A *command handler* is the code that handles a command. It may validate the command using the current state, and may emit events or update the state as part of its processing. A command handler **must not** update the state of the entity directly, only *indirectly* by using [Effect](about:blank#effect) API.

<a href=""></a> Effect Effects are predefined operations that align with the capabilities of each [Component](about:blank#component), except [Endpoint](about:blank#endpoint) s. These operations are the Component’s Effect API. Returning an Effect from the [Command handler](about:blank#command_handler) allows the Akka runtime to execute infrastructure-related code transparently to the user. For example, event-sourced entities provide an Effect API that among other things can persist events.

<a href=""></a> Entity Components like [Key Value Entity](about:blank#key_value_entity) and [Event Sourced Entity](about:blank#event_sourced_entity) are usually referred as entities. An entity is conceptually equivalent to a class, or a type of state. It will have multiple [Entity instance](about:blank#entity_instance) s, each of which has a unique ID and can handle commands. For example, a service may implement a chat room entity, encompassing the logic associated with chat rooms, and a particular chat room may be an instance of that entity, containing a list of the users currently in the room and a history of the messages sent to it. Entities cache their state and persist it using [Effect](about:blank#effect) APIs.

<a href=""></a> Entity instance An instance of an [Entity](about:blank#entity), which is identified by a unique [ID](about:blank#id).

<a href=""></a> Endpoint An *endpoint* component is a way to expose a service to the outside world. They act as controllers ahead of the other components, like [Entity](about:blank#entity) s or [View](about:blank#view) s. They don’t require [Component id](about:blank#component_id) because URL address is enough to identify them.

<a href=""></a> Event An *event* indicates that a change has occurred to an entity and persists the current state. Events are stored in a *journal*, and are read and replayed each time the entity is reloaded by the Akka runtime state management system. An event emitted by one component or service might be interpreted as a command by another.

<a href=""></a> Event handler An *event handler* is the only piece of code that is allowed to *update* the state of the [Event Sourced Entity](about:blank#event_sourced_entity). It receives events, and, according to the event, updates the state.

<a href=""></a> Event Sourced Entity A type of [Entity](about:blank#entity) that stores its state using a journal of events, and restores its state by replaying that journal. These are discussed in more detail in [Event Sourced state model](../concepts/state-model.html#_the_event_sourced_state_model).

<a href=""></a> ID An id used to identify instances of a [Stateful component](about:blank#stateful_component) s.

<a href=""></a> Journal Persistent storage for [Event](about:blank#event) s from [Event Sourced Entity](about:blank#event_sourced_entity) s. Some documentation uses the terms *Event Log* or *Event Store* instead of journal. Akka handles event storage for you, relieving you of connecting to, configuring, or managing the journal.

<a href=""></a> Key Value Entity A Key Value Entity stores state in an update-in-place model, similar to a Key-Value store that supports CRUD (Create, Read, Update, Delete) operations. In Domain Driven Design (DDD) terms, a Value Entity is an "Entity." In contrast with "Value Objects," you reference Entities by an identifier and the value associated with that identifier can change (be updated) over time. These are discussed in more detail in [Key Value state model](../concepts/state-model.html#_the_key_value_state_model).

<a href=""></a> OpenID Connect Akka supports user management with Single Sign-On via OpenID Connect.

<a href=""></a> Project A project is the root of one or more [Service](about:blank#service) s that are meant to be deployed and run together. The project is a logical container for these services and provides common management capabilities.

<a href=""></a> Runtime When you deploy a [Service](about:blank#service), Akka wraps it with the runtime. The runtime manages entity state, and exposes the service implementation to the rest of the system. It translates incoming messages to commands and sends them to the service. The runtime also forms a cluster with other instances of the same service, allowing advanced distributed state management features such as sharding, replication and addressed communication between instances.

<a href=""></a> Service A service is implemented by the Akka SDK. At runtime, Akka enriches the incoming and outgoing messages with state management capabilities, such as the ability to receive and update state. You implement the  business logic for the service, which includes stateful entities. You deploy your services and Akka adds a [Runtime](about:blank#runtime) that handles incoming communication and persistence at runtime.

<a href=""></a> Snapshot A snapshot records current state of an [Event Sourced Entity](about:blank#event_sourced_entity). Akka persists snapshots periodically as an optimization. With snapshots, when the Entity is reloaded from the journal, the entire journal doesn’t need to be replayed, just the changes since the last snapshot.

<a href=""></a> State The *state* is simply data—​the current set of values for an [Entity instance](about:blank#entity_instance). [Entity](about:blank#entity) s hold their state in memory.

<a href=""></a> State model Each entity uses one of the supported state models. The state model determines the way Akka manages data. Currently, these include [Key Value Entity](about:blank#key_value_entity) and [Event Sourced Entity](about:blank#event_sourced_entity).

<a href=""></a> Stateful component A component like [Key Value Entity](about:blank#key_value_entity), [Event Sourced Entity](about:blank#event_sourced_entity) or [Workflow](about:blank#workflow)

<a href=""></a> Timed actions A Timed Action provides consistent scheduling and execution of a call to another [Component](about:blank#component) at specified intervals or delays. They are convenient for automating repetitive work and handling timeouts within business logic implementation.

<a href=""></a> View A View provides a way to retrieve state from multiple Entities based on a query. You can query non-key data items. You can create views from Key Value Entity state, Event Sourced Entity events, and by subscribing to topics.

<a href=""></a> Workflow Workflows are high-level descriptions to easily align business requirements with their implementation in code. Orchestration across multiple services with support for failure scenarios and compensating actions is simple with Akka Workflows.

<a href=""></a> Workflow Step A Workflow definition element which encapsulates an action to perform and a transition to the next step (or end transition to finish the Workflow execution).

<!-- <footer> -->
<!-- <nav> -->
[Observability descriptor](descriptors/observability-descriptor.html) [Security announcements](security-announcements/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->