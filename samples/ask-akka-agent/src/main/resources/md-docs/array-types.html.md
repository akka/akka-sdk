<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View concepts](index.html)
- [Array types](array-types.html)

<!-- </nav> -->

# Array Types

Array types in Views allow you to work with collections of values within a single row. This page explains how to define, query, and manipulate array data in your Views.

## <a href="about:blank#_defining_array_fields_in_java"></a> Defining Array Fields in Java

There are several ways to represent array fields in your View data models:

### <a href="about:blank#_collections"></a> Collections

Use Java collection types to represent arrays:

```java
public record Product(
    String id,
    String name,
    List<String> categories,
    Set<String> tags
) { }
```
All Java collection types (List, Set, Collection, etc.) are treated as arrays in view queries.

### <a href="about:blank#_java_arrays"></a> Java Arrays

Native Java arrays are also supported:

```java
public record Product(
    String id,
    String name,
    String[] categories,
    int[] ratings
) { }
```

### <a href="about:blank#_nested_object_arrays"></a> Nested Object Arrays

Arrays can contain complex objects:

```java
public record Order(
    String id,
    String customerId,
    List<OrderItem> items
) { }

public record OrderItem(
    String productId,
    int quantity,
    double price
) { }
```

## <a href="about:blank#_querying_array_fields"></a> Querying Array Fields

### <a href="about:blank#_testing_membership_with_any"></a> Testing Membership with = ANY

The primary way to query array fields is with the `= ANY` operator, which tests if an array contains a specific value:

```sql
-- Find products in a specific category
SELECT * FROM products
WHERE 'Electronics' = ANY(categories)

-- Find products with a specific tag from a parameter
SELECT * FROM products
WHERE :tag = ANY(tags)
```
The `= ANY` operator can be used in two ways:

1. `value = ANY(arrayColumn)` - Tests if the array column contains the value
2. `column = ANY(:arrayParameter)` - Tests if the column’s value is in the array parameter

### <a href="about:blank#_filtering_with_array_parameters"></a> Filtering with Array Parameters

You can pass arrays as parameters to filter rows:

```sql
-- Find products with IDs in a specific list
SELECT * FROM products
WHERE id = ANY(:productIds)
```
In your Java code, pass a List, Set, or array as the parameter:

```java
List<String> productIds = List.of("prod-1", "prod-2", "prod-3");
componentClient.forView()
    .method(ProductView::getProducts)
    .invoke(productIds);
```

## <a href="about:blank#_creating_arrays_in_query_results"></a> Creating Arrays in Query Results

### <a href="about:blank#_using_collect_function"></a> Using collect() Function

The primary way to create arrays in query results is with the `collect()` function, typically combined with `GROUP BY`:

```sql
-- Group products by category
SELECT category, collect(*) AS products
FROM products
GROUP BY category
```
This groups products by category and creates an array of product objects for each category.

### <a href="about:blank#_collecting_specific_fields"></a> Collecting Specific Fields

You can collect specific fields into arrays:

```sql
-- Collect just product names for each category
SELECT category, collect(name) AS productNames
FROM products
GROUP BY category
```

### <a href="about:blank#_collecting_distinct_values"></a> Collecting Distinct Values

Use the `distinct` keyword to eliminate duplicates:

```sql
-- Collect unique tags across products
SELECT collect(distinct tags) AS allTags
FROM products
```

### <a href="about:blank#_arrays_of_nested_objects"></a> Arrays of Nested Objects

Create arrays of custom-structured objects:

```sql
-- Create custom objects in the array
SELECT category, collect((name, price) AS item) AS products
FROM products
GROUP BY category
```

## <a href="about:blank#_mapping_array_results_in_java"></a> Mapping Array Results in Java

Results containing arrays map to collection types in Java:

```java
public record CategoryProducts(
    String category,
    List<Product> products
) { }

public record CategoryProductNames(
    String category,
    List<String> productNames
) { }

public record CategoryItems(
    String category,
    List<ProductItem> products
) { }

public record ProductItem(
    String name,
    double price
) { }
```

