<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Operators](index.html)
- [Logical operators](logical.html)

<!-- </nav> -->

# Logical Operators

Logical operators combine conditions and enable complex filtering expressions. They allow you to express compound conditions by combining simpler conditions using boolean logic.

## <a href="about:blank#_available_operators"></a> Available Operators

### <a href="about:blank#and"></a> AND

The `AND` operator combines two conditions and returns true only when both conditions are true.

Syntax:

```sql
<condition1> AND <condition2>
```
Examples:

```sql
category = 'Electronics' AND price < 500
status = 'active' AND createdDate > '2023-01-01'
country = 'USA' AND state = 'California' AND city = 'San Francisco'
```
Truth table:

| Condition 1 | Condition 2 | Result |
| --- | --- | --- |
| true | true | true |
| true | false | false |
| false | true | false |
| false | false | false |

### <a href="about:blank#or"></a> OR

The `OR` operator combines two conditions and returns true when either condition is true or both are true.

Syntax:

```sql
<condition1> OR <condition2>
```
Examples:

```sql
category = 'Electronics' OR category = 'Computers'
price < 100 OR onSale = true
country = 'USA' OR country = 'Canada' OR country = 'Mexico'
```
Truth table:

| Condition 1 | Condition 2 | Result |
| --- | --- | --- |
| true | true | true |
| true | false | true |
| false | true | true |
| false | false | false |

### <a href="about:blank#not"></a> NOT

The `NOT` operator negates a condition, reversing its boolean value.

Syntax:

```sql
NOT <condition>
```
Examples:

```sql
NOT category = 'Electronics'
NOT price > 100
NOT (address.city = 'New York' AND age > 65)
```
Truth table:

| Condition | Result |
| --- | --- |
| true | false |
| false | true |

## <a href="about:blank#_operator_precedence"></a> Operator Precedence

In the View query language, logical operators have the following precedence (from highest to lowest):

1. NOT
2. AND
3. OR
This means that:

- `NOT a AND b` is evaluated as `(NOT a) AND b`
- `a AND b OR c` is evaluated as `(a AND b) OR c`
- `a OR b AND c` is evaluated as `a OR (b AND c)`
Use parentheses to override the default precedence:

```sql
(category = 'Electronics' OR category = 'Computers') AND price < 1000
```

## <a href="about:blank#_complex_expressions"></a> Complex Expressions

Logical operators can be combined to create complex conditions:

```sql
(category = 'Electronics' OR category = 'Computers')
AND (price < 1000 OR onSale = true)
AND NOT discontinued = true
```

## <a href="about:blank#_alternative_expressions"></a> Alternative Expressions

Many complex logical expressions can be simplified using other operators:

| Complex Expression | Simpler Alternative |
| --- | --- |
| `category = 'A' OR category = 'B' OR category = 'C'` | `category IN ('A', 'B', 'C')` |
| `NOT a = b` | `a != b` |
| `NOT a > b` | `a ⇐ b` |
| `NOT a < b` | `a >= b` |
| `NOT a IS NULL` | `a IS NOT NULL` |

## <a href="about:blank#_de_morgans_laws"></a> De Morgan’s Laws

These logical equivalences can help simplify negated expressions:

- `NOT (a AND b)` is equivalent to `NOT a OR NOT b`
- `NOT (a OR b)` is equivalent to `NOT a AND NOT b`

## <a href="about:blank#_notes"></a> Notes

- Use parentheses to make complex expressions more readable and to ensure the correct evaluation order
- When combining many OR conditions with the same column, consider using the IN operator instead
- For checking if a value is in an array column, use the = ANY operator
- Logical operators work with any expressions that evaluate to boolean values

## <a href="about:blank#_related_features"></a> Related Features

- [Operators Overview](index.html) - All available operators
- [Comparison Operators](comparison.html) - =, !=, >, <, etc.
- [IN Operator](in.html) - Shorthand for multiple OR conditions with equality
- [= ANY Operator](any.html) - Tests if a value is in an array
- [WHERE Clause](../where.html) - Using logical operators for filtering

<!-- <footer> -->
<!-- <nav> -->
[Comparison operators](comparison.html) [IN](in.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->