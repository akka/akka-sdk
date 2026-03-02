<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View query syntax](index.html)
- [WHERE](where.html)

<!-- </nav> -->

# WHERE

The `WHERE` clause filters query results based on specified conditions. It allows you to retrieve only the rows that satisfy the filtering criteria, enabling precise data selection.

## <a href="about:blank#_syntax"></a> Syntax

```sql
WHERE <condition> [AND|OR <condition>...]
```

## <a href="about:blank#_elements"></a> Elements

`condition` An expression that evaluates to true or false. Only rows for which the condition evaluates to true are included in the result.

## <a href="about:blank#_supported_operators"></a> Supported operators

### <a href="about:blank#_comparison_operators"></a> Comparison operators

The WHERE clause supports various comparison operators for creating conditions:

| Operator | Description | Example |
| --- | --- | --- |
| <a href="operators/comparison.html#equals">`=`</a> | Equals - Tests if values are equal | `name = 'John'` or `status = :statusParam` |
| <a href="operators/comparison.html#not-equals">`!=`</a> | Not equals - Tests if values are not equal | `category != 'Electronics'` |
| <a href="operators/comparison.html#greater-than">`>`</a> | Greater than - Tests if left value is greater | `price > 100` or `created > '2023-01-01'` |
| <a href="operators/comparison.html#greater-equals">`>=`</a> | Greater than or equal to | `price >= 100` or `age >= 18` |
| <a href="operators/comparison.html#less-than">`<`</a> | Less than - Tests if left value is smaller | `price < 50` or `created < :dateParam` |
| <a href="operators/comparison.html#less-equals">`⇐`</a> | Less than or equal to | `price ⇐ 50` or `count ⇐ 10` |

### <a href="about:blank#_logical_operators"></a> Logical operators

Logical operators combine multiple conditions:

| Operator | Description | Example |
| --- | --- | --- |
| <a href="operators/logical.html#and">`AND`</a> | Requires both conditions to be true | `category = 'Books' AND price < 20` |
| <a href="operators/logical.html#or">`OR`</a> | Requires at least one condition to be true | `status = 'new' OR status = 'sale'` |
| <a href="operators/logical.html#not">`NOT`</a> | Negates a condition | `NOT price > 100` or `NOT (status = 'deleted')` |

### <a href="about:blank#_set_membership_operators"></a> Set membership operators

Check if values are members of sets:

| Operator | Description | Example |
| --- | --- | --- |
| <a href="operators/in.html">`IN`</a> | Tests if a value matches any in a list | `category IN ('Books', 'Magazines', 'Comics')` |
| <a href="operators/any.html">`= ANY`</a> | Tests if a value matches any in an array column | `'electronics' = ANY(tags)` or `category = ANY(:categoryList)` |

### <a href="about:blank#_null_testing"></a> Null testing

Operators for checking null values:

| Operator | Description | Example |
| --- | --- | --- |
| <a href="operators/is-null.html">`IS NULL`</a> | Tests if a value is NULL (missing) | `phoneNumber IS NULL` |
| <a href="operators/is-null.html">`IS NOT NULL`</a> | Tests if a value is not NULL (present) | `email IS NOT NULL` |

### <a href="about:blank#_pattern_matching"></a> Pattern matching

Operators for string pattern matching:

| Operator | Description | Example |
| --- | --- | --- |
| <a href="operators/like.html">`LIKE`</a> | Pattern matching with wildcards | `name LIKE 'Jo%'` or `code LIKE 'ABC_%'` |

### <a href="about:blank#_text_search_function"></a> Text search function

Advanced text searching capabilities:

| Function | Description | Example |
| --- | --- | --- |
| <a href="functions/text-search.html">`text_search()`</a> | Language-aware text search with word tokenization | `text_search(description, :searchTerms, 'english')` |

## <a href="about:blank#_examples"></a> Examples

### <a href="about:blank#_basic_filtering"></a> Basic filtering

Simple equality filter
```sql
SELECT * FROM products
WHERE category = 'Electronics'
```
Numeric comparison
```sql
SELECT * FROM products
WHERE price < 100
```
Date comparison
```sql
SELECT * FROM customers
WHERE joinDate > '2023-01-01'
```

### <a href="about:blank#_multiple_conditions"></a> Multiple conditions

Combining conditions with AND
```sql
SELECT * FROM products
WHERE category = 'Electronics' AND price < 500 AND inStock = true
```
Combining conditions with OR
```sql
SELECT * FROM customers
WHERE region = 'Europe' OR region = 'Asia'
```
Using parentheses for complex logic
```sql
SELECT * FROM products
WHERE (category = 'Electronics' OR category = 'Computers')
  AND price < 1000
  AND NOT discontinued = true
```

### <a href="about:blank#_working_with_null_values"></a> Working with NULL values

Finding missing values
```sql
SELECT * FROM customers
WHERE phoneNumber IS NULL
```
Finding present values
```sql
SELECT * FROM customers
WHERE address IS NOT NULL AND address.street IS NOT NULL
```

### <a href="about:blank#_using_sets_and_arrays"></a> Using sets and arrays

Testing membership in a fixed list
```sql
SELECT * FROM products
WHERE category IN ('Electronics', 'Computers', 'Accessories')
```
Testing membership in a parameter list
```sql
SELECT * FROM products
WHERE category IN (:category1, :category2, :category3)
```
Testing membership in an array column
```sql
SELECT * FROM products
WHERE :searchTag = ANY(tags)
```
Testing membership with an array parameter
```sql
SELECT * FROM customers
WHERE status = ANY(:statusList)
```

### <a href="about:blank#_text_searching"></a> Text searching

Pattern matching with prefix
```sql
SELECT * FROM customers
WHERE email LIKE 'john.%'
```
Pattern matching with suffix
```sql
SELECT * FROM customers
WHERE email LIKE '%.com'
```
Full-text search
```sql
SELECT * FROM articles
WHERE text_search(content, :searchQuery, 'english')
```

### <a href="about:blank#_accessing_nested_fields"></a> Accessing nested fields

Filter based on nested object fields
```sql
SELECT * FROM customers
WHERE address.country = 'USA' AND address.state = 'California'
```
Filter with deeply nested fields
```sql
SELECT * FROM orders
WHERE shipping.address.zipCode = '10001'
```

## <a href="about:blank#_notes"></a> Notes

- Conditions in the WHERE clause are applied before any GROUP BY, ORDER BY, or LIMIT operations
- For complex conditions, use parentheses to explicitly control the order of evaluation
- Type compatibility is enforced - you cannot directly compare values of different types
- NULL values require special handling with IS NULL and IS NOT NULL - standard comparison operators don’t work with NULL
- The LIKE operator requires a non-wildcard prefix or suffix for optimal performance
- For pagination efficiency, ensure your WHERE conditions match available indexes

## <a href="about:blank#_related_features"></a> Related features

- [Operators overview](operators/index.html) - Complete reference for all operators
- [text_search() function](functions/text-search.html) - Advanced text search capabilities
- [JOIN clause](join.html) - Combining data from multiple tables
- [FROM clause](from.html) - Specifies the source table
- [Data types](../concepts/data-types.html) - Type compatibility in conditions
- [Optional fields](../concepts/optional-fields.html) - Working with null values

<!-- <footer> -->
<!-- <nav> -->
[FROM](from.html) [AS](as.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->