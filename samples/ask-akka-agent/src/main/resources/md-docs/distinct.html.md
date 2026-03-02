<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Functions](index.html)
- [DISTINCT](distinct.html)

<!-- </nav> -->

# DISTINCT

The `DISTINCT` keyword eliminates duplicate values in collection results. It is used within the `collect()` function to ensure that only unique values are included in the resulting collection.

## <a href="about:blank#_syntax"></a> Syntax

```sql
collect(distinct <select_expression>) AS <alias>
```

## <a href="about:blank#_elements"></a> Elements

`select_expression` The expression to collect unique values from. This can be a single column, multiple columns, or a composite expression.

`AS alias` Required aliasing for the resulting collection in the query result.

## <a href="about:blank#_features"></a> Features

Duplicate Elimination Removes duplicate values from collections based on the complete expression being collected.

Unique Value Sets Creates collections containing only unique values, useful for categories, tags, or other sets where duplicates are unnecessary.

Composite Expression Support Works with both simple column values and complex expressions, applying uniqueness checks to the entire collected expression.

## <a href="about:blank#_examples"></a> Examples

Collect distinct product names
```sql
SELECT category, collect(distinct name) AS uniqueProductNames
FROM products
GROUP BY category
```
Collect distinct manufacturers
```sql
SELECT category, collect(distinct manufacturer) AS uniqueManufacturers
FROM products
GROUP BY category
```
Collect distinct composite values
```sql
SELECT category, collect(distinct (manufacturer, country)) AS uniqueManufacturerLocations
FROM products
GROUP BY category
```
Complex structure with both distinct and non-distinct collections
```sql
SELECT
  category,
  (
    collect(*) AS allProducts,
    collect(distinct manufacturer) AS uniqueManufacturers
  ) AS categoryData
FROM products
GROUP BY category
```

## <a href="about:blank#_notes"></a> Notes

- Currently, `DISTINCT` is only supported within the `collect()` function
- The `DISTINCT` keyword applies to the entire expression being collected, not just individual fields
- When using composite expressions, uniqueness is determined by considering all values in the expression together
- Unlike some SQL dialects, the View query language does not currently support `SELECT DISTINCT` as a standalone feature
- `DISTINCT` is particularly useful for creating sets of unique categories, tags, or other metadata from related items

## <a href="about:blank#_java_type_mapping"></a> Java Type Mapping

The results of `collect(distinct …​)` map to the same Java collection types as regular `collect()` operations. For example:

```sql
SELECT category, collect(distinct manufacturer) AS uniqueManufacturers
FROM products
GROUP BY category
```
Would map to a Java type like:

```java
public record CategoryResponse(
    String category,
    List<String> uniqueManufacturers
) {}
```

## <a href="about:blank#_related_features"></a> Related Features

- [collect() function](collect.html) - Creates collections from grouped rows
- [GROUP BY clause](../group-by.html) - Groups rows for collection
- [Result Mapping](../../concepts/result-mapping.html) - How query results map to Java types

<!-- <footer> -->
<!-- <nav> -->
[count()](count.html) [text_search()](text-search.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->