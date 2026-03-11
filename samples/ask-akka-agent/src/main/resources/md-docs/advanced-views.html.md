<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View concepts](index.html)
- [Advanced views](advanced-views.html)

<!-- </nav> -->

# Advanced Views

Advanced Views in Akka provide sophisticated features for complex data access patterns, including multi-table joins, complex projections, and hierarchical data structures. This page explains these advanced capabilities and how to use them effectively.

## <a href="about:blank#_multi_table_views"></a> Multi-Table Views

A key feature of Advanced Views is the ability to define multiple tables in a single View component and perform joins across them.

### <a href="about:blank#_defining_multiple_tables"></a> Defining Multiple Tables

Multiple tables are defined by creating multiple `TableUpdater` classes within a View:

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

  // Query methods with joins
}
```
Each `TableUpdater` class:

1. Is annotated with `@Table` to specify the table name
2. Has its own data source annotation (`@Consume.From…​`)
3. Defines its own row structure with the generic type parameter
4. Can have its own transformation methods

### <a href="about:blank#_joining_tables"></a> Joining Tables

Once multiple tables are defined, you can join them in queries:

```sql
SELECT customers.*, orders.*
FROM customers
JOIN orders ON orders.customerId = customers.id
WHERE customers.id = :customerId
```
This retrieves customer data along with their orders in a single query.

## <a href="about:blank#_join_types"></a> Join Types

Advanced Views support several join types:

### <a href="about:blank#_inner_join"></a> INNER JOIN

Returns only rows that have matching values in both tables:

```sql
SELECT c.name, o.id, o.amount
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id
```
Only customers who have placed orders will appear in the result.

### <a href="about:blank#_left_join"></a> LEFT JOIN

Returns all rows from the left table and matching rows from the right table:

```sql
SELECT c.name, o.id, o.amount
FROM customers AS c
LEFT JOIN orders AS o ON o.customerId = c.id
```
All customers appear in the result, even those without orders (with NULL order fields).

### <a href="about:blank#_right_join"></a> RIGHT JOIN

Returns all rows from the right table and matching rows from the left table:

```sql
SELECT c.name, o.id, o.amount
FROM customers AS c
RIGHT JOIN orders AS o ON o.customerId = c.id
```
All orders appear in the result, even if the customer no longer exists (with NULL customer fields).

### <a href="about:blank#_full_join"></a> FULL JOIN

Returns rows when there is a match in either table:

```sql
SELECT c.name, o.id, o.amount
FROM customers AS c
FULL JOIN orders AS o ON o.customerId = c.id
```
Shows all customers and all orders, with NULL fields where there is no match.

## <a href="about:blank#_complex_data_projections"></a> Complex Data Projections

Advanced Views enable complex data projections to create custom result structures:

### <a href="about:blank#_restructuring_fields"></a> Restructuring Fields

Select and rename fields from multiple tables:

```sql
SELECT
  c.id,
  c.name AS customerName,
  o.id AS orderId,
  o.amount AS orderAmount
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id
```

### <a href="about:blank#_creating_nested_objects"></a> Creating Nested Objects

Group related fields into nested objects:

```sql
SELECT
  c.id,
  c.name,
  (o.id, o.amount, o.date) AS orderDetails
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id
```

### <a href="about:blank#_custom_field_names_in_nested_objects"></a> Custom Field Names in Nested Objects

Specify field names within nested objects:

```sql
SELECT
  c.id,
  c.name,
  (o.id AS identifier, o.amount AS total, o.date AS ordered) AS orderDetails
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id
```

## <a href="about:blank#_hierarchical_data_structures"></a> Hierarchical Data Structures

Advanced Views excel at creating hierarchical data structures that represent one-to-many relationships:

### <a href="about:blank#_nested_collections"></a> Nested Collections

Combine joins with `GROUP BY` and `collect()` to create nested collections:

```sql
SELECT
  c.id,
  c.name,
  collect(o.*) AS orders
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id
GROUP BY c.id, c.name
```
This creates a hierarchical structure with customer information and a nested collection of their orders.

### <a href="about:blank#_multi_level_nesting"></a> Multi-level Nesting

Create complex hierarchies with multiple levels of nesting:

```sql
SELECT
  c.id,
  c.name,
  collect((o.id, o.date, collect((i.productId, i.quantity) AS items) AS orderItems) AS order) AS orders
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id
JOIN order_items AS i ON i.orderId = o.id
GROUP BY c.id, c.name, o.id, o.date
GROUP BY c.id, c.name
```
This creates a three-level hierarchy: customers → orders → order items.

## <a href="about:blank#_multiple_data_sources"></a> Multiple Data Sources

Advanced Views can combine data from different types of sources:

### <a href="about:blank#_mixed_entity_types"></a> Mixed Entity Types

Combine Event Sourced Entities with Key Value Entities:

```java
@Table("customers")
@Consume.FromEventSourcedEntity(CustomerEntity.class)
public static class Customers extends TableUpdater<Customer> { }