## <a href="about:blank#_array_limitations_and_behavior"></a> Array Limitations and Behavior

### <a href="about:blank#_querying_within_arrays"></a> Querying Within Arrays

In the current implementation, querying for specific elements within arrays has some limitations:

- You can check if an array contains a value using `= ANY`
- Directly accessing array elements by index is not supported
- Querying nested properties within array elements is not supported

### <a href="about:blank#_empty_arrays"></a> Empty Arrays

Empty arrays are handled as follows:

- `value = ANY(emptyArray)` will always be false
- `collect(…​)` on an empty result set produces an empty array

### <a href="about:blank#_null_vs_empty_array"></a> NULL vs. Empty Array

There’s a distinction between NULL arrays and empty arrays:

- NULL array: The array field itself is missing (`array IS NULL`)
- Empty array: The array exists but contains no elements (`array = '{}'`)
These are queried differently:

```sql
-- Find products with no categories specified (NULL array)
SELECT * FROM products
WHERE categories IS NULL

-- This would find products with an empty categories array,
-- but this precise syntax isn't currently supported
```

## <a href="about:blank#_best_practices"></a> Best Practices

### <a href="about:blank#_when_to_use_arrays"></a> When to Use Arrays

Arrays are useful for:

- Categorization and tagging (products with multiple categories)
- Property lists (features, attributes)
- Simple one-to-many relationships within a row
- Aggregating related items in query results

### <a href="about:blank#_performance_considerations"></a> Performance Considerations

- Arrays are stored and indexed efficiently in the underlying database
- The `= ANY` operator can use indexes when properly configured
- For very large arrays, consider alternative data models

### <a href="about:blank#_array_design_tips"></a> Array Design Tips

- Keep arrays reasonably sized (typically under a few hundred elements)
- For complex many-to-many relationships, consider using separate tables and JOINs
- Use appropriate collection types in your Java models (List, Set, etc.)
- Document the expected behavior of your array fields

## <a href="about:blank#_examples"></a> Examples

### <a href="about:blank#_full_example_product_tags_and_categories"></a> Full Example: Product Tags and Categories

Model:

```java
public record Product(
    String id,
    String name,
    double price,
    List<String> categories,
    Set<String> tags
) { }
```
Query examples:

```sql
-- Find products in multiple categories
SELECT * FROM products
WHERE 'Electronics' = ANY(categories) OR 'Gadgets' = ANY(categories)

-- Find products with specific tags
SELECT * FROM products
WHERE :searchTag = ANY(tags)

-- Group products by category
SELECT category, collect(*) AS categoryProducts
FROM products, categories AS category
WHERE category = ANY(categories)
GROUP BY category
```

### <a href="about:blank#_nested_arrays_example"></a> Nested Arrays Example

Model:

```java
public record Order(
    String id,
    String customerId,
    List<OrderItem> items,
    Instant orderDate
) { }

public record OrderItem(
    String productId,
    String productName,
    int quantity,
    double unitPrice
) { }
```
Queries:

```sql
-- Group orders by customer
SELECT customerId, collect(*) AS orders
FROM orders
GROUP BY customerId

-- Group with transformations
SELECT
  customerId,
  collect((id, orderDate) AS orderSummary) AS orderHistory
FROM orders
GROUP BY customerId
```

## <a href="about:blank#_related_features"></a> Related Features

- [= ANY operator](../syntax/operators/any.html) - Testing array membership
- [collect() function](../syntax/functions/collect.html) - Creating arrays in results
- [DISTINCT keyword](../syntax/functions/distinct.html) - Removing duplicates from arrays
- [GROUP BY clause](../syntax/group-by.html) - Grouping data for arrays
- [Result Mapping](result-mapping.html) - How arrays map to Java types

<!-- <footer> -->
<!-- <nav> -->
[Optional fields](optional-fields.html) [Pagination](pagination.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->