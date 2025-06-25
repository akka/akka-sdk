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
The remainder of this page describes:

- [How to transform results](about:blank#results-projection)
- [How to modify a View](about:blank#changing)
- [Query syntax reference](about:blank#query)

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
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.Customer;

import java.util.List;

@ComponentId("customers-by-email") // (1)
public class CustomersByEmail extends View { // (2)

  public record Customers(List<Customer> customers) { }

  @Consume.FromKeyValueEntity(CustomerEntity.class) // (3)
  public static class CustomersByEmailUpdater extends TableUpdater<Customer> { } // (4)

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

|  | Assigning a component identifier (i.e. `@ComponentId`) to your View is mandatory, it must be unique, and it should be stable. This allows you to refactor the name of the class later on without the risk of losing the view. If you change this identifier later, Akka will not recognize this component as the same view and will create a brand-new view. For a view consuming from an Event Sourced Entity this becomes very resource consuming because it will reprocess all the events of that entity to rebuild it. While for a view built from a topic, you can lose all the previous events because, depending on the topic configuration, you may only process events from the current time forwards. Last but not least, it’s also a problem for Key Value Entities because it will need to index them again when grouping them by some value. |

### <a href="about:blank#_using_a_transformed_model"></a> Using a transformed model

Often, you will want to transform the entity model to which the view is subscribing into a different representation. To do that, let’s have a look at the example in which we store a summary of the `Customer` used in the previous section instead of the original one:

[CustomersByName.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByName.java)
```java
public record CustomerSummary(String customerId, String name, String email) { }
```
In this scenario, the view state should be of type `CustomerSummary` and you will need to handle and transform the incoming state changes into it, as shown below:

[CustomersByName.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByName.java)
```java
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.Customer;

import java.util.Collection;

@ComponentId("customers-by-name")
public class CustomersByName extends View {

  public record CustomerSummary(String customerId, String name, String email) { }

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class CustomersByNameUpdater extends TableUpdater<CustomerSummary> { // (1)
    public Effect<CustomerSummary> onUpdate(Customer customer) { // (2)
      return effects()
          .updateRow(new CustomerSummary(updateContext().eventSubject().get(), customer.name(), customer.email())); // (3)
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
        .updateRow(new CustomerSummary(updateContext().eventSubject().get(), customer.name()));
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
  record CustomerCreated(String email, String name, Address address) implements CustomerEvent {
  }

  @TypeName("internal-name-changed")
  record NameChanged(String newName) implements CustomerEvent {
  }

  @TypeName("internal-address-changed")
  record AddressChanged(Address address) implements CustomerEvent {
  }
}
```

| **1** | Includes the logical type name using `@TypeName` annotation. |

|  | It’s highly recommended to add a `@TypeName` to your persisted events. Akka needs to identify each event in order to deliver them to the right event handlers. If no logical type name is specified, Akka uses the FQCN, check [type name](serialization.html#_type_name) documentation for more details. |

### <a href="about:blank#_define_the_view_to_consume_events"></a> Define the View to consume events

Defining a view that consumes from an Event Sourced Entity is very similar to the one consuming a Key Value Entity. In this case, the handler method will be called for each event emitted by the Entity.

Every time an event is processed by the view, the state of the view can be updated. You can do this with the `updateRow` method, which is available through the `effects()` API. Below you can see how the View is updated:

[CustomersByNameView.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/application/CustomersByNameView.java)
```java
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.CustomerEvent;
import customer.domain.CustomerEntry;
import customer.domain.CustomerEntries;

@ComponentId("customers-by-name") // (1)
public class CustomersByNameView extends View {

  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class CustomersByNameUpdater extends TableUpdater<CustomerEntry> { // (2)

    public Effect<CustomerEntry> onEvent(CustomerEvent event) { // (3)
      return switch (event) {
        case CustomerEvent.CustomerCreated created ->
            effects().updateRow(new CustomerEntry(created.email(), created.name(), created.address()));

        case CustomerEvent.NameChanged nameChanged ->
            effects().updateRow(rowState().withName(nameChanged.newName()));

        case CustomerEvent.AddressChanged addressChanged ->
            effects().updateRow(rowState().withAddress(addressChanged.address()));
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
@ComponentId("transfer-view")
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
@ComponentId("counter-topic-view")
public class CounterTopicView extends View {

  private static final Logger logger = LoggerFactory.getLogger(CounterTopicView.class);

  public record CounterRow(String counterId, int value, Instant lastChange) {}

  public record CountersResult(List<CounterRow> foundCounters) {}

  @Consume.FromTopic("counter-events-with-meta")  // (1)
  public static class CounterUpdater extends TableUpdater<CounterRow> {

    public Effect<CounterRow> onEvent(CounterEvent event) {
      String counterId = updateContext().metadata().asCloudEvent().subject().get(); // (2)
      var newValue = switch (event) {
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

## <a href="about:blank#results-projection"></a> How to transform results

When creating a View, you can transform the results as a projection for constructing a new type instead of using a `SELECT *` statement.

### <a href="about:blank#_result_projection"></a> Result projection

Instead of using `SELECT *` you can define which columns will be used in the response message. If you want to use a `CustomerSummary` used on the previous section, you will need to define your entity as this:

[CustomerSummaryByName.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomerSummaryByName.java)
```java
@Query("SELECT id, name FROM customers WHERE name = :customerName") // (1)
public QueryEffect<CustomerSummary> getCustomer(String customerName) {
  return queryResult(); // (2)
}
```

| **1** | Note the renaming from `customerId` as `id` on the query, as `id` and `name` match the record `CustomerSummary`. |
| **2** | Returns the query result. |
In a similar way, you can include values from the request in the response, for example `:requestId`:

```sql
SELECT :requestId, customerId as id, name FROM customers
WHERE name = :customerName
```

### <a href="about:blank#_multiple_results"></a> Multiple results

Oftentimes a query might be designed to return multiple results. In this case, you can either:

- Wrap the results in a `Collection` field in the response type.
- Stream the results to the client.

#### <a href="about:blank#_wrapping_results_in_a_collection"></a> Wrapping results in a Collection

To include the results in a `Collection` field in the response object, you can do as below:

[CustomerList.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomerList.java)
```java
public record CustomerList(Collection<Customer> customers) { }
```
[CustomersListByName.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersListByName.java)
```java
public class CustomersListByName extends View {

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class CustomersByNameUpdater extends TableUpdater<Customer> { } // (1)

  @Query("""
    SELECT * AS customers
      FROM customers_by_name
      WHERE name = :name
    """) // (2)
  public QueryEffect<CustomerList> getCustomers(String name) { // (3)
    return queryResult();
  }
}
```

| **1** | Table updater type is the original `Customer` as shown at the beginning of this section. |
| **2** | Note the use of `* AS customers` so records are matched to `customers` field in `CustomersList`. |
| **3** | Return type of the query is `CustomersList`. |

#### <a href="about:blank#_streaming_the_result"></a> Streaming the result

Instead of collecting the query result in memory as a collection before returning it, the entries can be streamed.
To return the result as a stream, modify the returned type to be `QueryStreamEffect` and use `queryStreamResult()` to return the stream.

[CustomersByCity.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByCity.java)
```java
@Query(value = "SELECT * FROM customers_by_city WHERE address.city = :city")
  public QueryStreamEffect<Customer> streamCustomersInCity(String city) {
    return queryStreamResult();
  }
```

#### <a href="about:blank#_streaming_view_updates"></a> Streaming view updates

A query can provide a near real-time stream of results for the query, emitting new entries matching the query as they are added or updated in
the view.

This will first list the complete result for the query and then keep the response stream open, emitting new or updated
entries matching the query as they are added to the view. The stream does not complete until the client closes it.

To use streaming updates, add `streamUpdates = true` to the `Query` annotation. The returned type of the
query method must be `QueryStreamEffect`.

[CustomersByCity.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByCity.java)
```java
@Query(value = "SELECT * FROM customers_by_city WHERE address.city = :city", streamUpdates = true)
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

|  | You should be able to test if a change is compatible locally by running the service with [persistence mode enabled](running-locally.html#_running_service_with_persistence_enabled), producing some data, and then changing the View query and re-running the service. If the service boots up correctly and is able to serve the new query, the change is compatible. |
Rebuilding a new View may take some time if there are many events that have to be processed. The recommended way when changing a View is multi-step, with two deployments:

1. Define the new View with a new `@ComponentId`, and keep the old View intact.
2. Deploy the new View, and let it rebuild. Verify that the new query works as expected. The old View can still be used.
3. Remove the old View and redirect the endpoint calls to the new View.
4. Deploy the second change.
The View definitions are stored and validated when a new version is deployed. There will be an error message if the changes are not compatible.

|  | Views from topics cannot be rebuilt from the source messages, because it might not be possible to consume all events from the topic again. The new View is built from new messages published to the topic. |

## <a href="about:blank#query"></a> Query syntax reference

Define View queries in a language that is similar to SQL. The following examples are added to illustrate the syntax.

### <a href="about:blank#_retrieving"></a> Retrieving

- All customers without any filtering conditions:

```genericsql
SELECT * FROM customers
```

### <a href="about:blank#_filter_predicates"></a> Filter predicates

Use filter predicates in `WHERE` conditions to further refine results.

- Customers with a name matching the `customerName` property of the request object:

```genericsql
SELECT * FROM customers WHERE name = :customerName
```
- Customers matching the `customerName` AND `city` properties of the request object, with `city` being matched on a nested field:

```genericsql
SELECT * FROM customers WHERE name = :customerName AND address.city = :city
```
View query methods accept only a single parameter. Passing more than one argument to your query requires to wrap them in a query record, for example:

[CustomersByCity.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByCity.java)
```java
public record QueryParams(String customerName, String city) {} // (1)

  @Query(value = "SELECT * FROM customers_by_city WHERE name = :customerName AND address.city = :city") // (2)
  public QueryEffect<Customer> getCustomersByCityAndName(QueryParams queryParams) {
    return queryResult();
  }
```

| **1** | Group query arguments in a record class. |
| **2** | Use record field names in the query syntax. |
- Customers in a city matching a literal value:

```genericsql
SELECT * FROM customers WHERE address.city = 'New York'
```

#### <a href="about:blank#_comparison_operators"></a> Comparison operators

The following comparison operators are supported:

- `=` equals
- `!=` not equals
- `>` greater than
- `>=` greater than or equals
- `<` less than
- `<=` less than or equals

#### <a href="about:blank#_logical_operators"></a> Logical operators

Combine filter conditions with the `AND` or `OR` operators, and negate using the `NOT` operator. Group conditions using parentheses.

```genericsql
SELECT * FROM customers WHERE
  name = :customer_name AND NOT (address.city = 'New York' AND age > 65)
```

#### <a href="about:blank#_array_operators"></a> Array operators

Use `IN` or `= ANY` to check whether a value is contained in a group of values or in a `List` field.

Use `IN` with a list of values or parameters:

```genericsql
SELECT * FROM customers WHERE email IN ('bob@example.com', :someEmail)
```
Use `= ANY` to check against a `List` column:

```genericsql
SELECT * FROM customers WHERE :someEmail = ANY(emails)
```
Or use `= ANY` with a `List` field in the request object:

```genericsql
SELECT * FROM customers WHERE email = ANY(:someEmails)
```

#### <a href="about:blank#_pattern_matching"></a> Pattern matching

Use `LIKE` to pattern match on strings. The standard SQL `LIKE` patterns are supported, with `_` (underscore) matching a single character, and `%` (percent sign) matching any sequence of zero or more characters.

```genericsql
SELECT * FROM customers WHERE name LIKE 'Bob%'
```

|  | For index efficiency, the pattern must have a non-wildcard prefix or suffix as used in the query above. A pattern like `'%foo%'` is not supported. Given this limitation, only constant patterns with literal strings are supported; patterns in request parameters are not allowed. |

#### <a href="about:blank#_text_search"></a> Text search

Use the `text_search` function to search text values for words, with automatic tokenization and normalization based on language-specific configuration. The `text_search` function takes the text column to search, the query (as a parameter or literal string), and an optional language configuration.

```genericsql
text_search(<column>, <query parameter or string>, [<configuration>])
```
If the query contains multiple words, the text search will find values that contain all of these words (logically combined with AND), with tokenization and normalization automatically applied.

The following text search language configurations are supported: `'danish'`, `'dutch'`, `'english'`, `'finnish'`, `'french'`, `'german'`, `'hungarian'`, `'italian'`, `'norwegian'`, `'portuguese'`, `'romanian'`, `'russian'`, `'simple'`, `'spanish'`, `'swedish'`, `'turkish'`. By default, a `'simple'` configuration will be used, without language-specific features.

```genericsql
SELECT * FROM customers WHERE text_search(profile, :search_words, 'english')
```

|  | Text search is currently only available for deployed services, and can’t be used in local testing. |

#### <a href="about:blank#_data_types"></a> Data types

When modeling your queries, the following data types are supported:

| Data type | Java type |
| --- | --- |
| Text | `String` |
| Integer | `int` / `Integer` |
| Long | `long` / `Long` |
| Float | `float` / `Float` |
| Double | `double` / `Double` |
| Boolean | `boolean` / `Boolean` |
| Lists | `Collection<T>` and derived |
| Timestamp | `java.time.Instant` |
| Date and time | `java.time.ZonedDateTime` |

#### <a href="about:blank#_optional_fields"></a> Optional fields

Fields in a view type that were not given a value are handled as the default value for primitive Java data types.

However, in some use cases it is important to explicitly express that a value is missing, doing that in a view column can be done in two ways:

- use one of the Java non-primitive types for the field (e.g. use `Integer` instead of `int`)
- Wrap the value in an `java.util.Optional`
- make the field a part of another class and leave it uninitialized (i.e. `null`), for example `address.street` where the lack of an `address` message implies there is no `street` field.
Optional fields with values present can be queried just like regular view fields:

```genericsql
SELECT * FROM customers WHERE phoneNumber = :number
```
Finding results with missing values can be done using `IS NULL`:

```genericsql
SELECT * FROM customers WHERE phoneNumber IS NULL
```
Finding entries with any value present can be queried using `IS NOT NULL`:

```genericsql
SELECT * FROM customers WHERE phoneNumber IS NOT NULL
```
Optional fields in query requests messages are handled like normal fields if they have a value, however missing optional request parameters are seen as an invalid request and lead to a bad request response.

### <a href="about:blank#_sorting"></a> Sorting

Results for a view query can be sorted. Use `ORDER BY` with view columns to sort results in ascending (`ASC`, by default) or descending (`DESC`) order.

If no explicit ordering is specified in a view query, results will be returned in the natural index order, which is based on the filter predicates in the query.

```genericsql
SELECT * FROM customers WHERE name = :name AND age > :minAge ORDER BY age DESC
```

|  | Some orderings may be rejected, if the view index cannot be efficiently ordered. Generally, to order by a field it should also appear in the `WHERE` conditions. |

### <a href="about:blank#_aggregation"></a> Aggregation

#### <a href="about:blank#_grouping"></a> Grouping

Grouping of results based on a field is supported using `collect(*)`. Each found key leads to one returned entry, where
all the entries for that key are collected into a `List` field.

Given the view data structure and response type:

```java
record Product(String name, int popularity) {}
record GroupedProducts(int popularity, List<Products> products) {}
```

```genericsql
SELECT popularity, collect(*) AS products
  FROM all_products
  GROUP BY popularity
  ORDER BY popularity
```
This example query returns one `GroupedProducts` entry per found unique popularity value, with all the products with
that popularity in the `products` list.

It is also possible to project individual fields in the grouped result. Given the previous `Product` view table type
and the following response type:

```java
record GroupedProductsNames(int popularity, List<String> productNames) {}
```

```genericsql
SELECT popularity, name AS productNames
  FROM all_products
  GROUP BY popularity
  ORDER BY popularity
```

#### <a href="about:blank#_count"></a> Count

Counting results matching a query can be done using `count(*)`.

```genericsql
SELECT count(*) FROM customers WHERE address.city = 'New York'
```

### <a href="about:blank#_paging"></a> Paging

Splitting a query result into one "page" at a time rather than returning the entire result at once is possible in two ways:

- a count based offset;
- a token based offset.
In both cases `OFFSET` and `LIMIT` are used.

`OFFSET` specifies at which offset in the result to start

`LIMIT` specifies a maximum number of results to return

#### <a href="about:blank#_count_based_offset"></a> Count based offset

The values can either be static, defined up front in the query:

```genericsql
SELECT * FROM customers LIMIT 10
```
Or come from fields in the request message:

```genericsql
SELECT * FROM customers OFFSET :startFrom LIMIT :maxCustomers
```
Note: Using count based offsets can lead to missing or duplicated entries in the result if entries are added to or removed from the view between requests for the pages.

#### <a href="about:blank#_token_based_offset"></a> Token based offset

The count based offset requires that you keep track of how far you got by adding the page size to the offset for each query.

An alternative to this is to use a string token emitted by Akka identifying how far into the result set the paging has reached using the functions `next_page_token()` and `page_token_offset()`.

When reading the first page, an empty token is provided to `page_token_offset`. For each returned result page a new token that can be used to read the next page is returned by `next_page_token()`, once the last page has been read, an empty token is returned. ([See here](about:blank#has-more) for determining if the last page was reached).

The size of each page can optionally be specified using `LIMIT`, if it is not present a default page size of 100 is used.

With the query request and response types like this:

```java
public record Request(String pageToken) {}
public record Response(List<Customer> customers, String nextPageToken) { }
```
A query such as the one below will allow for reading through the view in pages, each containing 10 customers:

```genericsql
SELECT * AS customers, next_page_token() AS nextPageToken
FROM customers
OFFSET page_token_offset(:pageToken)
LIMIT 10
```
The page token value string is not meant to be parseable into any meaningful information other than being a token for reading the next page.

Starting from the beginning of the pages is done by using empty string as request `pageToken` field value.

#### <a href="about:blank#_total_count_of_results"></a> Total count of results

To get the total number of results that will be returned over all pages, use `total_count()` in a query that projects its results into a field. The total count will be returned in the aliased field (using `AS`) or otherwise into a field named `totalCount`.

SELECT * AS customers, total_count() AS total, has_more() AS more FROM customers LIMIT 10
#### <a href="about:blank#has-more"></a> Check if there are more pages

To check if there are more pages left, you can use the function `has_more()` providing a boolean value for the result. This works both for the count and token based offset paging, and also when only using `LIMIT` without any `OFFSET`:

```genericsql
SELECT * AS customers, has_more() AS moreCustomers FROM customers LIMIT 10
```
This query will return `moreCustomers = true` when the view contains more than 10 customers.

## <a href="about:blank#_advanced_view_queries"></a> Advanced view queries

Advanced view queries include additional sort operations, grouping operations, joins across tables, and subquery support.

|  | Advanced view queries might not be available by default. Please contact the Akka support team if you require access to these features. |

### <a href="about:blank#_joins_and_multiple_tables"></a> Joins and multiple tables

Advanced views can subscribe to events and changes from multiple entities or event sources. Data for multiple tables can then be joined using relational join operations, similar to SQL. Supported join types are:

- `(INNER) JOIN` - only returns entries with matching values in both tables
- `LEFT (OUTER) JOIN` - returns all entries in the left table, joined with any matching entries from the right table
- `RIGHT (OUTER) JOIN` - returns all entries in the right table, joined with any matching entries from the left table
- `FULL (OUTER) JOIN` - returns all entries from both tables, with joined entries for matching values
In these examples, the Customer Registry used for simple views is extended to be a simple Store, adding Products and Orders for Customers. Customers and Products are implemented using Event Sourced Entities, while Orders is a Key Value Entity.

Each Product includes a name and a price:

[Product.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/product/domain/Product.java)
```java
public record Product(String name, Money price) {
  public Product withName(String newName) {
    return new Product(newName, price);
  }

  public Product withPrice(Money newPrice) {
    return new Product(name, newPrice);
  }
}
```
[Money.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/product/domain/Money.java)
```java
public record Money(String currency, long units, int cents) {
}
```
Each Order has an id, refers to the Customer and Product ids for this order, has the quantity of the ordered product, and a timestamp for when the order was created:

[Order.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/order/domain/Order.java)
```java
public record Order(
  String orderId,
  String productId,
  String customerId,
  int quantity,
  long createdTimestamp) {
}
```
A view can subscribe to the events or changes for each of the Customer, Order, and Product entities.

The view query can then JOIN across these tables, to return all orders for a specified customer, and include the customer and product details with each order.

To do this, create a class with a `ComponentId` annotation and extending from `View`. Inside, various inner classes that extend `TableUpdater` can be declared, each subscribing to one of the entities and setting a table name.

[CustomerOrder.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/order/view/joined/CustomerOrder.java)
```java
public record CustomerOrder(
  String orderId,
  String productId,
  String productName,
  Money price,
  int quantity,
  String customerId,
  String email,
  String name,
  Address address,
  long createdTimestamp) {
}
```
[JoinedCustomerOrdersView.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/order/view/joined/JoinedCustomerOrdersView.java)
```java
@ComponentId("joined-customer-orders") // (1)
public class JoinedCustomerOrdersView extends View {

  @Table("customers") // (2)
  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class CustomersUpdater extends TableUpdater<Customer> {
    public Effect<Customer> onEvent(CustomerEvent event) {
      return switch (event) {
        case CustomerEvent.CustomerCreated created -> {
          String id = updateContext().eventSubject().orElse("");
          yield effects()
            .updateRow(new Customer(id, created.email(), created.name(), created.address()));
        }

        case CustomerEvent.CustomerNameChanged nameChanged ->
          effects().updateRow(rowState().withName(nameChanged.newName()));

        case CustomerEvent.CustomerAddressChanged addressChanged ->
          effects().updateRow(rowState().withAddress(addressChanged.newAddress()));
      };
    }
  }

  @Table("products") // (2)
  @Consume.FromEventSourcedEntity(ProductEntity.class)
  public static class ProductsUpdater extends TableUpdater<Product> {
    public Effect<Product> onEvent(ProductEvent event) {
      return switch (event) {
        case ProductEvent.ProductCreated created -> {
          String id = updateContext().eventSubject().orElse("");
          yield effects().updateRow(new Product(id, created.name(), created.price()));
        }

        case ProductEvent.ProductNameChanged nameChanged ->
          effects().updateRow(rowState().withProductName(nameChanged.newName()));

        case ProductEvent.ProductPriceChanged priceChanged ->
          effects().updateRow(rowState().withPrice(priceChanged.newPrice()));
      };
    }
  }

  @Table("orders") // (2)
  @Consume.FromKeyValueEntity(OrderEntity.class)
  public static class OrdersUpdater extends TableUpdater<Order> {
  }

  public record JoinedCustomerOrders(List<CustomerOrder> orders) { }

  @Query( // (3)
      """
        SELECT * AS orders
        FROM customers
        JOIN orders ON customers.customerId = orders.customerId
        JOIN products ON products.productId = orders.productId
        WHERE customers.customerId = :customerId
        ORDER BY orders.createdTimestamp
        """)
  public QueryEffect<JoinedCustomerOrders> get(String customerId) { // (4)
    return queryResult();
  }

}
```

| **1** | Add a component id for this multi-table view. |
| **2** | Each nested table updater stores its state type in a different table (declared with `@Table`) and subscribes to one of the entities: `customers`, `products`, and `orders`. |
| **3** | The view query does the following:
  - Select all columns from the joined entries to project into the combined `CustomerOrder` result type.
  - Join customers with orders on a matching customer id.
  - Join products with orders on a matching product id.
  - Find orders for a particular customer.
  - Sort all the orders by their created timestamp. |
| **4** | The query method returns a collections of customer orders. |
In the example above, each `CustomerOrder` returned will contain the same customer details. The results can instead include the customer details once, and then all the ordered products in a collection, using a [projection](about:blank#_result_projection) in the SELECT clause. That is, instead of using SELECT * you can define which columns will be used in the response message:

[NestedCustomerOrders.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/order/view/nested/NestedCustomerOrders.java)
```java
public record NestedCustomerOrders(
  String customerId,
  String email,
  String name,
  Address address,
  List<CustomerOrder> orders) {
} // (1)
```

| **1** | The `orders` field will contain the nested `CustomerOrder` objects. |
[NestedCustomerOrdersView.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/order/view/nested/NestedCustomerOrdersView.java)
```java
@Query( // (1)
  """
    SELECT customers.*, (orders.*, products.*) AS orders
    FROM customers
    JOIN orders ON customers.customerId = orders.customerId
    JOIN products ON products.productId = orders.productId
    WHERE customers.customerId = :customerId
    ORDER BY orders.createdTimestamp
    """)
public QueryEffect<NestedCustomerOrders> get(String customerId) { // (2)
  return queryResult();
}
```

| **1** | In the view query, the customer columns are projected into the result, and the order and product columns are combined into a nested object and projected into the `orders` field. |
| **2** | A single `CustomerOrders` object is returned, which will have the customer details and all orders for this customer. |
A [projection](about:blank#_result_projection) for a JOIN query can also restructure the results. For example, the shipping details for a customer can be constructed in a particular form, and the product orders transformed into a different nested message structure:

[StructuredCustomerOrders.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/order/view/structured/StructuredCustomerOrders.java)
```java
public record StructuredCustomerOrders(
  String id,
  CustomerShipping shipping,
  List<ProductOrder> orders) {
}
```
[CustomerShipping.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/order/view/structured/CustomerShipping.java)
```java
public record CustomerShipping(
  String name,
  String address1,
  String address2,
  String contactEmail) {
}
```
[ProductOrder.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/order/view/structured/ProductOrder.java)
```java
public record ProductOrder(
  String id,
  String name,
  int quantity,
  ProductValue value,
  String orderId,
  long orderCreatedTimestamp) {
}
```
[ProductValue.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/order/view/structured/ProductValue.java)
```java
public record ProductValue(String currency, long units, int cents) {
}
```
[StructuredCustomerOrdersView.java](https://github.com/akka/akka-sdk/blob/main/samples/view-store/src/main/java/store/order/view/structured/StructuredCustomerOrdersView.java)
```java
@Query( // (1)
  """
    SELECT
     customers.customerId AS id,
     (name,
      address.street AS address1,
      address.city AS address2,
      email AS contactEmail) AS shipping,
     (products.productId AS id,
      productName AS name,
      quantity,
      (price.currency, price.units, price.cents) AS value,
      orderId,
      createdTimestamp AS orderCreatedTimestamp) AS orders
    FROM customers
    JOIN orders ON orders.customerId = customers.customerId
    JOIN products ON products.productId = orders.productId
    WHERE customers.customerId = :customerId
    ORDER BY orders.createdTimestamp
    """)
public QueryEffect<StructuredCustomerOrders> get(String customerId) {
  return queryResult();
}
```

| **1** | The view query does the following:
  - The `customerId` is renamed to just `id` in the result.
  - Customer shipping details are transformed and combined into a nested object.
  - The product price is reconstructed into a `ProductValue` object, nested within the order object.
  - The order and associated product information is transformed and combined into a collection of `ProductOrder` objects.
  - The nested orders in the result will still be sorted by their created timestamps. |

|  | Rather than transforming results in a projection, it’s also possible to transform the stored state in the update methods for the view table. |

### <a href="about:blank#_enable_advanced_views"></a> Enable advanced views

Advanced view queries are not available by default when you deploy your Akka service. Please contact the Akka support team if you require access to these features.

For local development and when running integration tests, the advanced view features are available by default.

## <a href="about:blank#_testing_the_view"></a> Testing the View

Testing Views is very similar to testing other [subscription integrations](consuming-producing.html#_testkit_mocked_incoming_messages).

For a View definition that subscribes to changes from the `customer` Key Value Entity.

[CustomersByCity.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/application/CustomersByCity.java)
```java
public class CustomersByCity extends View {

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class CustomerUpdater extends TableUpdater<Customer> {}

  @Query("""
    SELECT * AS customers
        FROM customers_by_city
      WHERE address.city = ANY(:cities)
    """)
  public QueryEffect<CustomerList> getCustomers(List<String> cities) {
    return queryResult();
  }

  @Query(value = "SELECT * FROM customers_by_city WHERE address.city = :city")
  public QueryStreamEffect<Customer> streamCustomersInCity(String city) {
    return queryStreamResult();
  }

  @Query(value = "SELECT * FROM customers_by_city WHERE address.city = :city", streamUpdates = true)
  public QueryStreamEffect<Customer> continuousCustomersInCity(String city) {
    return queryStreamResult();
  }

  public record QueryParams(String customerName, String city) {} // (1)

  @Query(value = "SELECT * FROM customers_by_city WHERE name = :customerName AND address.city = :city") // (2)
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
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
      .withKeyValueEntityIncomingMessages("customer"); // (1)
  }

  @Test
  public void shouldGetCustomerByCity() {
    IncomingMessages customerEvents = testKit.getKeyValueEntityIncomingMessages("customer"); // (2)

    Customer johanna = new Customer("johanna@example.com", "Johanna",
      new Address("Cool Street", "Porto"));
    Customer bob = new Customer("boc@example.com", "Bob",
      new Address("Baker Street", "London"));
    Customer alice = new Customer("alice@example.com", "Alice",
      new Address("Long Street", "Wroclaw"));


    customerEvents.publish(johanna, "1"); // (3)
    customerEvents.publish(bob, "2");
    customerEvents.publish(alice, "3");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {

          CustomerList customersResponse =
            componentClient.forView()
              .method(CustomersByCity::getCustomers) // (4)
              .invoke(List.of("Porto", "London"));

          assertThat(customersResponse.customers()).containsOnly(johanna, bob);
        }
      );
  }
}
```

| **1** | Mocks incoming messages from the `customer` Key Value Entity. |
| **2** | Gets an `IncomingMessages` from the `customer` Key Value Entity. |
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