<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Developer best practices](dev-best-practices.html)

<!-- </nav> -->

# Developer best practices

## <a href="about:blank#_reactive_principles"></a> Reactive principles

Akka is ideally suited for the creation of *Microservices*. Microservices generally follow the Unix philosophy of "Do one thing and do it well." Akka allows developers to build systems that follow [the Reactive Principles](https://principles.reactive.foundation/) without having to become distributed data or distributed computing experts. As a best practice, following the Reactive Principles in your design makes it easier to build distributed systems. [Akkademy](https://akkademy.akka.io/learn/public/catalog/view/3) offers free courses on Reactive Architecture.

## <a href="about:blank#_domain_driven_design"></a> Domain Driven Design

Domain-driven design (DDD) is the concept that the structure and language of software code (class names, class methods, class variables) should match the business domain. For example, if a software processes loan applications, it might have classes such as LoanApplication and Customer, and methods such as AcceptOffer and Withdraw. — [Wikipedia](https://en.wikipedia.org/wiki/Domain-driven_design) Akka makes it easy and fast to build services using the concepts of Domain Driven Design (DDD). While it’s not necessary to understand all the ins and outs of Domain Driven Design, you’ll find a few of the concepts that make building services even more straightforward below. See [Akka architecture model](../concepts/architecture-model.html) for more information on the role of your domain model in Akka and how Akka layers your architecture. Akkademy provides a free course on [Domain Driven Design](https://akkademy.akka.io/learn/courses/6/reactive-architecture2-domain-driven-design).

### <a href="about:blank#bounded-context"></a> Bounded context

[Bounded context](https://martinfowler.com/bliki/BoundedContext.html) is a concept that divides large domain models into smaller groups that are explicit about their interrelationships. Normally a microservice is a bounded context. You *may* choose to have multiple bounded contexts in a microservice.

Each of these contexts will have autonomy to evolve the models it owns. Keeping each model within strict boundaries allows different modelling for entities that look similar but have slightly different meaning in each of the contexts. Each bounded context should have its own domain, application, and API layers as described in [Akka architecture model](../concepts/architecture-model.html).

![Bounded Context](_images/bounded-context.svg)

### <a href="about:blank#_events_first"></a> Events first

Defining your data structures first, and splitting them into bounded contexts, will also help you think about all the different interactions your data needs to have. These interactions, like `ItemAddedToShoppingCart` or `LightbulbTurnedOn` are the events that are persisted and processed in Akka. Defining your data structures first, makes it easier to think about which events are needed and where they fit into your design. These data structures and events will live in your domain model layer as described in [Akka architecture model](../concepts/architecture-model.html).

### <a href="about:blank#_message_migration"></a> Message migration

Behind the scenes everything in Akka is ultimately message driven. Plan for the evolution of your messages. Commands and events use data structures that often must evolve over time. Fields get added, fields get removed, the meaning of fields may change. How do you handle this over time as your system grows and evolves? Akka will make this easier, but you are ultimately responsible for schema evolution.

## <a href="about:blank#_right_sizing_your_services"></a> Right-sizing your services

Each Akka Service consists of one or more Components and is packaged and deployed as a unit. Akka services are deployed to Akka Projects. Thus, when you couple multiple business concerns by packaging them in the same service, even under separate bounded contexts, you limit the runtime’s ability to make the most efficient decisions to scale up or down.

### <a href="about:blank#_how_big_to_make_your_services"></a> How big to make your services

Deciding how many components and concepts to fit into a single service can be complex. Generally smaller is better hence the name microservices often being used. When you design a series of small services that don’t share code and can be deployed independently, you reap these benefits:

- **Your development velocity is higher**. It is faster and less complex to write and debug them because they focus on a small set of operations, usually around a single business concern (be it with one or multiple types of <a href="../reference/glossary.html#entity">*Entities*</a>).
- **Your operating velocity is higher**. Using smaller independent services simplifies operational concerns and provide scalability because they can be deployed, stopped and started independently of the rest of the system.
- **You can scale the services independently** to handle variations in load gracefully. If properly designed, multiple instances of the service can be started up when necessary to support more load: for example, if your system runs on Black Friday and the shopping cart service gets super busy, you can spin up more shopping carts to handle that load without also having to start up more copies of the catalog service. When the load decreases, these extra instances can be removed again, minimizing resource usage.
- **You reduce the failure domain / fault boundary**. Independent services handle failures gracefully. Components interact asynchronously with the rest of the world through messages and <a href="../reference/glossary.html#command">*commands*</a>. If one instance, or even a whole service, fails, it is possible for the rest of the system to keep going with reduced capabilities. This prevents cascading failures that take down entire systems.
- **Your development team is more productive**. A team can focus on features of a single service at a time, without worrying about what other services or teams are doing, or when they are releasing, allowing more parallel teams to focus on other services, allowing your development efforts to scale as needed.
- **You gain flexibility for upgrades**. You can upgrade services in a "rolling" fashion, where new instances are started before older instances are removed, allowing new versions to be deployed with no downtime or interruption.
- **You gain security**. Services serve as a security boundary both in your system overall and between teams.
- **You get granular visibility into costs**. Services are all billed separately, so it’s easier to see and understand costs and billing on a per-service basis if you break your services up in some way that matches your organizational needs overall.

## <a href="about:blank#message-deduplication"></a> Message deduplication

In the realm of distributed systems, Akka embraces an at-least-once delivery guarantee, for components like Consumer or Views (view updaters). Redeliveries occur in distributed systems due to their inherent uncertainty and failure characteristics. Network failures, process crashes, restarts, and temporary unavailability of nodes can all lead to situations where an acknowledgment for a delivered message is lost, even if the recipient successfully processed it. To ensure eventual consistency and guarantee delivery, the sender must retry messages when acknowledgments are missing.

|  | When consuming from Akka components like Event Sourced Entity, Key Value Entity or another Akka service, the Akka runtime not only guarantees at-least-once delivery, but also the order of messages. Meaning that a series of duplicated messages might be redelivered but always in the same order as they were produced. |
To ensure system integrity, consumers must be capable of handling duplicate messages gracefully. Effective deduplication is not just an optimization — it’s a core architectural requirement, turning the challenge of message redeliveries into a structured and predictable system behavior.

There is no one-size-fits-all solution to this challenge. Usually it’s a mix of business requirements and possible technical tricks in a given context.

### <a href="about:blank#_idempotent_updates"></a> Idempotent updates

The most common approach to deduplication is to make the processing of messages idempotent. An idempotent operation is one that can be applied multiple times without changing the result beyond the initial application. This means that if the same message is processed multiple times, the result will be the same as if it were processed only once.

To demonstrate this, let’s consider a simple example of a `CustomerStore` that persist customer data outside Akka ecosystem.

[CustomerStore.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/application/CustomerStore.java)
```java
public class CustomerStore {

  public CompletionStage<Optional<Customer>> getById(String customerId) {
  }

  public CompletionStage<Done> save(String customerId, Customer customer) {
  }
}
```
A consumer implementation that updates such a store is written in an idempotent way.

[CustomerStoreUpdater.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/application/CustomerStoreUpdater.java)
```java
@ComponentId("customer-store-updater")
@Consume.FromEventSourcedEntity(CustomerEntity.class)
public class CustomerStoreUpdater extends Consumer {

  private final CustomerStore customerStore;

  public CustomerStoreUpdater(CustomerStore customerStore) {
    this.customerStore = customerStore;
  }

  public Effect onEffect(CustomerEvent event) { // (1)
    var customerId = messageContext().eventSubject().get();
    return switch (event) {
      case CustomerCreated created ->
        effects().asyncDone(customerStore.save(customerId, new Customer(created.email(), created.name(), created.address())));

      case NameChanged nameChanged ->
        effects().asyncDone(customerStore.getById(customerId).thenCompose(customer -> {
          if (customer.isPresent()) {
            return customerStore.save(customerId, customer.get().withName(nameChanged.newName()));
          } else {
            throw new IllegalStateException("Customer not found: " + customerId);
          }
        }));

      case AddressChanged addressChanged ->
        effects().asyncDone(customerStore.getById(customerId).thenCompose(customer -> {
          if (customer.isPresent()) {
            return customerStore.save(customerId, customer.get().withAddress(addressChanged.address()));
          } else {
            throw new IllegalStateException("Customer not found: " + customerId);
          }
        }));
    };
  }
}
```

| **1** | Processing each event is idempotent. Duplicated events will not change the state of the store. |
Remember to test your idempotent operations. The [CustomerStoreUpdaterTest](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/test/java/customer/application/CustomerStoreUpdaterTest.java) demonstrates how it can be done with the `EventingTestKit`.

|  | Consumers, Views, Workflows and Entities are single writers for a given entity id. There are no concurrent updates. Messages (events or commands) are processed sequentially by a single instance for the entity id. Therefore, there is no need for things like optimistic locking. |
**Key Considerations**

- Ensure that operations like database inserts or state changes are idempotent.
- Evaluate the trade-off between complexity and storage requirements for maintaining idempotency.

### <a href="about:blank#events-enrichment"></a> Events enrichment

Some updates are inherently not idempotent. A good example might be calculating and storing some value in a View based on the series of events. Processing a single event twice will corrupt the result.

[CounterByValueView.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/application/CounterByValueView.java)
```java
@ComponentId("counter-by-value")
public class CounterByValueView extends View {

  public record CounterByValue(String name, int value) {
  }

  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class CounterByValueUpdater extends TableUpdater<CounterByValue> {
    public Effect<CounterByValue> onEvent(CounterEvent counterEvent) {
      var name = updateContext().eventSubject().get();
      var currentRow = rowState();
      var currentValue = Optional.ofNullable(currentRow).map(CounterByValue::value).orElse// (0);
      return switch (counterEvent) {
        case ValueIncreased increased -> effects().updateRow(
          new CounterByValue(name, currentValue + increased.value())); // (1)
        case ValueMultiplied multiplied -> effects().updateRow(
          new CounterByValue(name, currentValue * multiplied.multiplier())); // (2)
      };
    }
  }
}
```

| **1** | Handling `ValueIncreased` is not idempotent. |
| **2** | Handling `ValueMultiplied` is not idempotent. |
In such cases we can use a technique called *events enrichment*. The idea is to keep in the event not only a delta information but also other pre-calculated values that are (or will be) necessary for down stream consumers.

Events modelling is a key part of the system design. A consumer that have all the necessary information in the event can be more independent and less error-prone. Of course a balance must be found between the size of the event and simplicity of its processing.

[CounterEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/domain/CounterEvent.java)
```java
public sealed interface CounterEvent {

  record ValueMultiplied(int multiplier,
                         int updatedValue) // (1)
    implements CounterEvent {
  }
}
```

| **1** | `ValueMultiplied` event contains not only delta information under `multiplier` field but also pre-calculated `updatedValue` of the counter. |
The updated version of the `CounterByValueUpdater` can be again idempotent.

[CounterByValueViewEnrichment.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/application/CounterByValueViewEnrichment.java)
```java
@Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class CounterByValueUpdater extends TableUpdater<CounterByValueEntry> {
    public Effect<CounterByValueEntry> onEvent(CounterEvent counterEvent) {
      var name = updateContext().eventSubject().get();
      return switch (counterEvent) {
        case ValueIncreased increased -> effects().updateRow(
          new CounterByValueEntry(name, increased.updatedValue())); // (1)
        case ValueMultiplied multiplied -> effects().updateRow(
          new CounterByValueEntry(name, multiplied.updatedValue())); // (1)
      };
    }
  }
}
```

| **1** | Using pre-calculated `currentValue` from the event. |

|  | Overloading event payloads with excessive data, such as embedding entire entity state, can lead to bloated events, increased storage costs, and unnecessary data duplication. Instead, events should carry just enough context to maintaining a balance between enrichment and efficiency. |
**Benefits**

- Enables idempotent processing of enriched events.
- Reduces coupling between producers and consumers.
**Challenges**

- Increases event size, requiring a balance between richness and efficiency.
- Requires careful schema design and potential for schema evolution challenges

### <a href="about:blank#_sequence_number_tracking"></a> Sequence number tracking

For cases when events enrichment is not possible or not desired, a sequence number tracking can be used. The idea is to keep track of the sequence number of the last processed event and ignore any events with a sequence number lower or equal than the last processed one.

|  | A monotonically increased sequence number is available only when consuming updates from Akka components like Event Sourced Entity, Key Value Entity, or another Akka service. The sequence number is **not globally unique**, but unique per entity instance. |

|  | Akka View component has a built-in support for sequence number tracking. [CounterByValueViewTest](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/test/java/counter/application/CounterByValueViewTest.java) demonstrates how it can be verified. |
Let’s assume that we want to populate a view storage outside Akka ecosystem. To focus on the deduplication aspect, the following snippet shows the in-memory implementation of the `CounterStore`.

[CounterStore.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/application/CounterStore.java)
```java
public class CounterStore{

  public record CounterEntry(String counterId, int value, long seqNum) { // (1)
  }

  private Map<String, CounterEntry> store = new ConcurrentHashMap<>();


  public CompletionStage<Optional<CounterEntry>> getById(String counterId) {
    return completedFuture(Optional.ofNullable(store.get(counterId)));
  }

  public CompletionStage<Done> save(CounterEntry counterEntry) {
    return completedFuture(store.put(counterEntry.counterId(), counterEntry)).thenApply(__ -> done());
  }

  public CompletionStage<Collection<CounterEntry>> getAll() {
    return completedFuture(store.values());
  }
}
```

| **1** | A read model keeps track of the last processed sequence number. |
The Consumer component, uses sequence number for tracking deduplicated events.

[CounterStoreUpdater.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/application/CounterStoreUpdater.java)
```java
@ComponentId("counter-store-updater")
@Consume.FromEventSourcedEntity(CounterEntity.class)
public class CounterStoreUpdater extends Consumer {

  private final CounterStore counterStore;

  public Effect onEvent(CounterEvent counterEvent) {
    var counterId = messageContext().eventSubject().get();
    var newSeqNum = messageContext().metadata().asCloudEvent().sequence();

    return effects().asyncEffect(
      counterStore.getById(counterId) // (1)
        .thenApply(counterEntry -> {
          var currentSeqNum = counterEntry.map(CounterEntry::seqNum).orElse(0L);
          if (!newSeqNum.isPresent()) { // (2)
            // missing sequence number, can't deduplicate
            return processEvent(counterEvent, counterEntry, 0L);
          } else {
            if (newSeqNum.get() <= currentSeqNum) {
              //duplicate, can be ignored
              return effects().ignore(); // (3)
            } else {
              // not a duplicate
              return processEvent(counterEvent, counterEntry, newSeqNum.get()); // (4)
            }
          }
        })
    );
  }

  private Effect processEvent(CounterEvent counterEvent, Optional<CounterEntry> currentEntry, Long seqNum) {
    var counterId = messageContext().eventSubject().get();
    var currentValue = currentEntry.map(CounterEntry::value).orElse// (0);
    return switch (counterEvent) {
      case ValueIncreased increased -> {
        var updatedEntry = new CounterEntry(counterId, currentValue + increased.value(), seqNum);
        yield effects().asyncDone(counterStore.save(updatedEntry)); // (5)
      }
      case ValueMultiplied multiplied -> {
        var updatedEntry = new CounterEntry(counterId, currentValue * multiplied.multiplier(), seqNum);
        yield effects().asyncDone(counterStore.save(updatedEntry)); // (5)
      }
    };
  }
}
```

| **1** | Loads the existing entry for a given entity ID. |
| **2** | When sequence number is not available deduplication is disabled. |
| **3** | When sequence number is lower or equal to the last processed one, the event is ignored. |
| **4** | Otherwise, the event is processed and the last processed sequence number is updated. |
| **5** | Updates are not idempotent, but the deduplication mechanism ensures that the view is correct in case of processing duplicates. |
Keep in mind that the `CounterEntry` corresponds to a single entity instance, that’s why we can use the sequence number as a deduplication token.

It’s important to test your deduplication mechanism. The [CounterStoreUpdaterTest](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/test/java/counter/application/CounterStoreUpdaterTest.java) demonstrates how it can be done with the `EventingTestKit`.

**Benefits**

- Tracking sequence numbers is very effective and the additional storage overhead is minimal.
**Challenges**

- Only works per entity instance, can’t be used globally.

### <a href="about:blank#_deterministic_hashing"></a> Deterministic hashing

When calling external system or other Akka components/services from the Akka Consumer perspective, deduplication might require to send the same token for the same request. Based on that token, the receiver can deduplicate the request. The potential candidate for such a token might be the sequence number of the event. Unfortunately, the sequence number is not globally unique, so the same token might be used for requests based on processing events from two different entity instances.

To solve this problem a technique called *deterministic hashing* can be used. The idea is to use a deterministic hash of the event data to generate stable and unique deduplication tokens. The hash might be calculated from the event payload, but very often payloads themselves are not globally unique and might be expensive to hash. The minimal set of fields that uniquely identify the event are subject (entity ID) and sequence number from the metadata.

```java
public Effect handle(Event event) {
  var entityId = messageContext().eventSubject().get();
  var sequenceNumber = messageContext().metadata().asCloudEvent().sequence().get();
  var token = UUID.nameUUIDFromBytes((entityId + sequenceNumber).getBytes()); // (1)
  someService.doSomething(event, token.toString());
  return effects().done();
}
```

| **1** | Deduplication token is calculated from the entity ID and the sequence number. |
The token might be also [precalculated](about:blank#events-enrichment) and stored in the event payload. In such case the consumer can use it directly.

**Benefits**

- Useful for cross-service or cross-component communication.
**Challenges**

- Choosing the right hashing algorithm (e.g., SHA-256 vs. MD5) for a balance between collision resistance and performance.
- The receiver must be able to deduplicate based on the token, which, in most cases, has same limitations. See [request deduplication](about:blank#request-deduplication).

## <a href="about:blank#request-deduplication"></a> Request deduplication

A different aspect of deduplication is how to deal with, possibly duplicated, incoming commands that mutate Akka stateful components. Let’s examine this based on a `WalletEntity` example.

[WalletEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow-compensation/src/main/java/com/example/wallet/application/WalletEntity.java)
```java
@ComponentId("wallet")
public class WalletEntity extends EventSourcedEntity<Wallet, WalletEvent> {

  public Effect<WalletResult> deposit(Deposit deposit) { // (1)
    if (currentState().isEmpty()){
      return effects().error("Wallet does not exist");
    } else {
      List<WalletEvent> events = currentState().handle(deposit);
      return effects().persistAll(events)
          .thenReply(__ -> new WalletResult.Success());
    }
  }
}
```

| **1** | Processing the same `Deposit` command twice will corrupt the wallet state. |
To secure the entity from processing the same command multiple times, we must start with extending the command model with deduplication token, called `commandId` in our case.

[WalletCommand.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow-compensation/src/main/java/com/example/wallet/domain/WalletCommand.java)
```java
public sealed interface WalletCommand {

  String commandId();

  record Withdraw(String commandId, int amount) implements WalletCommand { // (1)
  }
  record Deposit(String commandId, int amount) implements WalletCommand { // (1)
  }
}
```

| **1** | All commands that require deduplication have `commandId` field. |
The information about already processed commands must be stored in the entity state. The simplest way is to keep a collection of processed command IDs.

[Wallet.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow-compensation/src/main/java/com/example/wallet/domain/Wallet.java)
```java
public record Wallet(String id, int balance, LinkedHashSet<String> commandIds) { // (1)

  public static final int COMMAND_IDS_MAX_SIZE = 1000;

  public List<WalletEvent> handle(WalletCommand command) {
    if (commandIds.contains(command.commandId())) { // (2)
      logger.info("Command already processed: [{}]", command.commandId());
      return List.of();
    }
    return switch (command) {
      case WalletCommand.Deposit deposit ->
        List.of(new WalletEvent.Deposited(command.commandId(), deposit.amount())); // (3)
      case WalletCommand.Withdraw withdraw ->
        List.of(new WalletEvent.Withdrawn(command.commandId(), withdraw.amount())); // (3)
    };
  }
  public Wallet applyEvent(WalletEvent event) {
    return switch (event) {
      case WalletEvent.Created created ->
        new Wallet(created.walletId(), created.initialBalance(), new LinkedHashSet<>());
      case WalletEvent.Withdrawn withdrawn ->
        new Wallet(id, balance - withdrawn.amount(), addCommandId(withdrawn.commandId()));
      case WalletEvent.Deposited deposited ->
        new Wallet(id, balance + deposited.amount(), addCommandId(deposited.commandId()));
    };
  }

  private LinkedHashSet<String> addCommandId(String commandId) {
    if (commandIds.size() >= COMMAND_IDS_MAX_SIZE) { // (4)
      commandIds.removeFirst();
    }
    commandIds.add(commandId);
    return commandIds;
  }
}
```

| **1** | List of processed command IDs. |
| **2** | Before we process the command we check if it was already processed. |
| **3** | To rebuild the state we need to store the command ID in the event. |
| **4** | To keep the collection size constrained we can remove old command IDs. |
This simple solution reveals a few important limitations of the deduplication that are common across many distributed technologies. It’s very expensive (and often not possible) to have a total deduplication of all incoming requests/commands. There will always be some constraints like:

- the size of the collection, e.g. keep only last 1000 command IDs, like in the example above,
- the time window, e.g. keep only command IDs from the last 24 hours,
- both combined, e.g. keep only command IDs from the last 24 hours, but not more than 1000.
Production ready deduplication should take into account these limitations in the context of the expected load. Also, using `java.util.List` should be evaluated against more efficient data structures.

Keep in mind that using collection types not supported by the Jackson serialization will require a custom serialization for that field. See `@JsonSerialize` annotation for more details.

**Benefits**

- Solution doesn’t require additional infrastructure to store already processed command IDs.
**Challenges**

- Memory and storage requirements for keeping command IDs.
- Performance consideration for effective data structure for command IDs.
- Custom serialization for non-standard collection types.

<!-- <footer> -->
<!-- <nav> -->
[Run a service locally](running-locally.html) [Using an AI coding assistant](ai-coding-assistant.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->