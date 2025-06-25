<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Entity state models](state-model.html)

<!-- </nav> -->

# Entity state models

Entities are used to store the data defined in the [domain model](architecture-model.html#_domain). They follow a specific *state model* chosen by the developer. The state model determines how the data is organized and persisted. Entities have data fields that can be simple or primitive types like numbers, strings, booleans, and characters. The fields can be more complex, which allows custom types to be stored in Akka.

Entities have operations that can change their state. These operations are triggered asynchronously and implemented via methods that return <a href="declarative-effects.html">`Effect`</a>. Operations allow entities to be dynamic and reflect the most up-to-date information and this all gets wired together for you.

Akka offers two state models: *Event Sourced Entity* and *Key Value Entity*. Event Sourced Entities build their state incrementally by storing each update as an event, while Key Value Entities store their entire state as a single entry in a Key/Value store. To replicate state across clusters and regions, Akka uses specific conflict resolution strategies for each state model.

Event Sourced Entities, Key Value Entities and Workflows replicate their state by default. If you deploy your Service to a Project that spans multiple regions the state is replicated for you with no extra work to be done. By default, any region can read the data, and will do so from a local store within the region, but only the primary region will be able to perform writes. To make this easier, Akka will forward writes to the appropriate region.

To understand more about regions and distribution see [Deployment model](deployment-model.html#_region).

## <a href="about:blank#_identity"></a> Identity

Each Entity instance has a unique id that distinguishes it from others. The id can have multiple parts, such as an address, serial number, or customer number. Akka handles concurrency for Entity instances by processing requests sequentially, one after the other, within the boundaries of a transaction. Akka proactively manages state, eliminating the need for techniques like lazy loading. For each state model, Akka uses a specific back-end data store, which cannot be configured.

### <a href="about:blank#_origin"></a> Origin

Stateful entities in Akka have a concept of location, that is region, and are designed to span regions and replicate their data. For more information about regions see [region](deployment-model.html#_region) in the Akka deployment model.

Entities call the region they were created in their **origin** and keep track of it throughout their lifetime. This allows Akka to simplify some aspects of distributed state.

By default, most entities will only allow their origin region to change their state. To make this easier, Akka will automatically route state-changing operations to the origin region. This routing is asynchronous and durable, meaning network partitions will not stop the write from being queued. This gives you a read-anywhere model out of the box that automatically routes writes appropriately.

## <a href="about:blank#_the_event_sourced_state_model"></a> The Event Sourced state model

The Event Sourced state model captures changes to data by storing events in a journal. The current entity state is derived from the events. Interested parties can read the journal and transform the stream of events into read models (Views) or perform business actions based on events.

![Concepts Events Source Flow](_images/event-sourced-entity-flow.svg)


A client sends a request to an Endpoint
![steps 1](_images/steps-1.svg)
. The request is handled in the Endpoint which decides to send a command to the appropriate Event sourced  entity
![steps 2](_images/steps-2.svg)
, its identity is either determined from the request or by logic in the Endpoint.

The Event sourced entity processes the command
![steps 3](_images/steps-3.svg)
. This command requires updating the Event sourced entity state. To update the state it emits events describing the state change. Akka stores these events in the event store
![steps 4](_images/steps-4.svg)
.

After successfully storing the events, the event sourced entity updates its state through its event handlers
![steps 5](_images/steps-5.svg)
.

The business logic also describes the reply as the commands effect which is passed back to the Endpoint
![steps 6](_images/steps-6.svg)
. The Endpoint replies to the client when the reply is processed
![steps 7](_images/steps-7.svg)
.

|  | Event sourced entities express state changes as events that get applied to update the state. |

## <a href="about:blank#_the_key_value_state_model"></a> The Key Value state model

In the *Key Value* state model, only the current state of the Entity is persisted - its value. Akka caches the state to minimize data store access. Interested parties can subscribe to state changes emitted by a Key Value Entity and perform business actions based on those state changes.

![Concepts Key Value Flow](_images/key-value-entity-flow.svg)


A client sends a request to an Endpoint
![steps 1](_images/steps-1.svg)
. The request is handled in the Endpoint which decides to send a command to the appropriate Key Value entity
![steps 2](_images/steps-2.svg)
, its identity is either determined from the request or by logic in the Endpoint.

The Key Value entity processes the command
![steps 3](_images/steps-3.svg)
. This command requires updating the Key Value entity state. To persist the new state of the Key Value entity, it returns an effect. Akka updates the full state in its persistent data store
![steps 4](_images/steps-4.svg)
.

The business logic also describes the reply as the commands effect which is passed back to the Endpoint
![steps 5](_images/steps-5.svg)
. The Endpoint replies to the client when the reply is processed
![steps 6](_images/steps-6.svg)
.

|  | Key Value entities capture state as one single unit, they do not express state changes in events. |

## <a href="about:blank#_state_models_and_replication"></a> State models and replication

Event Sourced entities are replicated between all regions in an Akka project by default. This allows for a multi-reader capability, with writes automatically routed to the correct region based on the origin of the entity.

In order to have multi-writer (or write anywhere) capabilities you must implement a conflict-free replicated data type (CRDT) for your Event Sourced Entity. This allows data to be shared across multiple instances of an entity and is eventually consistent to provide high availability with low latency. The underlying CRDT semantics allow replicated Event Sourced Entity instances to update their state independently and concurrently and without coordination. The state changes will always converge without conflicts, but note that with the state being eventually consistent, reading the current data may return an out-of-date value.

|  | Although Key Value Entities are planned to support a Last Writer Wins (LWW) mechanism, this feature is not yet available. |

## <a href="about:blank#_related_documentation"></a> Related documentation

- [Event Sourced Entities](../java/event-sourced-entities.html)
- [Key Value Entities](../java/key-value-entities.html)

<!-- <footer> -->
<!-- <nav> -->
[Declarative effects](declarative-effects.html) [Multi-region operations](multi-region.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->