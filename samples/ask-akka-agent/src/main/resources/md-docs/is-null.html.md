<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Operators](index.html)
- [IS NULL/IS NOT NULL](is-null.html)

<!-- </nav> -->

# IS NULL / IS NOT NULL

The `IS NULL` and `IS NOT NULL` operators check if a value is missing (NULL) or present in a column. These operators are essential for handling optional fields in your data models.

## <a href="about:blank#_syntax"></a> Syntax

```sql
<column_or_expression> IS NULL
<column_or_expression> IS NOT NULL
```

## <a href="about:blank#_elements"></a> Elements

`column_or_expression` The column or expression to check for NULL or non-NULL value.

## <a href="about:blank#_features"></a> Features

NULL Testing Checks whether a value is NULL (missing) or not NULL (present).

Optional Field Handling Provides a way to filter results based on whether optional fields have values or not.

Three-valued Logic Handles the special NULL value, which is neither equal to nor different from any other value, including another NULL.

## <a href="about:blank#_examples"></a> Examples

Find customers without a phone number
```sql
SELECT * FROM customers
WHERE phoneNumber IS NULL
```
Find customers with a phone number
```sql
SELECT * FROM customers
WHERE phoneNumber IS NOT NULL
```
Find customers with missing address information
```sql
SELECT * FROM customers
WHERE address IS NULL OR address.street IS NULL
```
Combined with other conditions
```sql
SELECT * FROM products
WHERE category = 'Electronics' AND description IS NOT NULL
```
Filter with nested fields
```sql
SELECT * FROM orders
WHERE shippingAddress IS NOT NULL AND billingAddress IS NULL
```

## <a href="about:blank#_null_in_java_types"></a> NULL in Java Types

In the View query language, NULL values correspond to specific representations in Java:

1. For primitive types (int, long, boolean, etc.), NULL is not directly representable
2. For object types (Integer, Long, Boolean, etc.), NULL is represented as `null`
3. For `Optional<T>` types, NULL is represented as an empty Optional
4. For nested objects, NULL can mean the entire object is missing

## <a href="about:blank#_notes"></a> Notes

- NULL values require special comparison operators - they cannot be compared using standard operators like `=` or `!=`
- The expression `column = NULL` will not work as expected; use `column IS NULL` instead
- Similarly, `column != NULL` will not work; use `column IS NOT NULL` instead
- NULL values in Java are represented differently based on the field type:

  - For primitive types, default values are used (0, false, etc.)
  - For reference types, `null` is used
  - For `Optional<T>`, an empty Optional is used
- A NULL in a nested field can indicate either that the field itself is NULL or that a parent object is NULL

## <a href="about:blank#_related_features"></a> Related Features

- [WHERE clause](../where.html) - Used with IS NULL / IS NOT NULL for filtering
- [Optional Fields](../../concepts/optional-fields.html) - Working with optional data in views

<!-- <footer> -->
<!-- <nav> -->
[LIKE](like.html) [Functions](../functions/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->