<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Operators](index.html)
- [Comparison operators](comparison.html)

<!-- </nav> -->

# Comparison Operators

Comparison operators compare values and return boolean results. They form the foundation of filtering conditions in the WHERE clause and join conditions in the JOIN clause.

## <a href="about:blank#_syntax"></a> Syntax

```sql
<value1> <operator> <value2>
```

## <a href="about:blank#_available_operators"></a> Available Operators

### <a href="about:blank#equals"></a> Equals (=)

Tests if two values are equal.

Syntax:

```sql
<value1> = <value2>
```
Examples:

```sql
name = 'John'
age = 30
status = :statusParameter
customer.address.city = 'New York'
```

### <a href="about:blank#not-equals"></a> Not Equals (!=)

Tests if two values are not equal.

Syntax:

```sql
<value1> != <value2>
```
Examples:

```sql
category != 'Electronics'
status != 'inactive'
price != :targetPrice
```

### <a href="about:blank#greater-than"></a> Greater Than (>)

Tests if the first value is greater than the second value.

Syntax:

```sql
<value1> > <value2>
```
Examples:

```sql
price > 100
age > 18
createdDate > '2023-01-01'
```

### <a href="about:blank#greater-equals"></a> Greater Than or Equal To (>=)

Tests if the first value is greater than or equal to the second value.

Syntax:

```sql
<value1> >= <value2>
```
Examples:

```sql
price >= 100
age >= 18
rating >= :minimumRating
```

### <a href="about:blank#less-than"></a> Less Than (<)

Tests if the first value is less than the second value.

Syntax:

```sql
<value1> < <value2>
```
Examples:

```sql
price < 50
age < 65
expireDate < :currentDate
```

### <a href="about:blank#less-equals"></a> Less Than or Equal To (⇐)

Tests if the first value is less than or equal to the second value.

Syntax:

```sql
<value1> <= <value2>
```
Examples:

```sql
price <= 50
age <= 65
endDate <= '2023-12-31'
```

## <a href="about:blank#_type_compatibility"></a> Type Compatibility

Comparison operators require compatible types on both sides:

- String values can only be compared with other strings
- Numeric values (int, long, float, double) can be compared with other numeric values
- Boolean values can only be compared with other booleans
- Date/timestamp values can only be compared with other date/timestamp values

## <a href="about:blank#_comparison_with_null"></a> Comparison with NULL

Comparison operators (`=`, `!=`, `>`, etc.) do not work correctly with NULL values. Use the [IS NULL / IS NOT NULL](is-null.html) operators instead:

```sql
-- Incorrect: does not find rows where email is NULL
email = NULL

-- Correct: finds rows where email is NULL
email IS NULL
```

## <a href="about:blank#_usage_in_where_clause"></a> Usage in WHERE Clause

Comparison operators are commonly used in the WHERE clause to filter results:

```sql
SELECT * FROM products
WHERE price > 100 AND category = 'Electronics'
```

## <a href="about:blank#_usage_in_join_conditions"></a> Usage in JOIN Conditions

Comparison operators define how tables are joined together:

```sql
SELECT c.name, o.id
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id
```

## <a href="about:blank#_notes"></a> Notes

- String comparisons are case-sensitive
- Date/time comparisons work with ISO format strings (`'YYYY-MM-DD'`, `'YYYY-MM-DDThh:mm:ss'`)
- When comparing values of different but compatible numeric types, implicit conversion may occur
- Field paths can be used on either side of a comparison operator

## <a href="about:blank#_related_features"></a> Related Features

- [Operators Overview](index.html) - All available operators
- [Logical Operators](logical.html) - AND, OR, NOT operators
- [WHERE clause](../where.html) - Using comparisons for filtering
- [IS NULL / IS NOT NULL](is-null.html) - Testing for NULL values
- [Data Types](../../concepts/data-types.html) - Type compatibility information

<!-- <footer> -->
<!-- <nav> -->
[Operators](index.html) [Logical operators](logical.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->