@Table("sessions")
@Consume.FromKeyValueEntity(SessionEntity.class)
public static class Sessions extends TableUpdater<Session> { }
```

### <a href="about:blank#_combining_entities_and_topics"></a> Combining Entities and Topics

Mix entity data with data from topics:

```java
@Table("customers")
@Consume.FromEventSourcedEntity(CustomerEntity.class)
public static class Customers extends TableUpdater<Customer> { }

@Table("notifications")
@Consume.FromTopic("customer-notifications")
public static class Notifications extends TableUpdater<Notification> { }
```

## <a href="about:blank#_advanced_filtering"></a> Advanced Filtering

Advanced Views support sophisticated filtering:

### <a href="about:blank#_complex_join_conditions"></a> Complex Join Conditions

Join tables with multiple conditions:

```sql
SELECT c.*, o.*
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id AND o.status = 'active'
```

### <a href="about:blank#_filtering_in_multiple_places"></a> Filtering in Multiple Places

Apply filters at different stages of the query:

```sql
SELECT c.name, collect(p.*)
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id
JOIN order_items AS i ON i.orderId = o.id
JOIN products AS p ON p.id = i.productId
WHERE c.status = 'active' AND o.date > '2023-01-01'
GROUP BY c.name
```

## <a href="about:blank#_enabling_advanced_views"></a> Enabling Advanced Views

Advanced View features are not available by default in deployed services:

- For local development and testing, advanced features are available automatically
- For deployed services, contact the Akka support team to enable advanced view features

## <a href="about:blank#_best_practices"></a> Best Practices

### <a href="about:blank#_performance_considerations"></a> Performance Considerations

- Be mindful of join complexity - very complex joins may impact performance
- Consider indexing strategies for columns used in join conditions
- Use appropriate join types to avoid unnecessary data processing
- Test queries with realistic data volumes

### <a href="about:blank#_design_guidelines"></a> Design Guidelines

- Group related tables in a single View component
- Use clear naming conventions for tables and fields
- Document the relationships between tables
- Create response types that match the hierarchical structure of your queries
- Use table aliases to make complex queries more readable

### <a href="about:blank#_modeling_tips"></a> Modeling Tips

- Use nested objects for related fields that always appear together
- Use collections for one-to-many relationships
- Consider normalization vs. denormalization tradeoffs based on query patterns
- Design your table structures based on access patterns, not just entity structure

## <a href="about:blank#_examples"></a> Examples

### <a href="about:blank#_e_commerce_example"></a> E-Commerce Example

Model:

```java
// Customer data
public record Customer(String id, String name, String email, Address address) { }
public record Address(String street, String city, String zipCode, String country) { }

// Product data
public record Product(String id, String name, String description, double price) { }

// Order data
public record Order(String id, String customerId, Instant orderDate, String status) { }
public record OrderItem(String orderId, String productId, int quantity, double price) { }
```
View with multiple tables:

```java
@Component(id = "shop-view")
public class ShopView extends View {

  @Table("customers")
  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class Customers extends TableUpdater<Customer> { }

  @Table("products")
  @Consume.FromEventSourcedEntity(ProductEntity.class)
  public static class Products extends TableUpdater<Product> { }

  @Table("orders")
  @Consume.FromEventSourcedEntity(OrderEntity.class)
  public static class Orders extends TableUpdater<Order> { }

  @Table("order_items")
  @Consume.FromKeyValueEntity(OrderItemEntity.class)
  public static class OrderItems extends TableUpdater<OrderItem> { }

  @Query("""
    SELECT
      c.name,
      c.email,
      (c.address.street, c.address.city, c.address.zipCode) AS shippingAddress,
      collect(
        (o.id AS orderId,
         o.orderDate,
         collect(
           (p.name, i.quantity, i.price) AS item
         ) AS items
        )
      ) AS orders
    FROM customers AS c
    JOIN orders AS o ON o.customerId = c.id
    JOIN order_items AS i ON i.orderId = o.id
    JOIN products AS p ON p.id = i.productId
    WHERE c.id = :customerId
    GROUP BY o.id, o.orderDate
    GROUP BY c.name, c.email, c.address
    """)
  public QueryEffect<CustomerOrderDetails> getCustomerOrderDetails(String customerId) {
    return queryResult();
  }
}
```
Response types:

```java
public record CustomerOrderDetails(
    String name,
    String email,
    ShippingAddress shippingAddress,
    List<OrderDetail> orders
) { }

public record ShippingAddress(
    String street,
    String city,
    String zipCode
) { }

public record OrderDetail(
    String orderId,
    Instant orderDate,
    List<OrderItem> items
) { }

public record OrderItem(
    String name,
    int quantity,
    double price
) { }
```

## <a href="about:blank#_related_features"></a> Related Features

- [JOIN clause](../syntax/join.html) - Combining data from multiple tables
- [collect() function](../syntax/functions/collect.html) - Creating nested collections
- [GROUP BY clause](../syntax/group-by.html) - Grouping data for hierarchical structures
- [Table Updaters](table-updaters.html) - Defining view tables
- [Result Mapping](result-mapping.html) - How queries map to Java types

<!-- <footer> -->
<!-- <nav> -->
[Pagination](pagination.html) [JSON Web Tokens (JWTs)](../../jwts.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->