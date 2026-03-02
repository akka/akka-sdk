<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View query syntax](index.html)
- [GROUP BY](group-by.html)

<!-- </nav> -->

# GROUP BY

The `GROUP BY` clause groups rows that have the same values in specified columns into summary rows. This is typically used with the `collect()` function to create collections of values from grouped data.

## <a href="about:blank#_syntax"></a> Syntax

```sql
GROUP BY <column> [, <column>...]
```

## <a href="about:blank#_elements"></a> Elements

`column` A column name or expression by which to group the results. Multiple columns can be specified, separated by commas.

## <a href="about:blank#_features"></a> Features

Grouping Groups rows with the same values in the specified columns into a single row in the result.

Collection Creation When combined with the `collect()` function, creates arrays or collections of values from the grouped rows. A collection will also be implicitly derived when a wildcard `*` is aliased to a list field in the result object (for example, `* AS someList`).

## <a href="about:blank#_examples"></a> Examples

Group by a single column and collect products
```sql
SELECT category, collect(*) AS products
FROM products
GROUP BY category
```
Group by a single column and collect specific fields
```sql
SELECT category, collect(name) AS productNames
FROM products
GROUP BY category
```
Group by multiple columns
```sql
SELECT manufacturer, category, collect(*) AS products
FROM products
GROUP BY manufacturer, category
```
Group with distinct values
```sql
SELECT category, collect(distinct name) AS uniqueProductNames
FROM products
GROUP BY category
```
Group by with projection into a complex structure
```sql
SELECT
  category,
  (
    collect(name) AS productNames,
    collect((price, manufacturer) AS productDetails) AS products,
    collect(distinct manufacturer) AS uniqueManufacturers
  ) AS categoryData
FROM products
GROUP BY category
```

## <a href="about:blank#_notes"></a> Notes

- The `GROUP BY` clause groups rows with identical values in the specified columns
- All non-aggregated columns in the `SELECT` clause should appear in the `GROUP BY` clause
- When using `GROUP BY`, each column in the `SELECT` list should either be included in the `GROUP BY` clause or be used with the `collect()` function
- The `collect()` function is the primary means to create collections of values from grouped rows
- Use `collect(distinct …​)` to eliminate duplicate values in the resulting collections

## <a href="about:blank#_result_transformation"></a> Result Transformation

When using `GROUP BY` with `collect()`, the results are transformed into a hierarchical structure. The grouping columns become fields in the result objects, and the collected values become arrays or nested objects.

This transformation is particularly useful for representing one-to-many relationships in your query results, allowing you to construct complex data structures directly from your query.

## <a href="about:blank#_current_limitations"></a> Current Limitations

- Currently, the only aggregation supported with `GROUP BY` is collection creation using the `collect()` function
- Traditional aggregation functions like `count()`, `sum()`, `avg()`, etc. are not yet supported within grouped results
- The `total_count()` function can be used to get a count of all matching rows, but not per group

## <a href="about:blank#_related_features"></a> Related Features

- [SELECT clause](select.html) - Specifies what data to retrieve
- [collect() function](functions/collect.html) - Creates collections from grouped rows
- [DISTINCT keyword](functions/distinct.html) - Eliminates duplicate values
- [total_count() function](functions/total-count.html) - Counts total matching rows

<!-- <footer> -->
<!-- <nav> -->
[ORDER BY](order-by.html) [JOIN](join.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->