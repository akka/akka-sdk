<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View query syntax](index.html)
- [ORDER BY](order-by.html)

<!-- </nav> -->

# ORDER BY

The `ORDER BY` clause sorts the query results based on one or more columns in ascending or descending order. It determines the sequence in which rows appear in the query result.

## <a href="about:blank#_syntax"></a> Syntax

```sql
ORDER BY <column> [ASC|DESC] [, <column> [ASC|DESC]]...
```

## <a href="about:blank#_elements"></a> Elements

`column` A column name or expression by which to sort the results. Multiple columns can be specified, separated by commas.

`ASC` Optional keyword indicating ascending order (the default if not specified). Results are sorted from lowest to highest value.

`DESC` Optional keyword indicating descending order. Results are sorted from highest to lowest value.

## <a href="about:blank#_features"></a> Features

Sorting Arranges the result rows based on the values in the specified columns.

Multi-column Ordering Allows sorting by multiple columns, where the second column is used to break ties in the first column, and so on.

Direction Control Supports both ascending and descending order for each column independently.

## <a href="about:blank#_examples"></a> Examples

Sort in ascending order (default)
```sql
SELECT * FROM products
ORDER BY price
```
Sort in descending order
```sql
SELECT * FROM products
ORDER BY price DESC
```
Sort by multiple columns
```sql
SELECT * FROM products
ORDER BY category, price DESC
```
Sort with nested fields
```sql
SELECT * FROM customers
ORDER BY address.city, name
```
Sort with filtering
```sql
SELECT * FROM products
WHERE category = 'Electronics'
ORDER BY price ASC
```
Sort with limiting results
```sql
SELECT * FROM products
ORDER BY price DESC
LIMIT 10
```

## <a href="about:blank#_notes"></a> Notes

- If no `ORDER BY` clause is specified, the results are returned in no guaranteed order
- `ASC` is the default order if not specified
- Sorting is based on the natural order for the data type of the column
- NULL values are typically placed last in ascending sorts and first in descending sorts
- When sorting by multiple columns, the order of columns in the `ORDER BY` clause determines the significance of the sort (primary, secondary, etc.)
- Performance may be affected when sorting by columns that are not indexed

## <a href="about:blank#_ordering_constraints"></a> Ordering Constraints

Some order operations may be rejected if they cannot be efficiently implemented with the view’s index structure. Generally, to order by a field, it should also appear in the `WHERE` conditions or be directly related to fields that do.

For example, if you filter with `WHERE category = :category`, ordering by `price` within that category would be allowed, but ordering by an unrelated field might not be supported.

## <a href="about:blank#_related_features"></a> Related Features

- [WHERE clause](where.html) - Filters results before sorting
- [LIMIT clause](limit.html) - Limits the number of sorted results
- [OFFSET clause](offset.html) - Skips a number of sorted results
- [Pagination](../concepts/pagination.html) - Pagination with sorted results

<!-- <footer> -->
<!-- <nav> -->
[OFFSET](offset.html) [GROUP BY](group-by.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->