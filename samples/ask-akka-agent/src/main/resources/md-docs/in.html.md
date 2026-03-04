<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Operators](index.html)
- [IN](in.html)

<!-- </nav> -->

# IN

The `IN` operator checks if a value matches any value in a specified list or set. It provides a concise way to check for multiple possible values without using multiple OR conditions.

## <a href="about:blank#_syntax"></a> Syntax

```sql
<column_or_expression> IN (<value_list>)
```
Where `<value_list>` is a comma-separated list of:

- Literal values
- Parameters
- A combination of both

## <a href="about:blank#_elements"></a> Elements

`column_or_expression` The column or expression to check against the values in the list.

`value_list` A comma-separated list of values to check against. These can be literals, parameters, or a combination.

## <a href="about:blank#_features"></a> Features

Multiple Value Matching Tests if a value equals any of several specified values without needing multiple OR conditions.

Parameter Support Accepts parameters for dynamic value lists, allowing client code to provide the values at runtime.

Mixed Literal and Parameter Values Supports lists containing both literal values and parameters in the same IN expression.

## <a href="about:blank#_examples"></a> Examples

Using literal values
```sql
SELECT * FROM products
WHERE category IN ('Electronics', 'Computers', 'Accessories')
```
Using parameters
```sql
SELECT * FROM products
WHERE category IN (:category1, :category2, :category3)
```
Using mixed literals and parameters
```sql
SELECT * FROM products
WHERE category IN ('Electronics', :category1, :category2)
```
With nested fields
```sql
SELECT * FROM customers
WHERE address.country IN ('USA', 'Canada', 'Mexico')
```
Combined with other conditions
```sql
SELECT * FROM products
WHERE category IN ('Electronics', 'Computers')
AND price < 1000
```

## <a href="about:blank#_comparison_with_any"></a> Comparison with = ANY

The View query language provides two similar operators for checking membership in a set:

- `IN` - Used with a list of values specified in the query
- `= ANY` - Used with array columns or parameters
The primary differences are:

| IN | = ANY |
| --- | --- |
| `column IN (val1, val2, …​)` | `column = ANY(array_column)` or `value = ANY(array_parameter)` |
| For fixed lists of values | For array columns or array parameters |
| Values specified directly in the query | Values come from an array column or parameter |

## <a href="about:blank#_notes"></a> Notes

- The `IN` operator is equivalent to multiple OR conditions combined with equals (=) operations
- All values in the list must be of compatible types with the column being compared
- Performance is typically better with `IN` than with multiple OR conditions
- The order of values in the list doesn’t affect the results
- Empty lists are not allowed in the `IN` operator

## <a href="about:blank#_related_features"></a> Related Features

- [= ANY operator](any.html) - Checks if a value matches any element in an array
- [WHERE clause](../where.html) - Used with IN for filtering
- [OR operator](logical.html#or) - Alternative way to check multiple conditions

<!-- <footer> -->
<!-- <nav> -->
[Logical operators](logical.html) [= ANY](any.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->