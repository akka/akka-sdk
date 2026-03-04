<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Functions](index.html)
- [count()](count.html)

<!-- </nav> -->

# count(*)

The `count(*)` function returns the number of rows that match the query criteria. In the current implementation, it has specific limitations and usage restrictions.

## <a href="about:blank#_syntax"></a> Syntax

```sql
count(*) [AS alias]
```

## <a href="about:blank#_elements"></a> Elements

`AS alias` Optional aliasing for the count result in the query response. If not specified, the count will be available in a field with an implementation-defined name.

## <a href="about:blank#_features"></a> Features

Row Counting Counts all rows that match the query’s WHERE conditions.

## <a href="about:blank#_current_limitations"></a> Current Limitations

In the current implementation, the `count(*)` function has several important limitations:

Limited Use Cases The `count(*)` function can only be used for counting all matching rows in a simple query, similar to `total_count()`. It cannot be used with `GROUP BY` for counting rows per group.

Not for Aggregation Unlike in standard SQL, `count(*)` cannot currently be used as an aggregation function within grouped results.

No Distinct Support `count(distinct column)` syntax is not currently supported.

No Column Counting `count(column_name)` for counting non-NULL values is not currently supported.

## <a href="about:blank#_examples"></a> Examples

Basic usage
```sql
SELECT count(*) AS customerCount FROM customers
```
With WHERE condition
```sql
SELECT count(*) AS activeCustomers FROM customers WHERE status = 'active'
```

## <a href="about:blank#_notes"></a> Notes

- For total counts with pagination, prefer using the `total_count()` function which is specifically designed for this purpose
- For checking if additional pages exist, use the `has_more()` function

## <a href="about:blank#_related_features"></a> Related Features

- [total_count() function](total-count.html) - Returns the total count of rows matching the query
- [has_more() function](has-more.html) - Checks if more results exist
- [GROUP BY clause](../group-by.html) - For future aggregation features

<!-- <footer> -->
<!-- <nav> -->
[collect()](collect.html) [DISTINCT](distinct.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->