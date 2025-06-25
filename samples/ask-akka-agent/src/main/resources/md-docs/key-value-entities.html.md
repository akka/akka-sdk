<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Components](components/index.html)
- [Key Value Entities](key-value-entities.html)

<!-- </nav> -->

# Implementing key value entities

![Key value entity](../_images/key-value-entity.png)
[Key Value Entities](../reference/glossary.html#key_value_entity) are entities that persist the full state on every change. Only the latest state is stored, so we don’t have access to any of the history of changes, unlike the event sourced storage used by [Event Sourced Entities](event-sourced-entities.html).


Entity and Workflow sharding [Stateful components](../reference/glossary.html#stateful_component), such as Entities and Workflows, offer strong consistency guarantees. Each stateful component can have many instances, identified by [ID](../reference/glossary.html#id). Akka distributes them across every service instance in the cluster. We guarantee that there is only one stateful component instance in the whole service cluster. If a command arrives to a service instance not hosting that stateful component instance, the command is forwarded by the Akka Runtime to the one that hosts that particular component instance. This forwarding is done transparently via [Component Client](../reference/glossary.html#component_client) logic. Because each stateful component instance lives on exactly one service instance, messages can be handled sequentially. Hence, there are no concurrency concerns, each Entity or Workflow instance handles one message at a time.

The state of the stateful component instance is kept in memory as long as it is active. This means it can serve read requests or command validation before updating without additional reads from the durable storage. There might not be room for all stateful component instances to be kept active in memory all the time and therefore least recently used instances can be passivated. When the stateful component is used again it recovers its state from durable storage and becomes an active with its system of record in memory, backed by consistent durable storage. This recovery process is also used in cases of rolling updates, rebalance, and abnormal crashes.

Akka needs to serialize that data to send it to the underlying data store. However, we recommend that you do not persist your service’s public API messages. Persisting private API messages may introduce some overhead when converting from a public message to an internal one but it allows the logic of the service public interface to evolve independently of the data storage format, which should be private.

The steps necessary to implement a Key Value Entity include:

1. Defining the API and model the entity’s state.
2. Creating and initializing the Entity.
3. Implementing behavior in command handlers.
The following sections walk through these steps using a counter service as an example.

## <a href="about:blank#_modeling_the_entity"></a> Modeling the entity

As mentioned above, to help us illustrate a Key Value Entity, you will be implementing a Counter service. For such service, you will want to be able to set the initial counter value but also to increase the counter modifying its state. The state will be a simple `Integer` but you will use a wrapper class `Counter` as the domain model, as shown below:

[Counter.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-counter/src/main/java/com/example/domain/Counter.java)
```java
public record Counter(int value) {
  public Counter increment(int delta) {
    return new Counter(value + delta);
  }
}
```

|  | Above we are taking advantage of the Java `record` to reduce the amount of boilerplate code, but you can use regular classes so long as they can be serialized to JSON (e.g. using Jackson annotations). See [Serialization](serialization.html). |

## <a href="about:blank#_identifying_the_entity"></a> Identifying the Entity

In order to interact with an Entity in Akka, we need to assign a **component id** and an instance **id**:

- **component id** is a unique identifier for all entities of a given type. To define the component id, the entity class must be annotated with `@ComponentId` and have a unique and stable identifier assigned.
- **id**, on the other hand, is unique per instance. The entity id is used in the component client when calling the entity from for example an Endpoint.
As an example, an entity representing a customer could have the **component id** `customer` and a customer entity for a specific customer could have the UUID instance **id** `8C59E488-B6A8-4E6D-92F3-760315283B6E`.

|  | The component id and entity id cannot contain the reserved character `\|`, because that is used internally by Akka as a separator. |

## <a href="about:blank#_effect_api"></a> Key value entity’s effect API

The Key Value Entity’s Effect defines the operations that Akka should perform when an incoming command is handled by a Key Value Entity.

A Key Value Entity Effect can either:

- update the entity state and send a reply to the caller
- directly reply to the caller if the command is not requesting any state change
- instruct Akka to delete the entity
- return an error message
For additional details, refer to [Declarative Effects](../concepts/declarative-effects.html).

## <a href="about:blank#entity-behavior"></a> Implementing behavior

Now that we have our Entity state defined, the remaining steps can be summarized as follows:

- Declare your entity and pick an entity id (it needs to be a unique identifier).
- Initialize your entity state
- Implement how each command is handled.
The class signature for our counter entity will look like this:

[CounterEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-counter/src/main/java/com/example/application/CounterEntity.java)
```java
@ComponentId("counter") // (1)
public class CounterEntity extends KeyValueEntity<Counter> { // (2)

  private final String entityId;

  public CounterEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId(); // (3)
  }

  @Override
  public Counter emptyState() { return new Counter// (0); } // (4)
}
```

| **1** | Every Entity must be annotated with `@ComponentId` with a stable unique identifier for this entity type. |
| **2** | The `CounterEntity` class should extend `akka.javasdk.keyvalueentity.KeyValueEntity`. |
| **3** | Stores the `entityId` on an internal attribute so we can use it later. Alternatively, if inside a command handler, `commandContext().entityId()` also provides such information. |
| **4** | The initial state of each counter is defined with value 0. |

|  | The `@ComponentId` value `counter` is common for all instances of this entity but must be stable - cannot be changed after a production deploy - and unique across the different entity types in the service. |

### <a href="about:blank#_updating_state"></a> Updating state

We will now show how to add the command handlers for supporting the two desired operations (`set` and `plusOne`). Command handlers are implemented as methods on the entity class but are also exposed for external interactions and always return an `Effect` of some type.

[CounterEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-counter/src/main/java/com/example/application/CounterEntity.java)
```java
public Effect<Counter> set(int number) {
  Counter newCounter = new Counter(number);
  return effects()
      .updateState(newCounter) // (1)
      .thenReply(newCounter); // (2)
}

public Effect<Counter> plusOne() {
  Counter newCounter = currentState().increment// (1); // (3)
  return effects()
      .updateState(newCounter) // (4)
      .thenReply(newCounter);
}
```

| **1** | Set the new counter value to the value received from the command request. |
| **2** | Reply with the new counter value wrapped within a `Counter` object. |
| **3** | `plusOne` increases the counter by adding 1 to the current state. |
| **4** | Finally, using the Effect API, you instruct Akka to persist the new state, and build a reply with the wrapper object. |

|  | The **only** way for a command handler to modify the Entity’s state is using the `updateState` effect. Any modifications made directly to the state (or instance variables) from the command handler are not persisted. When the Entity is passivated and reloaded, those modifications will not be present. |

### <a href="about:blank#_retrieving_state"></a> Retrieving state

To have access to the current state of the entity we can use `currentState()` as you have probably noticed from the examples above. The following example shows the implementation of the read-only command handler `get` to retrieve the value for a specific counter:

[CounterEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-counter/src/main/java/com/example/application/CounterEntity.java)
```java
public ReadOnlyEffect<Counter> get() {
  return effects()
      .reply(currentState()); // (1)
}
```

| **1** | Reply with the current state. |
What if this is the first command we are receiving for this entity? The initial state is provided by overriding `emptyState()`. That is optional and if not doing it, be careful to deal with a `currentState()` with a `null` value when receiving the first command.

|  | We are returning the internal state directly back to the requester. In the endpoint, it’s usually best to convert this internal domain model into a public model so the internal representation is free to evolve without breaking clients code. |

### <a href="about:blank#deleting-state"></a> Deleting state

The next example shows how to delete a Key Value Entity state by returning special `deleteEntity()` effect.

[CounterEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-counter/src/main/java/com/example/application/CounterEntity.java)
```java
public Effect<Done> delete() {
  return effects()
      .deleteEntity() // (1)
      .thenReply(done());
}
```

| **1** | We delete the state by returning an `Effect` with `effects().deleteEntity()`. |
When you give the instruction to delete the entity it will still exist with an empty state for some time. The actual removal happens later to give downstream consumers time to process the change. By default, the existence of the entity is completely cleaned up after a week.

It is not allowed to make further changes after the entity has been "marked" as deleted. You can still handle read requests to the entity until it has been completely removed, but the current state will be empty. To check whether the entity has been deleted, you can use the `isDeleted` method inherited from the `KeyValueEntity` class.

|  | If you don’t want to permanently delete an entity, you can instead use the `updateState` effect with an empty state. This will work the same as resetting the entity to its initial state. |
It is best to not reuse the same entity id after deletion, but if that happens after the entity has been completely removed it will be instantiated as a completely new entity without any knowledge of previous state.

Note that [deleting View state](views.html#ve_delete) must be handled explicitly.

## <a href="about:blank#_replication"></a> Multi-region replication

Stateful components like Event Sourced Entities, Key Value Entities or Workflow can be replicated to other regions. This is useful for several reasons:

- resilience to tolerate failures in one location and still be operational, even multi-cloud redundancy
- possibility to serve requests from a location near the user to provide better responsiveness
- load balancing to be able to handle high throughput
For each stateful component instance there is a primary region, which handles all write requests. Read requests can be served from any region.

Read requests are defined by declaring the command handler method with `ReadOnlyEffect` as return type. A read-only handler cannot update the state, and that is enforced at compile time.

[ShoppingCartEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java)
```java
public ReadOnlyEffect<ShoppingCart> getCart() {
  return effects().reply(currentState()); // (3)
}
```
Write requests are defined by declaring the command handler method with `Effect` as return type, instead of `ReadOnlyEffect`. Write requests are routed to the primary region and handled by the stateful component instance in that region even if the original call to the instance with the component client was made from another region.

State changes (Workflow, Key Value Entity) or events (Event Sourced Entity) persisted by the instance in the primary region are replicated to other regions and processed by corresponding instance there. This means that the state of the stateful components in all regions are updated from the primary.

The replication is asynchronous, which means that read replicas are eventually updated. Normally within a few milliseconds, but if there is for example a problem with the network between the regions it can take longer time for the read replicas to become up to date, but eventually they will.

This also means that you might not see your own writes, immediately. Consider the following:

- send a write request and that is routed to a primary in another region
- after receiving the response of the write request, you send a read request that is served by the non-primary region
- the stateful component instance in the non-primary region might not have seen the replicated changes yet, and therefore replies with "stale" information
If it’s important for some read requests to have seen latest writes you can use `Effect` for such command handler, even though it is not persisting any events. Then the request will be routed to the primary and use the latest fully consistent state.

The operational aspects are described in [Regions](../operations/regions/index.html).

## <a href="about:blank#_side_effects"></a> Side effects

An entity doesn’t perform any external side effects aside from persisting state changes and replying to the request. Side effects can be handled from the Workflow, Consumer, or Endpoint components that are calling the entity.

## <a href="about:blank#_testing_the_entity"></a> Testing the entity

There are two ways to test an Entity:

- Unit test, which only runs the Entity component with a test kit.
- Integration test, running the entire service with a test kit and the test interacting with it using a component client or over HTTP requests.
Each way has its benefits, unit tests are faster and provide more immediate feedback about success or failure but can only test a single entity at a time and in isolation. Integration tests, on the other hand, are more realistic and allow many entities to interact with other components inside and outside the service.

### <a href="about:blank#_unit_tests"></a> Unit tests

The following snippet shows how the `KeyValueEntityTestKit` is used to test the `CountertEntity` implementation. Akka provides two main APIs for unit tests, the `KeyValueEntityTestKit` and the `KeyValueEntityResult`. The former gives us the overall state of the entity and the ability to call the command handlers while the latter only holds the effects produced for each individual call to the Entity.

[CounterTest.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-counter/src/test/java/com/example/CounterTest.java)
```java
@Test
public void testSetAndIncrease() {
  var testKit = KeyValueEntityTestKit.of(CounterEntity::new); // (1)

  var resultSet = testKit.method(CounterEntity::set).invoke// (10); // (2)
  assertTrue(resultSet.isReply());
  assertEquals(10, resultSet.getReply().value()); // (3)

  var resultPlusOne = testKit.method(CounterEntity::plusOne).invoke(); // (4)
  assertTrue(resultPlusOne.isReply());
  assertEquals(11, resultPlusOne.getReply().value());

  assertEquals(11, testKit.getState().value()); // (5)
}
```

| **1** | Creates the TestKit passing the constructor of the Entity. |
| **2** | Calls the method `set` from the Entity in the `KeyValueEntityTestKit` with value `10`. |
| **3** | Asserts the reply value is `10`. |
| **4** | Calls the method `plusOne` from the Entity in the `KeyValueEntityTestKit` and assert reply value of `11`. |
| **5** | Asserts the state value after both operations is `11`. |

|  | The `KeyValueEntityTestKit` is stateful, and it holds the state of a single entity instance in memory. If you want to test more than one entity in a test, you need to create multiple instance of `KeyValueEntityTestKit`. |

### <a href="about:blank#_integration_tests"></a> Integration tests

The skeleton of an Integration Test is included in the [getting started sample](../getting-started/author-your-first-service.html). Let’s see what it could look like to test our Counter Entity:

[CounterIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-counter/src/test/java/com/example/CounterIntegrationTest.java)
```java
public class CounterIntegrationTest extends TestKitSupport { // (1)

  @Test
  public void verifyCounterSetAndIncrease() {

    Counter counterGet =
        componentClient // (2)
          .forKeyValueEntity("bar")
          .method(CounterEntity::get) // (3)
          .invoke();
    Assertions.assertEquals(0, counterGet.value());

    Counter counterPlusOne =
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::plusOne) // (4)
          .invoke();
    Assertions.assertEquals(1, counterPlusOne.value());

    Counter counterGetAfter = // (5)
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::get)
          .invoke();
    Assertions.assertEquals(1, counterGetAfter.value());
  }

}
```

| **1** | Note the test class must extend `TestKitSupport`. |
| **2** | A built-in component client is provided to interact with the components. |
| **3** | Get the current value of the counter named `bar`. Initial value of counter is expected to be `0`. |
| **4** | Request to increase the value of counter `bar`. Response should have value `1`. |
| **5** | Explicitly request current value of `bar`. It should be `1`. |

|  | The integration tests in samples can be run using `mvn verify`. |

<!-- <footer> -->
<!-- <nav> -->
[Event Sourced Entities](event-sourced-entities.html) [HTTP Endpoints](http-endpoints.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->