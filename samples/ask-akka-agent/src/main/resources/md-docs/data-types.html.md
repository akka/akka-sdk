<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View concepts](index.html)
- [Data types](data-types.html)

<!-- </nav> -->

# Data Types

The View query language supports a range of data types that map to Java types in your View components. This page explains the supported data types, how they’re used in queries, and how they map between the query language and Java.

## <a href="about:blank#_supported_data_types"></a> Supported Data Types

View queries support the following data types:

| Query Data Type | Java Type | Description |
| --- | --- | --- |
| Text | `String` | Text strings of any length |
| Integer | `int` / `Integer` | 32-bit signed integers |
| Long | `long` / `Long` | 64-bit signed integers |
| Float | `float` / `Float` | 32-bit floating point numbers |
| Double | `double` / `Double` | 64-bit floating point numbers |
| Boolean | `boolean` / `Boolean` | True or false values |
| Lists | `Collection<T>` and derived types | Collections of values |
| Timestamp | `java.time.Instant` | Point in time with microsecond precision |
| Date and Time | `java.time.ZonedDateTime` | Date and time with timezone information |
| Decimal numbers | `java.math.BigDecimal` | Suitable for finance, currency. |

## <a href="about:blank#_type_mapping"></a> Type Mapping

When executing queries, the View system automatically maps between Java types and query data types:

### <a href="about:blank#_from_java_to_query"></a> From Java to Query

- `String` → Text
- `int` / `Integer` → Integer
- `long` / `Long` → Long
- `float` / `Float` → Float
- `double` / `Double` → Double
- `boolean` / `Boolean` → Boolean
- `Collection<T>` → List of the mapped type of `T`
- `java.time.Instant` → Timestamp
- `java.time.ZonedDateTime` → Date and time
- `java.math.BigDecimal` → Numeric
- Custom types → Complex structure based on fields

### <a href="about:blank#_from_query_to_java"></a> From Query to Java

When query results are mapped to Java objects, the reverse mapping occurs. Fields in your response types must be compatible with the corresponding data in the query result.

## <a href="about:blank#_literals_in_queries"></a> Literals in Queries

Different data types have specific literal formats in queries:

### <a href="about:blank#_text_literals"></a> Text Literals

Text literals are enclosed in single quotes:

```sql
WHERE name = 'John'
WHERE status = 'active'
```

### <a href="about:blank#_numeric_literals"></a> Numeric Literals

Numeric literals are written without quotes:

```sql
WHERE price = 99.99
WHERE quantity = 5
```
It is also possible to explicitly describe the type of a literal:

```sql
WHERE price = 99.99::numeric
WHERE quantity = 5::float
```

### <a href="about:blank#_boolean_literals"></a> Boolean Literals

Boolean literals are written as `true` or `false`:

```sql
WHERE active = true
WHERE discontinued = false
```

### <a href="about:blank#_timestamp_literals"></a> Timestamp Literals

Timestamp literals are written as ISO-8601 formatted strings in single quotes:

```sql
WHERE createdTime > '2023-01-01T00:00:00Z'
```

## <a href="about:blank#_type_conversion"></a> Type Conversion

The View query language generally requires exact type matches:

- You cannot directly compare values of different types (e.g., a text value with a numeric value)
- When working with numeric types, some automatic conversion may occur (e.g., Integer to Long)
- Special handling applies to NULL values, which are compared using `IS NULL` and `IS NOT NULL`

## <a href="about:blank#_complex_and_nested_types"></a> Complex and Nested Types

Views support complex and nested data structures:

### <a href="about:blank#_nested_objects"></a> Nested Objects

Nested objects are represented using dot notation:

```sql
WHERE address.city = 'New York'
WHERE customer.contact.email = :email
```

### <a href="about:blank#_collection_types"></a> Collection Types

Collection fields can be:

- Selected in results: `SELECT tags AS productTags`
- Used with the `= ANY` operator: `WHERE :tag = ANY(tags)`
- Created with the `collect()` function: `collect(name) AS productNames`
For more details see [Array Types](array-types.html)

## <a href="about:blank#_optional_fields"></a> Optional Fields

Fields in a view type can be optional, represented in Java as:

- Java’s non-primitive types (e.g., `Integer` instead of `int`)
- `java.util.Optional<T>` wrapper
- Nested classes with potentially null fields
Optional fields can be queried using the `IS NULL` and `IS NOT NULL` operators:

```sql
WHERE phoneNumber IS NULL
WHERE address IS NOT NULL
```

## <a href="about:blank#_parameters"></a> Parameters

Query parameters use the same type system as other values in the query. For example:

```sql
WHERE category = :categoryParam
WHERE price < :maxPrice
WHERE tags = ANY(:tagList)
```
The Java type of the parameter must be compatible with how it’s used in the query.

## <a href="about:blank#_related_features"></a> Related Features

- [Comparison Operators](../syntax/operators/comparison.html) - Type-compatible comparisons
- [IS NULL / IS NOT NULL](../syntax/operators/is-null.html) - Working with optional values
- [Optional Fields](optional-fields.html) - Detailed information about handling optional data
- [Result Mapping](result-mapping.html) - How query results map to Java types
- [Array Types](array-types.html) - Working with collection data in views

<!-- <footer> -->
<!-- <nav> -->
[Table updaters](table-updaters.html) [Result mapping](result-mapping.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->