<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Operators](index.html)
- [LIKE](like.html)

<!-- </nav> -->

# LIKE

The `LIKE` operator performs pattern matching on text values, allowing you to search for strings that match a specified pattern. It supports wildcards for flexible text matching.

## <a href="about:blank#_syntax"></a> Syntax

```sql
<column> LIKE <pattern>
```

## <a href="about:blank#_elements"></a> Elements

`column` The text column or expression to match against the pattern.

`pattern` A string pattern that can include wildcard characters:

- `%` (percent sign) - Matches any sequence of zero or more characters
- `_` (underscore) - Matches any single character

## <a href="about:blank#_features"></a> Features

Pattern Matching Searches for text values that match a specific pattern with wildcards.

Prefix Matching Efficiently matches text that begins with a specific prefix using `'prefix%'` patterns.

Suffix Matching Efficiently matches text that ends with a specific suffix using `'%suffix'` patterns.

## <a href="about:blank#_examples"></a> Examples

Match names starting with "Jo"
```sql
SELECT * FROM customers
WHERE name LIKE 'Jo%'
```
Match email addresses ending with ".com"
```sql
SELECT * FROM customers
WHERE email LIKE '%.com'
```
Match product codes with specific format (3 characters, then underscore, then 2 digits)
```sql
SELECT * FROM products
WHERE code LIKE '____%'
```

## <a href="about:blank#_notes"></a> Notes

- For index efficiency, the pattern must have a non-wildcard prefix or suffix
- Patterns like `'%text%'` (containing text anywhere) are not supported due to indexing limitations
- Only literal string patterns are supported; patterns cannot be specified as parameters
- The LIKE operation is case-sensitive unless the database is configured otherwise
- For more advanced text searching capabilities, consider using the `text_search()` function

## <a href="about:blank#_indexing_requirements"></a> Indexing Requirements

For optimal performance, LIKE operations require specific index structures:

- `column LIKE 'prefix%'` - Requires a prefix index on the column
- `column LIKE '%suffix'` - Requires a suffix index on the column
Patterns that don’t have a non-wildcard prefix or suffix cannot be efficiently indexed and are therefore not supported.

## <a href="about:blank#_limitations"></a> Limitations

The View query language implementation of LIKE has the following limitations:

- Patterns must include a non-wildcard prefix or suffix
- Only constant patterns with literal strings are supported
- Patterns cannot be specified using parameters
- Escape characters for literal `%` or `_` are not currently supported
- Case-insensitive LIKE operations (ILIKE) are not supported

## <a href="about:blank#_related_features"></a> Related Features

- [text_search() function](../functions/text-search.html) - More powerful language-aware text search
- [WHERE clause](../where.html) - Used with LIKE for filtering

<!-- <footer> -->
<!-- <nav> -->
[= ANY](any.html) [IS NULL/IS NOT NULL](is-null.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->