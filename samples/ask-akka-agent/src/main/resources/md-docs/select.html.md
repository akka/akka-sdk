<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View query syntax](index.html)
- [SELECT](select.html)

<!-- </nav> -->

# SELECT

The `SELECT` clause specifies which data to retrieve from a view, allowing you to choose specific columns, all columns, or transform the data with expressions, functions, and projections.

## <a href="about:blank#_syntax"></a> Syntax

```sql
SELECT <select_list>
```
Where `<select_list>` is one or more of the following expressions, separated by commas:

```sql
* [AS alias]                                 -- select all columns
table_name.* [AS alias]                      -- select all columns from a specific table
column_name [AS alias]                       -- select a specific column
path.to.nested.field [AS alias]              -- select a nested field
:parameter [AS alias]                        -- include a parameter value
(select_list) AS alias                       -- create a nested object
collect([distinct] select_list) AS alias     -- create a collection
next_page_token() AS alias                   -- get the next page token
has_more() AS alias                          -- check if there are more results
total_count() AS alias                       -- get the total count of results
count(*) [AS alias]                          -- count all matching rows
```

## <a href="about:blank#_features"></a> Features

Column selection Select specific columns or nested fields from the view data. You can select individual columns by name or use dot notation to access nested fields.

Wildcard selection Select all columns using the `*` wildcard character. You can also select all columns from a specific table in a join using `table_name.*`.

Column aliasing Rename columns in the result set using the `AS` keyword. This is particularly useful when mapping to field names in your response types.

Parameter values Include request parameter values directly in the response using the parameter syntax (`:parameterName`). This allows you to pass through values from the request to the response.

Object construction Create nested objects by grouping selected columns with parentheses followed by an alias. This feature lets you construct complex nested data structures directly in the query. The columns or expressions inside the parentheses become fields of the nested object, and their names can be customized with aliases.

For example, `(name, email) AS contact_info` creates a nested object with `name` and `email` fields, while `(name AS fullName, email AS contactEmail) AS contact_info` creates a nested object with custom field names.

Collection construction Create collections of values or objects using the `collect()` function. This is typically used with `GROUP BY` to collect multiple rows into arrays within a single result row.

The `collect()` function can operate on single columns to create arrays of values, or on multiple columns/nested objects to create arrays of complex objects. You can also use `collect(distinct …​)` to remove duplicate values from the collection.

This feature enables hierarchical data representation, allowing you to represent one-to-many relationships in your query results.

Pagination Support Use special functions like `next_page_token()`, `has_more()`, and `total_count()` to support pagination:

- `next_page_token()` generates a token for retrieving the next page of results
- `has_more()` returns a boolean indicating if there are more results available
- `total_count()` provides the total number of matching rows regardless of pagination

## <a href="about:blank#_examples"></a> Examples

Select all columns
```sql
SELECT * FROM customers
```
Select specific columns
```sql
SELECT id, name, email FROM customers
```
Select with aliases
```sql
SELECT id, name AS customer_name FROM customers
```
Select nested fields
```sql
SELECT id, name, address.city FROM customers
```
Select with parameter inclusion
```sql
SELECT :requestId, id, name FROM customers
```
Create a nested object
```sql
SELECT (name, email) AS contact_info FROM customers
```
Create nested objects with specific field names
```sql
SELECT id, (name AS fullName, email AS contactEmail) AS contact_info FROM customers
```
Create collections with collect()
```sql
SELECT popularity, collect(*) AS products
FROM all_products
GROUP BY popularity
```
Create collections of specific fields
```sql
SELECT category, collect(name) AS productNames
FROM products
GROUP BY category
```
Use pagination helpers
```sql
SELECT * AS customers, next_page_token() AS nextPageToken
FROM customers
LIMIT 10
```

## <a href="about:blank#_notes"></a> Notes

- The asterisk (`*`) selects all columns from the table
- Column names or aliases in the SELECT clause must match field names in the return type of the query method
- Nested fields are accessed using dot notation (for example, `address.city`)
- For a collection result, use `* AS collectionName` to match your return type’s collection field
- If column names contain special characters or spaces, they must be enclosed in backticks

## <a href="about:blank#_related_features"></a> Related features

- [FROM clause](from.html) - Specifies the data source for the query
- [AS keyword](as.html) - Column aliasing
- [GROUP BY clause](group-by.html) - Grouping rows for aggregation
- [Pagination](../concepts/pagination.html) - Using pagination functions and techniques

<!-- <footer> -->
<!-- <nav> -->
[Query](query.html) [FROM](from.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->