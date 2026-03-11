<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View query syntax](index.html)
- [AS](as.html)

<!-- </nav> -->

# AS

The `AS` keyword creates aliases for columns, expressions, tables, or functions in a query. Aliases provide meaningful names for the query results and are essential for mapping query results to Java objects in the View response.

## <a href="about:blank#_syntax"></a> Syntax

Column or expression alias:

```sql
<column_or_expression> AS <alias>
```
Table alias:

```sql
<table_name> AS <alias>
```

## <a href="about:blank#_elements"></a> Elements

`column_or_expression` A column name, function, or expression that you want to alias.

`table_name` The name of a table that you want to alias.

`alias` A new name to assign to the column, expression, or table. This name will be used in the query result and should match field names in your Java response types.

## <a href="about:blank#_features"></a> Features

Result mapping Maps columns or expressions in the query result to fields in your Java response types, ensuring proper deserialization.

Column renaming Renames columns in the result set to match the required field names or to make them more meaningful.

Structuring results When used with composite expressions, enables creation of nested objects and structured data in the result.

Table references Creates shorter or more meaningful names for tables, especially useful in joins and complex queries.

## <a href="about:blank#_examples"></a> Examples

### <a href="about:blank#_basic_column_aliases"></a> Basic column aliases

Simple column alias
```sql
SELECT name AS customerName FROM customers
```
Alias for a nested field
```sql
SELECT address.city AS customerCity FROM customers
```
Multiple aliases in a single query
```sql
SELECT
  id,
  name AS customerName,
  email AS contactEmail,
  status
FROM customers
```

### <a href="about:blank#_aliases_for_special_elements"></a> Aliases for special elements

Alias for a collection
```sql
SELECT * AS products FROM products
```
Alias for a parameter
```sql
SELECT :requestId AS requestId, name FROM customers
```
Alias for a function result
```sql
SELECT next_page_token() AS nextPageToken FROM products
```

### <a href="about:blank#_aliases_for_nested_structures"></a> Aliases for nested structures

Simple nested object
```sql
SELECT (name, email) AS contactInfo FROM customers
```
Complex nested object with custom field names
```sql
SELECT (
  name AS fullName,
  email AS emailAddress,
  phone AS phoneNumber
) AS contactDetails
FROM customers
```
Multiple nested objects
```sql
SELECT
  id,
  (name, email) AS contactInfo,
  (address.street, address.city, address.zipCode) AS location
FROM customers
```
Nested object with collections
```sql
SELECT
  category,
  (
    collect(name) AS productNames,
    collect(price) AS pricePoints
  ) AS categoryData
FROM products
GROUP BY category
```

### <a href="about:blank#_table_aliases"></a> Table aliases

Basic table alias in JOIN
```sql
SELECT c.name, o.id
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id
```
Multiple table aliases in complex joins
```sql
SELECT
  c.name,
  o.id AS orderId,
  p.name AS productName
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id
JOIN products AS p ON p.id = o.productId
```

## <a href="about:blank#_java_type_mapping"></a> Java type mapping

The alias names in your query must match the field names in your Java response types. For example:

```sql
SELECT
  id,
  name AS customerName,
  (address.street, address.city) AS location
FROM customers
```
Should match a Java type like:

```java
public record CustomerResponse(
  String id,
  String customerName,
  Location location
) {}

public record Location(
  String street,
  String city
) {}
```
For collection results:

```sql
SELECT category, collect(name) AS productNames
FROM products
GROUP BY category
```
Maps to:

```java
public record CategorySummary(
  String category,
  List<String> productNames
) {}
```

## <a href="about:blank#_alias_requirements_and_constraints"></a> Alias requirements and constraints

### <a href="about:blank#_when_aliases_are_required"></a> When aliases are required

Aliases are mandatory in the following cases:

- For composite expressions: `(name, email) AS contactInfo`
- For special functions: `next_page_token() AS nextPageToken`
- When collecting items: `collect(name) AS productNames`
- When using wildcards with a specific field name: `* AS items`

### <a href="about:blank#_naming_constraints"></a> Naming constraints

- Alias names must be valid Java identifiers
- Alias names are case-sensitive and should match your Java field names exactly
- Reserved words should be avoided or enclosed in backticks
- If alias names contain special characters or spaces, they must be enclosed in backticks

## <a href="about:blank#_notes"></a> Notes

- Aliases defined in the SELECT clause can’t be referenced in WHERE clauses
- Table aliases can be referenced in SELECT, WHERE, and ON clauses
- Aliases are primarily for result mapping and don’t affect the underlying data
- For complex nested structures, ensure that your Java classes have matching structure
- The mapping between SQL aliases and Java fields is case-sensitive

## <a href="about:blank#_related_features"></a> Related features

- [SELECT clause](select.html) - Uses aliases for result mapping
- [FROM clause](from.html) - Can include table aliases
- [JOIN clause](join.html) - Often uses table aliases
- [Result mapping](../concepts/result-mapping.html) - How query results map to Java types

<!-- <footer> -->
<!-- <nav> -->
[WHERE](where.html) [LIMIT](limit.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->