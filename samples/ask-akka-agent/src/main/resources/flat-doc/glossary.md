# Glossary of Terms

* **<a name="CICD"></a>CI/CD**\
You can deploy [service](#service)s using a Continuous Integration/Continuous Delivery service. See [operations:integrating-cicd/index.adoc](operations:integrating-cicd/index.adoc) for instructions.
* **<a name="component"></a>Component**\
The SDK supports [endpoint](#endpoint), [key_value_entity](#key_value_entity), [event_sourced_entity](#event_sourced_entity), [workflow](#workflow), [consumer](#consumer), [view](#view) and [timed_action](#timed_action) components. These components enable you to implement your business logic.
* **<a name="consumer"></a>Consumer**\
A component used to consume or produce stream of changes.
* **<a name="component_client"></a>Component client**\
It’s utility to call Akka components without knowing where they are located.
* **<a name="component_id"></a>Component id**\
An id to identify components. Changing component id for [stateful_component](#stateful_component)s should be done with caution, because the id is used for persistence.
* **<a name="command"></a>Command**\
A command comes from a _sender_, and a reply may be sent to the sender. A command expresses the intention to alter the state or retrieve information based on the state of an [entity](#entity) or [workflow](#workflow). A command is materialized by a message received by a component implementation. Commands are not persisted and might fail.
* **<a name="command_handler"></a>Command handler**\
A _command handler_ is the code that handles a command. It may validate the command using the current state, and may emit events or update the state as part of its processing. A command handler ***must not*** update the state of the entity directly, only _indirectly_ by using [effect](#effect) API.
* **<a name="effect"></a>Effect**\
Effects are predefined operations that align with the capabilities of each [component](#component), except [endpoint](#endpoint)s. These operations are the Component’s Effect API. Returning an Effect from the [command_handler](#command_handler) allows the Akka runtime to execute infrastructure-related code transparently to the user. For example, event-sourced entities provide an Effect API that among other things can persist events.
* **<a name="entity"></a>Entity**\
Components like [key_value_entity](#key_value_entity) and [event_sourced_entity](#event_sourced_entity) are usually referred as entities. An entity is conceptually equivalent to a class, or a type of state. It will have multiple [entity_instance](#entity_instance)s, each of which has a unique ID and can handle commands. For example, a service may implement a chat room entity, encompassing the logic associated with chat rooms, and a particular chat room may be an instance of that entity, containing a list of the users currently in the room and a history of the messages sent to it. Entities cache their state and persist it using [effect](#effect) APIs.
* **<a name="entity_instance"></a>Entity instance**\
An instance of an [entity](#entity), which is identified by a unique [id](#id).
* **<a name="endpoint"></a>Endpoint**\
An _endpoint_ component is a way to expose a service to the outside world. They act as controllers ahead of the other components, like [entity](#entity)s or [view](#view)s. They don’t require [component_id](#component_id) because URL address is enough to identify them.
* **<a name="event"></a>Event**\
An _event_  indicates that a change has occurred to an entity and persists the current state. Events are stored in a _journal_, and are read and replayed each time the entity is reloaded by the Akka runtime state management system. An event emitted by one component or service might be interpreted as a command by another.
* **<a name="event_handler"></a>Event handler**\
An _event handler_ is the only piece of code that is allowed to _update_ the state of the [event_sourced_entity](#event_sourced_entity). It receives events, and, according to the event, updates the state.
* **<a name="event_sourced_entity"></a>Event Sourced Entity**\
A type of [entity](#entity) that stores its state using a journal of events, and restores its state by replaying that journal. These are discussed in more detail in [Event Sourced state model](concepts:state-model.adoc#_the_event_sourced_state_model).
* **<a name="id"></a>ID**\
An id used to identify instances of a [stateful_component](#stateful_component)s.
* **<a name="journal"></a>Journal**\
Persistent storage for [event](#event)s from [event_sourced_entity](#event_sourced_entity)s. Some documentation uses the terms _Event Log_ or _Event Store_ instead of journal. Akka handles event storage for you, relieving you of connecting to, configuring, or managing the journal.
* **<a name="key_value_entity"></a>Key Value Entity**\
A Key Value Entity stores state in an update-in-place model, similar to a Key-Value store that supports CRUD (Create, Read, Update, Delete) operations. In Domain Driven Design (DDD) terms, a Value Entity is an "Entity." In contrast with "Value Objects," you reference Entities by an identifier and the value associated with that identifier can change (be updated) over time. These are discussed in more detail in [Key Value state model](concepts:state-model.adoc#_the_key_value_state_model).
* **<a name="opendid-connect"></a>OpenID Connect**\
Akka supports user management with Single Sign-On via OpenID Connect. For details see [security:index.adoc](security:index.adoc).
* **<a name="project"></a>Project**\
A project is the root of one or more [service](#service)s that are meant to be deployed and run together. The project is a logical container for these services and provides common management capabilities.
* **<a name="runtime"></a>Runtime**\
When you deploy a [service](#service), Akka wraps it with the runtime. The runtime manages entity state, and exposes the service implementation to the rest of the system. It translates incoming messages to commands and sends them to the service. The runtime also forms a cluster with other instances of the same service, allowing advanced distributed state management features such as sharding, replication and addressed communication between instances.
* **<a name="service"></a>Service**\
A service is implemented by the Akka SDK. At runtime, Akka enriches the incoming and outgoing messages with state management capabilities, such as the ability to receive and update state. You implement the  business logic for the service, which includes stateful entities. You deploy your services and Akka adds a [runtime](#runtime) that handles incoming communication and persistence at runtime.
* **<a name="snapshot"></a>Snapshot**\
A snapshot records current state of an [event_sourced_entity](#event_sourced_entity). Akka persists snapshots periodically as an optimization. With snapshots, when the Entity is reloaded from the journal, the entire journal doesn’t need to be replayed, just the changes since the last snapshot.
* **<a name="state"></a>State**\
The _state_ is simply data--the current set of values for an [entity_instance](#entity_instance). [entity](#entity)s hold their state in memory.
* **<a name="state_model"></a>State model**\
Each entity uses one of the supported state models. The state model determines the way Akka manages data. Currently, these include [key_value_entity](#key_value_entity) and [event_sourced_entity](#event_sourced_entity).
* **<a name="stateful_component"></a>Stateful component**\
A component like [key_value_entity](#key_value_entity),[event_sourced_entity](#event_sourced_entity) or [workflow](#workflow)
* **<a name="timed_action"></a>Timed actions**\
A Timed Action provides consistent scheduling and execution of a call to another [component](#component) at specified intervals or delays. They are convenient for automating repetitive work and handling timeouts within business logic implementation.
* **<a name="view"></a>View**\
A View provides a way to retrieve state from multiple Entities based on a query. You can query non-key data items. You can create views from Key Value Entity state, Event Sourced Entity events, and by subscribing to topics.
* **<a name="workflow"></a>Workflow**\
Workflows are high-level descriptions to easily align business requirements with their implementation in code. Orchestration across multiple services with support for failure scenarios and compensating actions is simple with Akka Workflows.
* **<a name="workflow_step"></a>Workflow Step**\
A Workflow definition element which encapsulates an action to perform and a transition to the next step (or end transition to finish the Workflow execution).
