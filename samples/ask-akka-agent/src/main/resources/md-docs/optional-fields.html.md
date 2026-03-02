<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View concepts](index.html)
- [Optional fields](optional-fields.html)

<!-- </nav> -->

# Optional Fields

In View queries and data models, optional fields represent values that may or may not be present. This page explains how to work with optional fields in Views, including representing them in Java, querying them, and handling them in results.

## <a href="about:blank#_representing_optional_fields_in_java"></a> Representing Optional Fields in Java

There are several ways to represent optional fields in your View data models:

### <a href="about:blank#_non_primitive_types"></a> Non-primitive Types

Use Java’s non-primitive (reference) types instead of primitives:

```java
// Instead of this (primitive)
public record Customer(String id, String name, int age) { }

// Use this (reference type can be null)
public record Customer(String id, String name, Integer age) { }
```
When a field is null, it indicates the value is missing.

### <a href="about:blank#_java_optional"></a> Java Optional

Wrap fields in `java.util.Optional`:

```java
public record Customer(
    String id,
    String name,
    Optional<String> phoneNumber,
    Optional<Address> address
) { }
```
This approach makes the optionality explicit in the type system.

### <a href="about:blank#_nested_objects"></a> Nested Objects

For nested fields, the entire nested object can be null, or fields within it can be null:

```java
public record Customer(
    String id,
    String name,
    Address address // address might be null
) { }

public record Address(
    String street,
    String city,    // city might be null
    String country
) { }
```

## <a href="about:blank#_querying_optional_fields"></a> Querying Optional Fields

### <a href="about:blank#_using_is_null_is_not_null"></a> Using IS NULL / IS NOT NULL

To find rows where a field is missing (null) or present (not null), use the `IS NULL` and `IS NOT NULL` operators:

```sql
-- Find customers without a phone number
SELECT * FROM customers
WHERE phoneNumber IS NULL

-- Find customers with a phone number
SELECT * FROM customers
WHERE phoneNumber IS NOT NULL
```

### <a href="about:blank#_querying_nested_optional_fields"></a> Querying Nested Optional Fields

For optional nested fields, there are two ways a field can be null:

1. The parent object is null
2. The specific field within the parent is null

```sql
-- Find customers where the address is missing
SELECT * FROM customers
WHERE address IS NULL

-- Find customers with an address but no city specified
SELECT * FROM customers
WHERE address IS NOT NULL AND address.city IS NULL
```

### <a href="about:blank#_comparing_optional_values"></a> Comparing Optional Values

Standard comparison operators (`=`, `!=`, `>`, etc.) only work on non-null values. To properly handle both null and non-null cases, combine `IS NULL` / `IS NOT NULL` with standard comparisons:

```sql
-- Find customers with a specific phone number or no phone number
SELECT * FROM customers
WHERE phoneNumber = :phone OR phoneNumber IS NULL
```

## <a href="about:blank#_optional_fields_in_results"></a> Optional Fields in Results

When mapping query results to Java types, optional fields should be represented appropriately:

### <a href="about:blank#_nullable_reference_types"></a> Nullable Reference Types

For fields that might be null:

```java
public record CustomerResult(
    String id,
    String name,
    String phoneNumber,  // might be null
    Address address      // might be null
) { }
```

### <a href="about:blank#_java_optional_2"></a> Java Optional

For explicit optionality:

```java
public record CustomerResult(
    String id,
    String name,
    Optional<String> phoneNumber,
    Optional<Address> address
) { }
```

## <a href="about:blank#_default_values_for_missing_fields"></a> Default Values for Missing Fields

When a field is null in the view data but mapped to a primitive type in a result object, default values are used:

- `int`, `long`, `short`, `byte`: `0`
- `float`, `double`: `0.0`
- `boolean`: `false`
- `char`: `\u0000` (null character)
To avoid unexpected default values, use reference types instead of primitives when a field might be null.

## <a href="about:blank#_best_practices"></a> Best Practices

### <a href="about:blank#_when_to_make_fields_optional"></a> When to Make Fields Optional

Fields should be optional when:

- The information might not be available for all entities
- The field represents optional behavior or characteristics
- The field is added in a schema evolution and might not exist for older entities
- There’s a meaningful semantic difference between "not applicable" and "not provided"

### <a href="about:blank#_handling_optional_fields"></a> Handling Optional Fields

- Use `IS NULL` / `IS NOT NULL` to filter based on presence or absence
- Consider providing default values for missing fields when appropriate
- Be careful when querying nested optional fields - check if the parent is null first
- Use appropriate Java types (reference types or `Optional`) for optional fields in result types
- Document which fields are optional to avoid confusion

### <a href="about:blank#_avoiding_null_issues"></a> Avoiding NULL Issues

- Don’t use `= NULL` or `!= NULL` - these won’t work as expected
- Check for null parent objects before accessing nested fields
- Watch for default value behavior when mapping nulls to primitive types
- Consider using the `Optional` API for safer handling of potentially missing values

## <a href="about:blank#_examples"></a> Examples

### <a href="about:blank#_full_example_customer_with_optional_fields"></a> Full Example: Customer with Optional Fields

Entity definition:

```java
public record Customer(
    String id,
    String name,
    String email,
    Optional<String> phoneNumber,
    Optional<Address> shippingAddress,
    Optional<Address> billingAddress
) { }

public record Address(
    String street,
    String city,
    String zipCode,
    String country
) { }
```
View query with optional field handling:

```sql
-- Find customers with a phone number but no shipping address
SELECT * FROM customers
WHERE phoneNumber IS NOT NULL AND shippingAddress IS NULL

-- Find customers with same billing and shipping city
SELECT * FROM customers
WHERE billingAddress IS NOT NULL
  AND shippingAddress IS NOT NULL
  AND billingAddress.city = shippingAddress.city
```

## <a href="about:blank#_related_features"></a> Related Features

- [IS NULL / IS NOT NULL](../syntax/operators/is-null.html) - Testing for optional values
- [Data Types](data-types.html) - Type system information
- [Result Mapping](result-mapping.html) - Mapping query results to Java types

<!-- <footer> -->
<!-- <nav> -->
[Result mapping](result-mapping.html) [Array types](array-types.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->