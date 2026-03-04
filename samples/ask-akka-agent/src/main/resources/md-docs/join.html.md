<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View query syntax](index.html)
- [JOIN](join.html)

<!-- </nav> -->

# JOIN

The `JOIN` clause combines rows from two or more tables based on a related column between them. This allows you to query and retrieve data from multiple tables in a single query.

## <a href="about:blank#_syntax"></a> Syntax

```sql
[<join_type>] JOIN <table_name> ON <join_condition>
```

## <a href="about:blank#_elements"></a> Elements

`join_type` Optional specification of the type of join to perform. Available options include `INNER` (default), `LEFT`, `RIGHT`, or `FULL`. May also include the optional keyword `OUTER`.

`table_name` The name of the table to join with the main table specified in the `FROM` clause.

`join_condition` A condition that specifies how the tables should be joined, typically by matching column values across tables.

## <a href="about:blank#_join_types"></a> Join Types

The View query language supports several join types:

`INNER JOIN` (or just `JOIN`) Returns rows when there is a match in both tables. Rows with no match in the other table are excluded.

`LEFT [OUTER] JOIN` Returns all rows from the left table, and the matched rows from the right table. When there is no match, NULL values appear for columns from the right table.

`RIGHT [OUTER] JOIN` Returns all rows from the right table, and the matched rows from the left table. When there is no match, NULL values appear for columns from the left table.

`FULL [OUTER] JOIN` Returns rows when there is a match in one of the tables. Combines the effect of LEFT and RIGHT joins.

## <a href="about:blank#_examples"></a> Examples

Basic INNER JOIN
```sql
SELECT customers.name, orders.id
FROM customers
JOIN orders ON orders.customerId = customers.id
```
LEFT JOIN (keeping all customers)
```sql
SELECT customers.name, orders.id
FROM customers
LEFT JOIN orders ON orders.customerId = customers.id
```
RIGHT JOIN (keeping all orders)
```sql
SELECT customers.name, orders.id
FROM customers
RIGHT JOIN orders ON orders.customerId = customers.id
```
FULL JOIN (keeping all records)
```sql
SELECT customers.name, orders.id
FROM customers
FULL JOIN orders ON orders.customerId = customers.id
```
Multiple JOINs
```sql
SELECT customers.name, orders.id, products.name AS product_name
FROM customers
JOIN orders ON orders.customerId = customers.id
JOIN products ON orders.productId = products.id
```
JOIN with complex condition
```sql
SELECT customers.name, orders.id
FROM customers
JOIN orders ON orders.customerId = customers.id AND orders.active = true
```

## <a href="about:blank#_notes"></a> Notes

- The `INNER` keyword is optional in `INNER JOIN` - simply using `JOIN` implies an inner join
- The `OUTER` keyword is optional in `LEFT OUTER JOIN`, `RIGHT OUTER JOIN`, and `FULL OUTER JOIN`
- JOIN conditions can use AND/OR operators for more complex joining logic
- Multiple JOINs can be chained to combine data from more than two tables
- When column names are ambiguous (exist in multiple tables), they must be qualified with the table name

## <a href="about:blank#_related_features"></a> Related Features

- [FROM clause](from.html) - Specifies the main table for the query
- [WHERE clause](where.html) - Filters the joined results
- [SELECT clause](select.html) - Selects data from the joined tables
- [Advanced Views](../concepts/advanced-views.html) - Creating multi-table views

<!-- <footer> -->
<!-- <nav> -->
[GROUP BY](group-by.html) [Operators](operators/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->