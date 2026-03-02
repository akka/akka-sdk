<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Functions](index.html)
- [collect()](collect.html)

<!-- </nav> -->

# collect()

The `collect()` function creates collections or arrays of values from multiple rows in a query result. It is primarily used with `GROUP BY` to aggregate related data into nested structures, enabling hierarchical representations of one-to-many relationships.

## <a href="about:blank#_syntax"></a> Syntax

```sql
collect([distinct] <select_expression>) AS <alias>
```

## <a href="about:blank#_elements"></a> Elements

`distinct` Optional keyword that removes duplicate values from the collection.

`select_expression` The expression to collect into an array. This can be:

- A single column name
- A wildcard `*` to collect all columns
- A composite expression like `(column1, column2)` to create objects within the collection
`AS alias` Required aliasing for the collection in the query result. The alias determines the field name in the response object.

## <a href="about:blank#_features"></a> Features

Data Aggregation Aggregates values from multiple rows into a single array or collection.

Hierarchical Structures Creates nested data structures that represent one-to-many relationships in the results.

Object Collections Can collect not just simple values but also complex objects with multiple fields.

Duplicate Elimination With the `distinct` keyword, removes duplicate values from the collection.

## <a href="about:blank#_examples"></a> Examples

Collect all columns into an array
```sql
SELECT category, collect(*) AS products
FROM products
GROUP BY category
```
Collect a single column into an array
```sql
SELECT category, collect(name) AS productNames
FROM products
GROUP BY category
```
Collect without duplicate values
```sql
SELECT category, collect(distinct manufacturer) AS manufacturers
FROM products
GROUP BY category
```
Collect with custom object structure
```sql
SELECT category, collect(name, price) AS products
FROM products
GROUP BY category
```
Complex nested structure with multiple collections
```sql
SELECT
  category,
  (
    collect(name) AS names,
    collect(price, manufacturer) AS productDetails,
    collect(distinct manufacturer) AS uniqueManufacturers
  ) AS categoryData
FROM products
GROUP BY category
```

## <a href="about:blank#_java_type_mapping"></a> Java Type Mapping

The collect() function maps to Java collection types in your response objects. For example:

```sql
SELECT category, collect(name) AS productNames
FROM products
GROUP BY category
```
Would map to a Java type like:

```java
public record CategoryResponse(
    String category,
    List<String> productNames
) {}
```
For complex objects within collections:

```sql
SELECT category, collect(name, price) AS products
FROM products
GROUP BY category
```
Would map to:

```java
public record CategoryResponse(
    String category,
    List<ProductInfo> products
) {}

public record ProductInfo(
    String name,
    double price
) {}
```

## <a href="about:blank#_notes"></a> Notes

- The `collect()` function is most effective when used with `GROUP BY`
- The alias for `collect()` is required and should match a collection field in your Java response type
- The `distinct` keyword eliminates duplicates based on the entire collected expression
- Collections maintain the order determined by the query’s `ORDER BY` clause, or the natural index order if no explicit ordering is specified
- For complex nested structures, ensure that your Java classes have the correct nesting of fields and collection types

## <a href="about:blank#_related_features"></a> Related Features

- [GROUP BY clause](../group-by.html) - Groups rows for collection
- [SELECT clause](../select.html) - Used with collect() for result structure
- [DISTINCT keyword](distinct.html) - Eliminates duplicate values
- [Result Mapping](../../concepts/result-mapping.html) - How query results map to Java types

<!-- <footer> -->
<!-- <nav> -->
[Functions](index.html) [count()](count.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->