# Implementing Key Value Entities

[Key Value Entities](reference:glossary.adoc#key_value_entity) are entities that persist the full state on every change. Only the latest state is stored, so we don’t have access to any of the history of changes, unlike the event sourced storage used by [Event Sourced Entities](java:event-sourced-entities.adoc).

**❗ IMPORTANT**\
Key Value Entities are currently not replicated across regions. The data of all Key Value Entities exists only in the primary region. All requests to Key Value Entities in other regions are forwarded to the primary region. This means that if Key Value Entities are used in a multi-region project the primary region should not be changed, since the data will not exist in the new region. Full replication of Key Value Entities is coming soon.

Akka needs to serialize that data to send it to the underlying data store. However, we recommend that you do not persist your service’s public API messages. Persisting private API messages may introduce some overhead when converting from a public message to an internal one but it allows the logic of the service public interface to evolve independently of the data storage format, which should be private.

The steps necessary to implement a Key Value Entity include:

1. Defining the API and model the entity’s state.
2. Creating and initializing the Entity.
3. Implementing behavior in command handlers.

The following sections walk through these steps using a counter service as an example.

## Modeling the Entity

As mentioned above, to help us illustrate a Key Value Entity, you will be implementing a Counter service. For such service, you will want to be able to set the initial counter value but also to increase the counter modifying its state. The state will be a simple `Integer` but you will use a wrapper class `Counter` as the domain model, as shown below:

**{sample-base-url}/key-value-counter/src/main/java/com/example/domain/Counter.java[Counter.java]**

```java
```

**📌 NOTE**\
Above we are taking advantage of the Java `record` to reduce the amount of boilerplate code, but you can use regular classes so long as they can be serialized to JSON (e.g. using Jackson annotations). See [Serialization](serialization.adoc).

## Key Value Entity's Effect API

The Key Value Entity’s Effect defines the operations that Akka should perform when an incoming command is handled by a Key Value Entity.

A Key Value Entity Effect can either:

* update the entity state and send a reply to the caller
* directly reply to the caller if the command is not requesting any state change
* instruct Akka to delete the entity
* return an error message

For additional details, refer to [Declarative Effects](concepts:declarative-effects.adoc).

## Implementing behavior

Now that we have our Entity state defined, the remaining steps can be summarized as follows:

* Declare your entity and pick an entity id (it needs to be a unique identifier).
* Initialize your entity state
* Implement how each command is handled.

The class signature for our counter entity will look like this:

**{sample-base-url}/key-value-counter/src/main/java/com/example/application/CounterEntity.java[CounterEntity.java]**

```java
```
1. Every Entity must be annotated with `@ComponentId` with a stable unique identifier for this entity type.
2. The `CounterEntity` class should extend `akka.javasdk.keyvalueentity.KeyValueEntity`.
3. The initial state of each counter is defined with value 0.

**📌 NOTE**\
The `@ComponentId` value `counter` is common for all instances of this entity but must be stable - cannot be changed after a production deploy - and unique across the different entity types in the service.

### Updating state

We will now show how to add the command handlers for supporting the two desired operations (`set` and `plusOne`). Command handlers are implemented as methods on the entity class but are also exposed for external interactions and always return an `Effect` of some type.

**{sample-base-url}/key-value-counter/src/main/java/com/example/application/CounterEntity.java[CounterEntity.java]**


```
1. Set the new counter value to the value received from the command request.
2. Reply with the new counter value wrapped within a `Counter` object.
3. `plusOne` increases the counter by adding 1 to the current state.
4. Finally, using the Effect API, you instruct Akka to persist the new state, and build a reply with the wrapper object.

**❗ IMPORTANT**\
The ***only*** way for a command handler to modify the Entity’s state is using the `updateState` effect. Any modifications made directly to the state (or instance variables) from the command handler are not persisted. When the Entity is passivated and reloaded, those modifications will not be present.

### Retrieving state

The following example shows how to implement a simple endpoint to retrieve the current state of the entity, in this case the value for a specific counter.

**{sample-base-url}/key-value-counter/src/main/java/com/example/application/CounterEntity.java[CounterEntity.java]**


```
1. Reply with the current state.

**❗ IMPORTANT**\
We are returning the internal state directly back to the requester. In the endpoint, it’s usually best to convert this internal domain model into a public model so the internal representation is free to evolve without breaking clients code.

### Deleting state

The next example shows how to delete a Key Value Entity state by returning special `deleteEntity()` effect.

**{sample-base-url}/key-value-counter/src/main/java/com/example/application/CounterEntity.java[CounterEntity.java]**


```
1. We delete the state by returning an `Effect` with `effects().deleteEntity()`.

When you give the instruction to delete the entity it will still exist with an empty state for some time. The actual removal happens later to give downstream consumers time to process the change. By default, the existence of the entity is completely cleaned up after a week.

It is not allowed to make further changes after the entity has been "marked" as deleted. You can still handle read requests to the entity until it has been completely removed, but the current state will be empty. To check whether the entity has been deleted, you can use the `isDeleted` method inherited from the `KeyValueEntity` class.

**📌 NOTE**\
If you don’t want to permanently delete an entity, you can instead use the `updateState` effect with an empty state. This will work the same as resetting the entity to its initial state.

It is best to not reuse the same entity id after deletion, but if that happens after the entity has been completely removed it will be instantiated as a completely new entity without any knowledge of previous state.

Note that [deleting View state](views.adoc#ve_delete) must be handled explicitly.

## Side Effects

An entity doesn’t perform any external side effects aside from persisting events and replying to the request. Side effects can be handled from the Workflow, Consumer, or Endpoint components that are calling the entity.

## Testing the Entity

### Unit tests

The following snippet shows how the `KeyValueEntityTestKit` is used to test the `CountertEntity` implementation. Akka provides two main APIs for unit tests, the `KeyValueEntityTestKit` and the `KeyValueEntityResult`. The former gives us the overall state of the entity and the ability to call the command handlers while the latter only holds the effects produced for each individual call to the Entity.

**{sample-base-url}/key-value-counter/src/test/java/com/example/CounterTest.java[CounterTest.java]**


```
1. Creates the TestKit passing the constructor of the Entity.
2. Calls the method `set` from the Entity in the `KeyValueEntityTestKit` with value `10`.
3. Asserts the reply value is `10`.
4. Calls the method `plusOne` from the Entity in the `KeyValueEntityTestKit` and assert reply value of `11`.
5. Asserts the state value after both operations is `11`.

**📌 NOTE**\
The `KeyValueEntityTestKit` is stateful, and it holds the state of a single entity instance in memory. If you want to test more than one entity in a test, you need to create multiple instance of `KeyValueEntityTestKit`.

### Integration tests

The skeleton of an Integration Test is generated for you if you use the archetype to start your Akka service. Let’s see what it could look like to test our Counter Entity:

**{sample-base-url}/key-value-counter/src/test/java/com/example/CounterIntegrationTest.java[CounterIntegrationTest.java]**


```
1. Note the test class must extend `TestKitSupport`.
2. A built-in component client is provided to interact with the components.
3. Get the current value of the counter named `bar`. Initial value of counter is expected to be `0`.
4. Request to increase the value of counter `bar`. Response should have value `1`.
5. Explicitly request current value of `bar`. It should be `1`.

**📌 NOTE**\
The integration tests in samples can be run using `mvn verify`.
