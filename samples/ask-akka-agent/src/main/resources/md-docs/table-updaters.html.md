<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View concepts](index.html)
- [Table updaters](table-updaters.html)

<!-- </nav> -->

# Table Updaters

Table Updaters are the core components that define and maintain view tables. They subscribe to events or state changes from entities and transform this data into queryable views. This page explains how to define and use Table Updaters in your View components.

## <a href="about:blank#_what_are_table_updaters"></a> What are Table Updaters?

A Table Updater is a Java class defined inside your View that:

1. Specifies a data source (entity or topic)
2. Defines the structure of a view table
3. Contains logic for transforming incoming data into view rows
4. Handles updates, deletes, and other operations on the view table

## <a href="about:blank#_basic_structure"></a> Basic Structure

A Table Updater is defined as a static inner class inside your View:

```java
@Component(id = "customer-view")
public class CustomerView extends View {

  @Table("customers") // Optional table name
  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class Customers extends TableUpdater<Customer> {
    // Transformation methods go here
  }

  // Query methods go here
}
```
For a full view sample see [Creating a View from a Key Value Entity](../../../sdk/views.html#value-entity)

## <a href="about:blank#_table_naming"></a> Table Naming

Table names can be specified explicitly with the `@Table` annotation:

```java
@Table("customers")
public static class Customers extends TableUpdater<Customer> { }
```
For single table views the annotation is optional and is derived from the table name used in the queries in the view.

For multi table [Advanced Views](advanced-views.html) each updater must have the annotation to specify
which table name it updates.

## <a href="about:blank#_data_sources"></a> Data Sources

Table Updaters can consume data from different sources:

### <a href="about:blank#_key_value_entities"></a> Key Value Entities

Subscribe to state changes from Key Value Entities:

```java
@Consume.FromKeyValueEntity(CustomerEntity.class)
```
For a full sample see [Creating a View from a Key Value Entity](../../../sdk/views.html#value-entity)

### <a href="about:blank#_event_sourced_entities"></a> Event Sourced Entities

Subscribe to events from Event Sourced Entities:

```java
@Consume.FromEventSourcedEntity(CustomerEntity.class)
```
For a full sample see [Creating a View from an Event Sourced Entity](../../../sdk/views.html#event-sourced-entity)

### <a href="about:blank#_workflows"></a> Workflows

Subscribe to state changes from Workflows:

```java
@Consume.FromWorkflow(TransferWorkflow.class)
```
For a full sample see [Creating a View from a Workflow](../../../sdk/views.html#workflow)

### <a href="about:blank#_service_to_service_eventing"></a> Service to service eventing

Subscribe to events from an Event Sourced entity made available by another Akka service:

```java
@Consume.FromServiceStream("service-name", "stream-id")
```
For more details see [Service to Service Eventing](../../../sdk/consuming-producing.html#s2s-eventing)

### <a href="about:blank#_topics"></a> Topics

Subscribe to messages from a topic:

```java
@Consume.FromTopic("customer-events")
```
For a full sample see [Creating a View from a topic](../../../sdk/views.html#topic-view)

## <a href="about:blank#_generic_type_parameter"></a> Generic Type Parameter

The generic type parameter of `TableUpdater<T>` defines the structure of each row in the view table:

```java
public static class Customers extends TableUpdater<Customer> { }
```
This means each row in the view table will have the structure of the `Customer` class.

## <a href="about:blank#_transformation_methods"></a> Transformation Methods

Table Updaters can include an `onUpdate` method to transform incoming data. The update method is handed the incoming event,
message or update, and returns an effect describing what should happen with the table row:

```java
@Consume.FromKeyValueEntity(CustomerEntity.class)
public static class CustomerSummaries extends TableUpdater<CustomerSummary> {

  public Effect<CustomerSummary> onUpdate(Customer customer) {
    return effects().updateRow(
      new CustomerSummary(
        updateContext().eventSubject().get(),
        customer.name(),
        customer.email()
      )
    );
  }
}
```
For a few examples of different table update handlers see [Implementing Views](../../../sdk/views.html)

## <a href="about:blank#_effect_types"></a> Effect Types

Table Updater methods return `Effect` objects that define what happens to the view table:

### <a href="about:blank#_update_row"></a> Update Row

Updates or inserts a row in the view table:

```java
return effects().updateRow(newRowState);
```

### <a href="about:blank#_delete_row"></a> Delete Row

Deletes the current row from the view table:

```java
return effects().deleteRow();
```

### <a href="about:blank#_ignore"></a> Ignore

Makes no changes to the view table:

```java
return effects().ignore();
```

## <a href="about:blank#_accessing_context"></a> Accessing Context

Table Updaters provide context about the current update:

### <a href="about:blank#_update_context"></a> Update Context

Access information about the event or state change:

```java
// Get the entity ID
String entityId = updateContext().eventSubject().get();

// Check if the event originated in the local region
boolean isLocal = updateContext().hasLocalOrigin();

// Get the region where the event originated
String originRegion = updateContext().originRegion();
```

### <a href="about:blank#_row_state"></a> Row State

Access the current state of the row being updated:

```java
// For immutable types, create a new instance with updated fields
return effects().updateRow(rowState().withName(nameChanged.newName()));

// For mutable types, modify and return
CustomerRow current = rowState();
current.setName(nameChanged.newName());
return effects().updateRow(current);
```

## <a href="about:blank#_multi_table_views"></a> Multi-Table Views

A single View can define multiple Table Updaters to create a view with multiple tables:

```java
@Component(id = "shop-view")
public class ShopView extends View {

  @Table("customers")
  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class Customers extends TableUpdater<Customer> {
    // Customer transformation methods
  }

  @Table("products")
  @Consume.FromEventSourcedEntity(ProductEntity.class)
  public static class Products extends TableUpdater<Product> {
    // Product transformation methods
  }

  @Table("orders")
  @Consume.FromKeyValueEntity(OrderEntity.class)
  public static class Orders extends TableUpdater<Order> {
    // Order transformation methods
  }

  // Query methods that can join across tables
  @Query("""
    SELECT c.name, o.*, p.name AS productName
    FROM customers AS c
    JOIN orders AS o ON c.id = o.customerId
    JOIN products AS p ON o.productId = p.id
    WHERE c.id = :customerId
    """)
  public QueryEffect<CustomerOrders> getCustomerOrders(String customerId) {
    return queryResult();
  }
}
```
For details on querying multi table views, see [Advanced Views](advanced-views.html)

## <a href="about:blank#_handling_deletes"></a> Handling Deletes

To handle entity deletions

### <a href="about:blank#_for_key_value_entities"></a> For Key Value Entities

Use the `@DeleteHandler` annotation:

```java
@Consume.FromKeyValueEntity(CustomerEntity.class)
public static class Customers extends TableUpdater<Customer> {

  @DeleteHandler
  public Effect<Customer> onDelete() {
    return effects().deleteRow();
  }
}
```

### <a href="about:blank#_for_event_sourced_entities"></a> For Event Sourced Entities

Handle delete events explicitly:

```java
@Consume.FromEventSourcedEntity(CustomerEntity.class)
public static class Customers extends TableUpdater<Customer> {

  public Effect<Customer> onEvent(CustomerEvent event) {
    return switch (event) {
      case CustomerEvent.CustomerDeleted deleted -> effects().deleteRow();
      // Handle other events
    };
  }
}
```

## <a href="about:blank#_related_features"></a> Related Features

- [Data Types](data-types.html) - Types supported in views
- [FROM clause](../syntax/from.html) - Referencing tables in queries
- [JOIN clause](../syntax/join.html) - Combining data from multiple tables
- [Advanced Views](advanced-views.html) - Creating complex views

<!-- <footer> -->
<!-- <nav> -->
[View concepts](index.html) [Data types](data-types.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->