<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View concepts](index.html)
- [Result mapping](result-mapping.html)

<!-- </nav> -->

# Result Mapping

Result mapping is the process of converting data retrieved by View queries into Java objects. This page explains how query results are mapped to Java types and provides guidance on defining response types that align with your queries.

## <a href="about:blank#_query_result_structure"></a> Query Result Structure

The structure of your query result is determined by:

1. The columns selected in the `SELECT` clause
2. The aliases assigned to those columns using `AS`
3. Any nesting created by grouping columns or using `collect()`
The mapping system establishes a correspondence between this structure and your Java response types.

## <a href="about:blank#_basic_mapping_rules"></a> Basic Mapping Rules

### <a href="about:blank#_field_names"></a> Field Names

Field names in your Java classes must match the column names or aliases in your query:

```sql
SELECT id, name, email FROM customers
```
Must map to a Java class with fields named `id`, `name`, and `email`:

```java
public record CustomerResponse(String id, String name, String email) { }
```

### <a href="about:blank#_using_aliases"></a> Using Aliases

Use the `AS` keyword to map columns to differently named Java fields:

```sql
SELECT id, name AS customerName, email AS contactEmail FROM customers
```
Maps to:

```java
public record CustomerResponse(
    String id,
    String customerName,
    String contactEmail
) { }
```

### <a href="about:blank#_wildcard_selection"></a> Wildcard Selection

When using `*` to select all columns, you can map to a specific field with an alias:

```sql
SELECT * AS customer FROM customers
```
Maps to:

```java
public record Response(Customer customer) { }
```
Where `Customer` contains all the fields from the `customers` table.

## <a href="about:blank#_complex_mappings"></a> Complex Mappings

### <a href="about:blank#_nested_objects"></a> Nested Objects

Create nested objects by grouping columns with parentheses and an alias:

```sql
SELECT id, (name, email) AS contactInfo FROM customers
```
Maps to:

```java
public record CustomerResponse(
    String id,
    ContactInfo contactInfo
) { }

public record ContactInfo(String name, String email) { }
```

### <a href="about:blank#_custom_field_names_in_nested_objects"></a> Custom Field Names in Nested Objects

Specify field names within nested objects:

```sql
SELECT id, (name AS fullName, email AS emailAddress) AS contact FROM customers
```
Maps to:

```java
public record CustomerResponse(
    String id,
    Contact contact
) { }

public record Contact(String fullName, String emailAddress) { }
```

### <a href="about:blank#_collections"></a> Collections

See [Creating Arrays in Query Results](array-types.html#_creating_arrays_in_query_results)

## <a href="about:blank#_special_function_results"></a> Special Function Results

### <a href="about:blank#_pagination_functions"></a> Pagination Functions

Map pagination function results to appropriate fields:

```sql
SELECT * AS items,
       next_page_token() AS nextPageToken,
       has_more() AS hasMore,
       total_count() AS totalCount
FROM products
LIMIT 10
```
Maps to:

```java
public record ProductsPage(
    List<Product> items,
    String nextPageToken,
    boolean hasMore,
    int totalCount
) { }
```

### <a href="about:blank#_count_function"></a> Count Function

Map count results to numeric fields:

```sql
SELECT count(*) AS totalCustomers FROM customers
```
Maps to:

```java
public record CustomerCount(int totalCustomers) { }
```

## <a href="about:blank#_java_type_compatibility"></a> Java Type Compatibility

### <a href="about:blank#_primitive_vs_object_types"></a> Primitive vs. Object Types

Both primitive and object types are supported for numeric and boolean fields:

- `int` / `Integer`
- `long` / `Long`
- `float` / `Float`
- `double` / `Double`
- `boolean` / `Boolean`
Use object types when the field might be NULL.

### <a href="about:blank#_collection_types"></a> Collection Types

Query results that return collections can map to:

- `java.util.List<T>`
- `java.util.Collection<T>`
- Other collection types that can be constructed from a `Collection`

### <a href="about:blank#_optional_fields"></a> Optional Fields

Fields that might be NULL can be represented as:

- Object types (e.g., `Integer` instead of `int`)
- `java.util.Optional<T>`
- Nullable fields in a class

## <a href="about:blank#_best_practices"></a> Best Practices

- Define response types that exactly match your query structure
- Use aliases in queries to match your preferred Java field names
- Use nested objects to organize related data
- Consider using Java records for response types
- Match field types carefully to ensure compatibility
- Use object types instead of primitives for potentially NULL values
- Document the relationship between queries and response types

## <a href="about:blank#_examples"></a> Examples

### <a href="about:blank#_flat_object_mapping"></a> Flat Object Mapping

Query:

```sql
SELECT id, name, email, createdDate FROM customers WHERE id = :customerId
```
Response type:

```java
public record CustomerDetails(
    String id,
    String name,
    String email,
    Instant createdDate
) { }
```

### <a href="about:blank#_nested_object_mapping"></a> Nested Object Mapping

Query:

```sql
SELECT id,
       name,
       (street, city, zipCode, country) AS address
FROM customers
WHERE id = :customerId
```
Response types:

```java
public record CustomerWithAddress(
    String id,
    String name,
    Address address
) { }

public record Address(
    String street,
    String city,
    String zipCode,
    String country
) { }
```

### <a href="about:blank#_collection_result_mapping"></a> Collection Result Mapping

Query:

```sql
SELECT category,
       collect((name, price, description) AS item) AS products
FROM products
GROUP BY category
WHERE category = :category
```
Response types:

```java
public record CategoryProducts(
    String category,
    List<ProductItem> products
) { }

public record ProductItem(
    String name,
    double price,
    String description
) { }
```

## <a href="about:blank#_related_features"></a> Related Features

- [SELECT clause](../syntax/select.html) - Defining the result structure
- [AS keyword](../syntax/as.html) - Naming result fields
- [collect() function](../syntax/functions/collect.html) - Creating collections in results
- [Data Types](data-types.html) - Type compatibility information
- [Optional Fields](optional-fields.html) - Working with nullable fields

<!-- <footer> -->
<!-- <nav> -->
[Data types](data-types.html) [Optional fields](optional-fields.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->