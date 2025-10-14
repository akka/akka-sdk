<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Components](components/index.html)
- [Views](views.html)

<!-- </nav> -->

# Implementing Views

![View](../_images/view.png)
Views allow you to access multiple entities or retrieve entities by attributes other than their *entity id*. You can create Views for different access patterns, optimized by specific queries, or combine multiple queries into a single View.

Views can be defined from any of the following:

- [Key Value Entity state changes](about:blank#value-entity)
- [Event Sourced Entity events](about:blank#event-sourced-entity)
- [Workflow state changes](about:blank#workflow)
- [Messages received from subscribing to topics on a broker](about:blank#topic-view)
- [Events consumed from a different Akka service](consuming-producing.html#s2s-eventing)
Reference documentation covering the view query language syntax, query capabilities and how query results are mapped to Java types can be found in [View reference](../reference/views/index.html).

The remainder of this page describes:

- [How to transform results](about:blank#results-projection)
- [How to modify a View](about:blank#changing)

|  | Be aware that Views are not updated immediately when the Entity state changes. It is not instant but eventually all changes will become visible in the query results. View updates might also take more time during failure scenarios (e.g. network instability) than during normal operation. |

## <a href="about:blank#_effect_api"></a> View’s Effect API

The View’s Effect defines the operations to be performed when an event, a message or a state change is handled by a View.

A View Effect can either:

- update the view state
- delete the view state
- ignore the event or state change notification (and not update the view state)
For additional details, refer to [Declarative Effects](../concepts/declarative-effects.html).

## <a href="about:blank#value-entity"></a> Creating a View from a Key Value Entity

Consider an example of a Customer Registry service with a `Customer` Key Value Entity. When customer state changes, the entire state is emitted as a value change. Those value changes update any associated Views.
To create a View that lists customers by their name, [define the view](about:blank#_define_the_view) for a service that selects customers by name and associates a table name with the View. The table is created and used to store the View.

This example assumes the following `Customer` exists:

[Customer.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/domain/Customer.java)
```java
public record Customer(String email, String name, Address address) { // (1)
  public Customer withName(String newName) { // (2)
    return new Customer(email, newName, address);
  }

  public Customer withAddress(Address newAddress) { // (2)
    return new Customer(email, name, newAddress);
  }
}
```
As well as a Key Value Entity component `CustomerEntity.java` that will produce the state changes consumed by the View. You can consult [Key Value Entity](key-value-entities.html#entity-behavior) documentation on how to create such an entity if needed.

### <a href="about:blank#_define_the_view"></a> Define the View

You implement a View by extending `akka.javasdk.view.View` and subscribing to changes from an entity. You specify how to query it by providing one or more methods annotated with `@Query`, which can then be made accessible via an [HTTP Endpoint](http-endpoints.html).

[CustomersByEmail.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByEmail.java)
```java
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.Customer;
import java.util.List;

@Component(id = "customers-by-email") // (1)
public class CustomersByEmail extends View { // (2)

  public record Customers(List<Customer> customers) {}

  @Consume.FromKeyValueEntity(CustomerEntity.class) // (3)
  public static class CustomersByEmailUpdater extends TableUpdater<Customer> {} // (4)

  @Query("SELECT * AS customers FROM customers_by_email WHERE email = :email") // (5)
  public QueryEffect<Customers> getCustomer(String email) {
    return queryResult(); // (6)
  }
}
```

| **1** | Define a component id for the view. |
| **2** | Extend from `View`. |
| **3** | Subscribe to updates from Key Value Entity `CustomerEntity`. |
| **4** | Declare a `TableUpdater` of type `Customer` (entity’s state type). |
| **5** | Define the query, including a table name (i.e. `customers_by_email`) of our choice. |
| **6** | Use method `queryResult()` to return the result of the query. |

|  | Assigning a component identifier (i.e. `@Component`) to your View is mandatory, it must be unique, and it should be stable. This allows you to refactor the name of the class later on without the risk of losing the view. If you change this identifier later, Akka will not recognize this component as the same view and will create a brand-new view. For a view consuming from an Event Sourced Entity this becomes very resource consuming because it will reprocess all the events of that entity to rebuild it. While for a view built from a topic, you can lose all the previous events because, depending on the topic configuration, you may only process events from the current time forwards. Last but not least, it’s also a problem for Key Value Entities because it will need to index them again when grouping them by some value. |

### <a href="about:blank#_using_a_transformed_model"></a> Using a transformed model

Often, you will want to transform the entity model to which the view is subscribing into a different representation. To do that, let’s have a look at the example in which we store a summary of the `Customer` used in the previous section instead of the original one:

[CustomersByName.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByName.java)
```java
public record CustomerSummary(String customerId, String name, String email) {}
```
In this scenario, the view state should be of type `CustomerSummary` and you will need to handle and transform the incoming state changes into it, as shown below:

[CustomersByName.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByName.java)
```java
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.Customer;
import java.util.Collection;

@Component(id = "customers-by-name")
public class CustomersByName extends View {

  public record CustomerSummary(String customerId, String name, String email) {}


  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class CustomersByNameUpdater extends TableUpdater<CustomerSummary> { // (1)

    public Effect<CustomerSummary> onUpdate(Customer customer) { // (2)
      return effects()
        .updateRow(
          new CustomerSummary(
            updateContext().eventSubject().get(),
            customer.name(),
            customer.email()
          )
        ); // (3)
    }
  }

  @Query("SELECT * FROM customers_by_name WHERE name = :name") // (4)
  public QueryEffect<CustomerSummary> getFirstCustomerSummary(String name) { // (5)
    return queryResult();
  }

}
```

| **1** | Declares a `TableUpdater` of type `CustomerSummary`. This type represents each stored row. |
| **2** | Implements a handler method `onUpdate` that receives the latest state of the entity `Customer` and returns an `Effect` with the updated row. |
| **3** | The id of the entity that was updated is available through the update context as `eventSubject`. |
| **4** | Defines the query. |
| **5** | Uses the new type `CustomerSummary` to return the result of the query. |

|  | Some `TableUpdater` implementation might update the view model in a non-idempotent way. For example, the view model adds an element to the list. When the source of the changes is an Event Sourced Entity, Key Value Entity or another Akka service, the View component has a build-in deduplication mechanism to ensure that the same event is not processed twice. In other cases, you should add the deduplication mechanism in the `TableUpdater` implementation. See [message deduplication](dev-best-practices.html#message-deduplication) for some suggested solutions. |

### <a href="about:blank#ve_delete"></a> Handling Key Value Entity deletes

The View state corresponding to an Entity is not automatically deleted when the Entity is deleted.

We can update our table updater with an additional handler marked with `@DeleteHandler`, to handle a Key Value Entity [delete](key-value-entities.html#deleting-state) operation.

[CustomerSummaryByName.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomerSummaryByName.java)
```java
@Consume.FromKeyValueEntity(value = CustomerEntity.class)
public static class CustomersUpdater extends TableUpdater<CustomerSummary> { // (1)

  public Effect<CustomerSummary> onUpdate(Customer customer) {
    return effects()
      .updateRow(
        new CustomerSummary(updateContext().eventSubject().get(), customer.name())
      );
  }

  // ...
  @DeleteHandler // (2)
  public Effect<CustomerSummary> onDelete() {
    return effects().deleteRow(); // (3)
  }
}
```

| **1** | Note we are adding a new handler to the existing table updater. |
| **2** | Marks the method as a delete handler. |
| **3** | An effect to delete the view row `effects().deleteRow()`. It could also be an update of a special column, to mark the view row as deleted. |

## <a href="about:blank#event-sourced-entity"></a> Creating a View from an Event Sourced Entity

You can create a View from an Event Sourced Entity by using events that the Entity emits to build a state representation.

Using our Customer Registry service example, to create a View for querying customers by name,
you have to [define the view to consume events](about:blank#_define_the_view_to_consume_events).

This example assumes a Customer equal to the previous example and an Event Sourced Entity that uses this Customer. The Event Sourced Entity is in charge of producing the events that update the View. These events are defined as subtypes of the class `CustomerEvent` using a sealed interface:

[CustomerEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/domain/CustomerEvent.java)
```java
import akka.javasdk.annotations.Migration;
import akka.javasdk.annotations.TypeName;

public sealed interface CustomerEvent {
  @TypeName("internal-customer-created") // (1)
  record CustomerCreated(String email, String name, Address address)
    implements CustomerEvent {}


  @TypeName("internal-name-changed")
  record NameChanged(String newName) implements CustomerEvent {}


  @TypeName("internal-address-changed")
  record AddressChanged(Address address) implements CustomerEvent {}
}
```

| **1** | Includes the logical type name using `@TypeName` annotation. |

|  | It’s highly recommended to add a `@TypeName` to your persisted events. Akka needs to identify each event in order to deliver them to the right event handlers. If no logical type name is specified, Akka uses the FQCN, check [type name](serialization.html#_type_name) documentation for more details. |

### <a href="about:blank#_define_the_view_to_consume_events"></a> Define the View to consume events

Defining a view that consumes from an Event Sourced Entity is very similar to the one consuming a Key Value Entity. In this case, the handler method will be called for each event emitted by the Entity.

Every time an event is processed by the view, the state of the view can be updated. You can do this with the `updateRow` method, which is available through the `effects()` API. Below you can see how the View is updated:

[CustomersByNameView.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/application/CustomersByNameView.java)
```java
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.CustomerEntries;
import customer.domain.CustomerEntry;
import customer.domain.CustomerEvent;

@Component(id = "customers-by-name") // (1)
public class CustomersByNameView extends View {

  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class CustomersByNameUpdater extends TableUpdater<CustomerEntry> { // (2)

    public Effect<CustomerEntry> onEvent(CustomerEvent event) { // (3)
      return switch (event) {
        case CustomerEvent.CustomerCreated created -> effects()
          .updateRow(new CustomerEntry(created.email(), created.name(), created.address()));
        case CustomerEvent.NameChanged nameChanged -> effects()
          .updateRow(rowState().withName(nameChanged.newName()));
        case CustomerEvent.AddressChanged addressChanged -> effects()
          .updateRow(rowState().withAddress(addressChanged.address()));
      };
    }
  }

  @Query("SELECT * as customers FROM customers_by_name WHERE name = :name")
  public QueryEffect<CustomerEntries> getCustomers(String name) {
    return queryResult();
  }
}
```

| **1** | Defines a component id for the view. |
| **2** | Declares a `TableUpdater` of type `CustomerRow`. |
| **3** | Handles the super type `CustomerEvent` and defines the proper update row method for each subtype. |

### <a href="about:blank#_ignoring_events"></a> Ignoring events

You can ignore events by returning `Effect.ignore` for those you are not interested in. Using a `sealed interface` for the events is a good practice to ensure that all events types are handled.

### <a href="about:blank#es_delete"></a> Handling Event Sourced Entity deletes

The View row corresponding to an Entity is not automatically deleted when the Entity is deleted.

To delete from the View you can use the `deleteRow()` effect from an event transformation method, similarly to the example shown above for a Key Value Entity.

## <a href="about:blank#workflow"></a> Creating a View from a Workflow

The source of a View can be also a Workflow state changes. It works the same way as shown in [Creating a View from an Event Sourced Entity](about:blank#event-sourced-entity) or [Creating a View from a Key Value Entity](about:blank#value-entity), but you define it with `@Consume.FromWorkflow` instead.

[TransfersView.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow/src/main/java/com/example/transfer/application/TransfersView.java)
```java
@Component(id = "transfer-view")
public class TransfersView extends View {

  public record TransferEntry(String id, String status) {}

  public record TransferEntries(Collection<TransferEntry> entries) {}

  @Query("SELECT * as entries FROM transfers WHERE status = 'COMPLETED'")
  public QueryEffect<TransferEntries> getAllCompleted() {
    return queryResult();
  }

  @Consume.FromWorkflow(TransferWorkflow.class) // (1)
  public static class TransfersUpdater extends TableUpdater<TransferEntry> {

    public Effect<TransferEntry> onUpdate(TransferState transferState) { // (2)
      var id = updateContext().eventSubject().orElse("");
      return effects().updateRow(new TransferEntry(id, transferState.status().name()));
    }
  }
}
```

| **1** | Uses `@Consume.FromWorkflow` annotation to set the source Workflow. |
| **2** | Transforms the Workflow state `TransferState` into a View `TransferEntry`. |

## <a href="about:blank#topic-view"></a> Creating a View from a topic

The source of a View can be a topic. It works the same way as shown in [Creating a View from an Event Sourced Entity](about:blank#event-sourced-entity) or [Creating a View from a Key Value Entity](about:blank#value-entity), but you define it with `@Consume.FromTopic` instead.

|  | For the messages to be correctly consumed in the view, there must be a `ce-subject` metadata associated with each message. This is required because for each message consumed from the topic there will be a corresponding view row. That view row is selected based on such `ce-subject`. For an example on how to pass such metadata when producing to a topic, see page [Metadata](consuming-producing.html#_metadata). |
[CounterTopicView.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/application/CounterTopicView.java)
```java
@Component(id = "counter-topic-view")
public class CounterTopicView extends View {

  private static final Logger logger = LoggerFactory.getLogger(CounterTopicView.class);

  public record CounterRow(String counterId, int value, Instant lastChange) {}

  public record CountersResult(List<CounterRow> foundCounters) {}

  @Consume.FromTopic("counter-events-with-meta") // (1)
  public static class CounterUpdater extends TableUpdater<CounterRow> {

    public Effect<CounterRow> onEvent(CounterEvent event) {
      String counterId = updateContext().metadata().asCloudEvent().subject().get(); // (2)
      var newValue =
        switch (event) {
          case ValueIncreased increased -> increased.updatedValue();
          case ValueMultiplied multiplied -> multiplied.updatedValue();
        };
      logger.info("Received new value for counter id {}: {}", counterId, event);

      return effects().updateRow(new CounterRow(counterId, newValue, Instant.now())); // (3)
    }
  }

  @Query("SELECT * AS foundCounters FROM counters WHERE value >= :minimum")
  public View.QueryEffect<CountersResult> countersHigherThan(int minimum) {
    return queryResult();
  }
}
```

| **1** | Uses `@Consume.FromTopic` annotation to set the target topic. |
| **2** | Extracts the `ce-subject` attribute from the topic event metadata to include in the view row. |
| **3** | Returns an updating effect with new table row state. |

## <a href="about:blank#_view_query_results"></a> View query results

### <a href="about:blank#results-projection"></a> How to transform results

When creating a View, you can transform the results as a projection for constructing a new type instead of returning the
view row type directly, for details see [Result Mapping](../reference/views/concepts/result-mapping.html)

### <a href="about:blank#_streaming_the_result"></a> Streaming the result

Instead of collecting the query result in memory as a collection before returning it, the entries can be streamed.
To return the result as a stream, modify the returned type to be `QueryStreamEffect` and use `queryStreamResult()` to return the stream.

[CustomersByCity.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByCity.java)
```java
@Query(value = "SELECT * FROM customers_by_city WHERE address.city = :city")
public QueryStreamEffect<Customer> streamCustomersInCity(String city) {
  return queryStreamResult();
}
```

### <a href="about:blank#_streaming_view_updates"></a> Streaming view updates

A query can provide a near real-time stream of results for the query, emitting new entries matching the query as they are added or updated in
the view.

This will first list the complete result for the query and then keep the response stream open, emitting new or updated
entries matching the query as they are added to the view. The stream does not complete until the client closes it.

To use streaming updates, add `streamUpdates = true` to the `Query` annotation. The returned type of the
query method must be `QueryStreamEffect`.

[CustomersByCity.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByCity.java)
```java
@Query(
  value = "SELECT * FROM customers_by_city WHERE address.city = :city",
  streamUpdates = true
)
public QueryStreamEffect<Customer> continuousCustomersInCity(String city) {
  return queryStreamResult();
}
```
This example would return the customers living in the same city, and then emit every time a customer
already in the city is changed, or when a new customer is added to the view with the given city.

Streaming updates can be streamed all the way to a gRPC or HTTP client via a [gRPC Endpoint](grpc-endpoints.html) or an [HTTP
endpoint using SSE](http-endpoints.html#sse).

|  | This is not intended as transport for [service to service](consuming-producing.html#s2s-eventing) propagation of updates, and it does not guarantee delivery. For such use cases you
should instead publish events to a topic, see [Consuming and producing](consuming-producing.html) |

## <a href="about:blank#changing"></a> How to modify a View

Akka creates indexes for the View based on the queries. For example, the following query will result in a View with an index on the `name` column:

```sql
SELECT * FROM customers WHERE name = :customer_name
```
You may realize after a deployment that you forgot adding some parameters to the query parameters that aren’t exposed to the endpoint of the View. After adding these parameters the query is changed and therefore Akka will add indexes for these new columns. For example, changing the above query to filter by active users would mean a new index on the `is-active` column. This is handled automatically behind the scenes.

```sql
SELECT * FROM customers WHERE name = :customer_name AND is-active = true
```

### <a href="about:blank#_incompatible_changes"></a> Incompatible changes

Some specific scenarios might require a complete rebuild of the View, for example:

- adding or removing tables for multi-table views;
- changing the data type of a column that is part of an index.
Such changes require you to define a new View. Akka will then rebuild it from the source event log or value changes.

|  | You should be able to test if a change is compatible locally by running the service with [persistence mode enabled](running-locally.html#persistence-enabled), producing some data, and then changing the View query and re-running the service. If the service boots up correctly and is able to serve the new query, the change is compatible. |
Rebuilding a new View may take some time if there are many events that have to be processed. The recommended way when changing a View is multi-step, with two deployments:

1. Define the new View with a new `@Component`, and keep the old View intact.
2. Deploy the new View, and let it rebuild. Verify that the new query works as expected. The old View can still be used.
3. Remove the old View and redirect the endpoint calls to the new View.
4. Deploy the second change.
The View definitions are stored and validated when a new version is deployed. There will be an error message if the changes are not compatible.

|  | Views from topics cannot be rebuilt from the source messages, because it might not be possible to consume all events from the topic again. The new View is built from new messages published to the topic. |

## <a href="about:blank#_testing_the_view"></a> Testing the View

Testing Views is very similar to testing other [subscription integrations](consuming-producing.html#_testkit_mocked_incoming_messages).

For a View definition that subscribes to changes from the `customer` Key Value Entity.

[CustomersByCity.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByCity.java)
```java
public class CustomersByCity extends View {

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class CustomerUpdater extends TableUpdater<Customer> {}

  @Query(
    """
    SELECT * AS customers
        FROM customers_by_city
      WHERE address.city = ANY(:cities)
    """
  )
  public QueryEffect<CustomerList> getCustomers(List<String> cities) {
    return queryResult();
  }

  @Query(value = "SELECT * FROM customers_by_city WHERE address.city = :city")
  public QueryStreamEffect<Customer> streamCustomersInCity(String city) {
    return queryStreamResult();
  }


  @Query(
    value = "SELECT * FROM customers_by_city WHERE address.city = :city",
    streamUpdates = true
  )
  public QueryStreamEffect<Customer> continuousCustomersInCity(String city) {
    return queryStreamResult();
  }


  public record QueryParams(String customerName, String city) {} // (1)

  @Query(
    """
    SELECT * FROM customers_by_city
    WHERE name = :customerName AND address.city = :city"""
  ) // (2)
  public QueryEffect<Customer> getCustomersByCityAndName(QueryParams queryParams) {
    return queryResult();
  }

}
```
An integration test can be implemented as below.

[CustomersByCityIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/test/java/customer/application/CustomersByCityIntegrationTest.java)
```java
class CustomersByCityIntegrationTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() { // (1)
    return TestKit.Settings.DEFAULT.withKeyValueEntityIncomingMessages(CustomerEntity.class);
  }

  @Test
  public void shouldGetCustomerByCity() {
    IncomingMessages customerEvents = // (2)
      testKit.getKeyValueEntityIncomingMessages(CustomerEntity.class);

    Customer johanna = new Customer(
      "johanna@example.com",
      "Johanna",
      new Address("Cool Street", "Porto")
    );
    Customer bob = new Customer(
      "boc@example.com",
      "Bob",
      new Address("Baker Street", "London")
    );
    Customer alice = new Customer(
      "alice@example.com",
      "Alice",
      new Address("Long Street", "Wroclaw")
    );

    customerEvents.publish(johanna, "1"); // (3)
    customerEvents.publish(bob, "2");
    customerEvents.publish(alice, "3");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        CustomerList customersResponse = componentClient
          .forView()
          .method(CustomersByCity::getCustomers) // (4)
          .invoke(List.of("Porto", "London"));

        assertThat(customersResponse.customers()).containsOnly(johanna, bob);
      });
  }

}
```

| **1** | Mocks incoming messages from the `customer` Key Value Entity. |
| **2** | Gets an `IncomingMessages` from the `CustomerEntity`. |
| **3** | Publishes test data. |
| **4** | Queries the view and asserts the results. |

## <a href="about:blank#_multi_region_replication"></a> Multi-region replication

Views are not replicated directly in the same way as for example [Event Sourced Entity replication](event-sourced-entities.html#_replication). A View is built from entities in the same service, or another service, in the same region. The entities will replicate all events across regions and identical Views are built in each region.

The origin of an event is the region where a message was first created. You can see the origin from `updateContext().hasLocalOrigin()` or `updateContext().originRegion()` and perform conditional processing of the event depending on the origin, such as ignoring events from other regions than the local region where the View is running. The local region can be retrieved with `messageContext().selfRegion()`.

A View can also be built from a message broker topic, and that could be regional or global depending on how the message broker is configured.

<!-- <footer> -->
<!-- <nav> -->
[MCP Endpoints](mcp-endpoints.html) [Workflows](workflows.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->