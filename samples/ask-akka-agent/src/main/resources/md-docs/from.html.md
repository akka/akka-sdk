<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View query syntax](index.html)
- [FROM](from.html)

<!-- </nav> -->

# FROM

The `FROM` clause specifies the data source for your query, indicating which view table to retrieve data from. In multi-table views, it establishes the primary source table that can be joined with other tables.

## <a href="about:blank#_syntax"></a> Syntax

```sql
FROM <table_name>
```

## <a href="about:blank#_elements"></a> Elements

`table_name` The name of the view table to query. This corresponds to a `TableUpdater` defined in your View. Table names can be specified with the `@Table` annotation or can be automatically derived for single table views.

## <a href="about:blank#_features"></a> Features

Table selection Specifies the primary source table for the query. This table contains the rows that will be examined by the query.

Derived tables In advanced scenarios, a subquery can be used in place of a table name to create a derived table.

## <a href="about:blank#_table_names"></a> Table names

Table names in a View query correspond to the name specified in the `@Table` annotation on a `TableUpdater` class. Single table views may omit the `@Table` annotation.

## <a href="about:blank#_examples"></a> Examples

Basic FROM clause
```sql
SELECT * FROM customers
```
Using a table name explicitly defined with `@Table("customer_profiles")`
```sql
SELECT * FROM customer_profiles
```
Referencing table in a join context
```sql
SELECT customers.name, orders.id
FROM customers
JOIN orders ON orders.customerId = customers.id
```
Using a derived table (advanced)
```sql
SELECT * FROM (
  SELECT name, address
  FROM customers
  WHERE active = true
) AS active_customers
```

## <a href="about:blank#_notes"></a> Notes

- The `FROM` clause is required in every query
- Table names are case-sensitive
- When using joins, the first table in the FROM clause becomes the left side of the first join
- Table names must correspond to `TableUpdater` classes defined in your View
- If table names contain special characters or spaces, they must be enclosed in backticks

## <a href="about:blank#_related_features"></a> Related Features

- [SELECT clause](select.html) - Specifies what data to retrieve
- [JOIN clause](join.html) - Combines data from multiple tables
- [WHERE clause](where.html) - Filters results based on conditions
- [Table updaters](../concepts/table-updaters.html) - Defining view tables in code

<!-- <footer> -->
<!-- <nav> -->
[SELECT](select.html) [WHERE](where.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->