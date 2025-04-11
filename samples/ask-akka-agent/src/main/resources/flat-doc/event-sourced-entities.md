# Implementing Event Sourced Entities

Event Sourced Entities are components that persist their state using the Event Sourcing Model. Instead of persisting the current state, they persist all the events that led to the current state. Akka stores these events in a [journal](reference:glossary.adoc#journal). Event Sourced Entities persist their state with [ACID semantics, window="new"](https://en.wikipedia.org/wiki/ACID), scale horizontally, and isolate failures.

An Event Sourced Entity must not update its in-memory state directly as a result of a [_command_](reference:glossary.adoc#command). The handling of a command, if it results in changes being required to state, should persist [_events_](reference:glossary.adoc#event). These events will then be processed by the entity, at which point the in-memory state can and should be changed in response.

When you need to read state in your service, ask yourself _what events should I be listening to_? When you need to write state, ask yourself _what events should I be persisting_?

![console-cart-events](console-cart-events.png)

The image above is from the Akka console and illustrates how events for a shopping cart updates the state of the cart entity.

* 3 Akka T-shirts added.
* 5 Akka socks added.
* 4 more Akka T-shirts added, making a total of 7.
* Cart is checked out.

To load an Entity, Akka reads the journal and replays events to compute the Entity’s current state. As an optimization, by default, Event Sourced Entities persist state snapshots periodically. This allows Akka to recreate an Entity from the most recent snapshot plus any events saved after the snapshot.

In contrast with typical create, read, update (CRUD) systems, event sourcing allows the state of the Entity to be reliably replicated to other services. Event Sourced Entities use offset tracking in the journal to record which portions of the system have replicated which events.

[Event Sourced Entities](reference:glossary.adoc#event_sourced_entity) persist changes as events and snapshots. Akka needs to serialize that data to send it to the underlying data store. However, we recommend that you do not persist your service’s public API messages. Persisting private API messages may introduce some overhead when converting from a public message to an internal one but it allows the logic of the service public interface to evolve independently of the data storage format, which should be private.

The steps necessary to implement an Event Sourced Entity include:

1. Model the entity’s state and its domain events.
2. Implementing behavior in command and event handlers.

The following sections walk through these steps using a shopping cart service as an example (working sample can be downloaded as a [zip file](../java/_attachments/shopping-cart-quickstart.zip)).

## Modeling the Entity

Through our "Shopping Cart" Event Sourced Entity we expect to manage our cart, adding and removing items as we please. Being event-sourced means it will represent changes to state as a series of domain events. Let’s have a look at what kind of model we expect to store and the events our entity might generate.

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/domain/ShoppingCart.java[ShoppingCart.java]**

```java
```
1. Our `ShoppingCart` is fairly simple, being composed only by a `cartId` and a list of line items.
2. A `LineItem` represents a single product and the quantity we intend to buy.

**📌 NOTE**\
Above we are taking advantage of the Java `record` to reduce the amount of boilerplate code, but you can use regular classes so long as they can be serialized to JSON (e.g. using Jackson annotations).

Another fundamental aspect of our entity will be its domain events. For now, we will have 3 different events `ItemAdded`, `ItemRemoved` and `CheckedOut`, defined as below:

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/domain/ShoppingCartEvent.java[ShoppingCartEvent.java]**

```java
```
1. The 3 types of event all derive from the same type `ShoppingCartEvent`.
2. Includes the logical type name using `@TypeName` annotation.

**❗ IMPORTANT**\
The use of logical names for subtypes is essential for maintainability purposes. Our recommendation is to use logical names (i.e. `@TypeName`) that are unique per Akka service. Check [type name](serialization.adoc#_type_name) documentation for more details.

## Event Sourced Entity's Effect API

The Event Sourced Entity’s Effect defines the operations that Akka should perform when an incoming command is handled by an Event Sourced Entity.

An Event Sourced Entity Effect can either:

* persist events and send a reply to the caller
* directly reply to the caller if the command is not requesting any state change
* instruct Akka to delete the entity and send a reply to the caller
* return an error message

For additional details, refer to [Declarative Effects](concepts:declarative-effects.adoc).

## Implementing behavior

Now that we have our Entity state defined along with its events, the remaining steps can be summarized as follows:

* declare your entity and pick a component id (it needs to be unique as it will be used for sharding purposes);
* implement how each command is handled and which event(s) it generates;
* provide an event handler and how it updates the entity’s state.

The class signature for our shopping cart entity will look like this:

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java[ShoppingCartEntity.java]**

```java
```
1. Create a class that extends `EventSourcedEntity<S, E>`, where `S` is the state type this entity will store (i.e. `ShoppingCart`) and `E` is the top type for the events it persists (i.e. `ShoppingCartEvent`).
2. Make sure to annotate such class with `@ComponentId` and pass a stable unique identifier for this entity type.

**📌 NOTE**\
The `@ComponentId` value `shopping-cart` is common for all instances of this entity but must be stable - cannot be changed after a production deploy - and unique across the different entity types in the service.

### Updating state

Having created the basis of our entity, we will now define how each command is handled. In the example below, we define a method that will add a new line item to a given shopping cart. It returns an `Effect` to persist an event and then sends a reply once the event is stored successfully. The state is updated by the event handler.

**❗ IMPORTANT**\
The ***only*** way for a command handler to modify the Entity’s state is by persisting an event. Any modifications made directly to the state (or instance variables) from the command handler are not persisted. When the Entity is passivated and reloaded, those modifications will not be present.

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java[ShoppingCartEntity.java]**

```java
```
1. The validation ensures the quantity of items added is greater than zero and it fails for calls with illegal values by returning an `Effect` with `effects().error`.
2. From the current incoming `LineItem` we create a new `ItemAdded` event representing the change of the cart.
3. We store the event by returning an `Effect` with `effects().persist`.
4. The acknowledgment that the command was successfully processed is only sent if the event was successfully stored and applied, otherwise there will be an error reply. The lambda parameter `newState` gives us access to the new state returned by applying such event.
5. Event handler returns the updated state after applying the event - the logic for state transition is defined inside the `ShoppingCart` domain model.

As mentioned above, the business logic that allows us to transition between states was placed on the domain model as seen below:

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/domain/ShoppingCart.java[ShoppingCart.java]**


```
1. For an existing item, we will make sure to sum the existing quantity with the incoming one.
2. Returns an update list of items without the existing item.
3. Adds the update item to the shopping cart.
4. Returns a new instance of the shopping cart with the updated line items.

### Retrieving state

To have access to the current state of the entity we can use `currentState()` as you have probably noticed from the examples above. However, what if this is the first command we are receiving for this entity? The following example shows the implementation of the read-only command handler `getCart`:

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java[ShoppingCartEntity.java]**


```
1. Stores the `entityId` on an internal attribute so we can use it later.
2. Provides initial state - overriding `emptyState()` is optional but if not doing it, be careful to deal with a currentState() with a `null` value when receiving the first command or event.
3. Returns the current state as reply for the request.

**❗ IMPORTANT**\
We are returning the internal state directly back to the requester. In the endpoint, it’s usually best to convert this internal domain model into a public model so the internal representation is free to evolve without breaking clients code.

### Deleting an Entity

Normally, Event Sourced Entities are not deleted because the history of the events typically provide business value.
For certain use cases or for regulatory reasons the entity can be deleted.

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java[ShoppingCartEntity.java]**


```
1. Persist final event before deletion, which is handled as any other event.
2. Instruction to delete the entity.

When you give the instruction to delete the entity it will still exist for some time, including its events and snapshots. The actual removal of events and snapshots will be deleted later to give downstream consumers time to process all prior events, including the final event that was persisted together with the `deleteEntity` effect. By default, the existence of the entity is completely cleaned up after a week.

It is not allowed to persist more events after the entity has been "marked" as deleted. You can still handle read requests to the entity until it has been completely removed. To check whether the entity has been deleted, you can use the `isDeleted` method inherited from the `EventSourcedEntity` class.

It is best to not reuse the same entity id after deletion, but if that happens after the entity has been completely removed it will be instantiated as a completely new entity without any knowledge of previous state.

Note that [deleting View state](views.adoc#ve_delete) must be handled explicitly.

## Snapshots

Snapshots are an important optimization for Event Sourced Entities that persist many events. Rather than reading the entire journal upon loading or restart, Akka can initiate them from a snapshot.

Snapshots are stored and handled automatically by Akka without any specific code required. Snapshots are stored after a configured number of events:

**{sample-base-url}/shopping-cart-quickstart/src/main/resources/application.conf[application.conf]**


```

When the Event Sourced Entity is loaded again, the snapshot will be loaded before any other events are received.

## Side Effects

An entity doesn’t perform any external side effects aside from persisting events and replying to the request. Side effects can be handled from the Workflow, Consumer, or Endpoint components that are calling the entity.

## Testing the Entity

### Unit tests

The following snippet shows how the `EventSourcedTestKit` is used to test the `ShoppingCartEntity` implementation. Akka provides two main APIs for unit tests, the `EventSourcedTestKit` and the `EventSourcedResult`. The former gives us the overall state of the entity and all the events produced by all the calls to the Entity. While the latter only holds the effects produced for each individual call to the Entity.

**{sample-base-url}/shopping-cart-quickstart/src/test/java/shoppingcart/application/ShoppingCartTest.java[ShoppingCartTest.java]**

```java
```
1. Creates the TestKit passing the constructor of the Entity.
2. Calls the method `addItem` from the Entity in the `EventSourcedTestKit` with quantity `10`.
3. Asserts the return value is `"OK"`.
4. Returns the next event of type `IdemAdded` and asserts on the quantity.
5. Add a new item with quantity `5`.
6. Asserts that the total number of events should be 2.
7. Calls the `getCart` method and asserts that quantity should be `15`.

**📌 NOTE**\
The `EventSourcedTestKit` is stateful, and it holds the state of a single entity instance in memory. If you want to test more than one entity in a test, you need to create multiple instance of `EventSourcedTestKit`.

**EventSourcedResult**

Calling a command handler through the TestKit gives us back an [`EventSourcedResult`, window="new"]({attachmentsdir}/testkit/akka/javasdk/testkit/EventSourcedResult.html). This class has methods that we can use to assert the handling of the command, such as:

* `getReply()` - the response from the command handler if there was one, if not an, exception is thrown, failing the test.
* `getAllEvents()` - all the events persisted by handling the command.
* `getState()` - the state of the entity after applying any events the command handler persisted.
* `getNextEventOfType(ExpectedEvent.class)` - check the next of the persisted events against an event type, return it for inspection if it matches, or fail the test if it does not. The event gets consumed once is inspected and the next call will look for a subsequent event.

**EventSourcedTestKit**

For the above example, this class provides access to all the command handlers of the `ShoppingCart` entity for unit testing. In addition to that also has the following methods:

* `getState()` - the current state of the entity, it is updated on each method call persisting events.
* `getAllEvents()` - all events persisted since the creation of the testkit instance.

### Integration tests

The skeleton of an Integration Test is generated for you if you use the archetype to start your Akka service. Let’s see what it could look like to test our `ShoppingCartEntity`:

**{sample-base-url}/shopping-cart-quickstart/src/test/java/shoppingcart/ShoppingCartIntegrationTest.java[ShoppingCartIntegrationTest.java]**

```java
```
1. Note the test class must extend `TestKitSupport`.
2. A built-in component client is provided to interact with the components.
3. Request to create a new shopping cart with id `cart-abc`.
4. Request to add an item to the cart.
5. Request to retrieve current status of the shopping cart.
6. Assert there should only be one item.

**📌 NOTE**\
The integration tests in samples can be run using `mvn verify`.
