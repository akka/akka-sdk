<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View query syntax](index.html)
- [Query](query.html)

<!-- </nav> -->

# Query

A View query provides SQL-like syntax for retrieving and filtering data from your Views. It defines what data to select, where to retrieve it from, how to project data into results, and optional criteria to filter, sort, or limit the results.

## <a href="about:blank#_syntax"></a> Syntax

```sql
SELECT <select_expression> [AS alias]
FROM <table_name>
[JOIN <table_name> ON <join_condition>]
[WHERE <filter_conditions>]
[GROUP BY <group_expressions>]
[ORDER BY <order_expressions>]
[OFFSET <offset_value>]
[LIMIT <limit_value>]
```

## <a href="about:blank#_elements"></a> Elements

[SELECT](select.html) (required) Specifies what data to retrieve from the view. You can select specific columns, all columns using `*`, or transform the data with projections and functions.

[FROM](from.html) (required) Specifies the source table to query. This corresponds to the table name defined for the table updater.

[JOIN](join.html) Combines rows from two or more tables based on a related column between them. Various join types (INNER, LEFT, RIGHT, FULL) control how unmatched records are handled.

[WHERE](where.html) Filters results based on specified conditions using comparison operators, logical operators, and expressions.

[GROUP BY](group-by.html) Groups rows that have the same values in specified columns, often used with collect functions to create nested collections.

[ORDER BY](order-by.html) Sorts the result set by one or more columns in ascending or descending order.

[OFFSET](offset.html) Specifies the number of rows to skip before starting to return rows from the query. Used for pagination.

[LIMIT](limit.html) Specifies the maximum number of rows to return. Used for pagination and limiting result size.

## <a href="about:blank#_examples"></a> Examples

Basic query retrieving all fields
```sql
SELECT * FROM customers
```
Query with filtering
```sql
SELECT * FROM customers
WHERE name = :customerName
```
Query with multiple clauses
```sql
SELECT * FROM customers
WHERE address.city = 'New York'
ORDER BY name
LIMIT 10
```
Query with join
```sql
SELECT customers.*, orders.*
FROM customers
JOIN orders ON customers.customerId = orders.customerId
WHERE customers.customerId = :id
```

## <a href="about:blank#_notes"></a> Notes

- The query language is similar to SQL but has some differences in behavior and feature support
- The syntax supports referencing nested fields using dot notation
- Clauses must appear in the order shown in the syntax definition
- Table names correspond to [table updaters](../concepts/table-updaters.html) defined in the View

## <a href="about:blank#_related_features"></a> Related features

- [SELECT clause](select.html) - Detailed information on specifying what data to retrieve
- [FROM clause](from.html) - Information on specifying data sources
- [WHERE clause](where.html) - How to filter results
- [ORDER BY clause](order-by.html) - Sorting results
- [GROUP BY clause](group-by.html) - Grouping related data
- [JOIN operation](join.html) - Combining data from multiple tables
- [Paging with OFFSET and LIMIT](../concepts/pagination.html) - Limiting result sets

<!-- <footer> -->
<!-- <nav> -->
[View query syntax](index.html) [SELECT](select.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->