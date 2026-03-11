<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View query syntax](index.html)
- [OFFSET](offset.html)

<!-- </nav> -->

# OFFSET

The `OFFSET` clause specifies the number of rows to skip before starting to return rows from the query. It’s primarily used for implementing pagination or skipping initial results.

## <a href="about:blank#_syntax"></a> Syntax

```sql
OFFSET <offset_value>
```

## <a href="about:blank#_elements"></a> Elements

`offset_value` The number of rows to skip. This can be:

- A literal number (e.g., `10`)
- A parameter (e.g., `:startFrom`)
- A page token offset (e.g., `page_token_offset(:pageToken)`)

## <a href="about:blank#_features"></a> Features

Count-based offset Skips a fixed number of rows based on a numeric value, used for simple pagination.

Token-based offset Uses a token from a previous query to determine the offset position, providing more stable pagination when data changes between requests.

## <a href="about:blank#_examples"></a> Examples

### <a href="about:blank#_basic_usage"></a> Basic usage

Skip 10 rows with a literal value
```sql
SELECT * FROM products
OFFSET 10
```
Skip rows using a parameter
```sql
SELECT * FROM products
OFFSET :startFrom
```

### <a href="about:blank#_count_based_pagination"></a> Count-based pagination

Basic count-based pagination
```sql
SELECT * FROM products
OFFSET 20 LIMIT 10
```
Count-based pagination with sorting
```sql
SELECT * FROM products
ORDER BY price DESC
OFFSET 20 LIMIT 10
```
Count-based pagination with total count
```sql
SELECT * AS products,
       total_count() AS totalCount
FROM products
ORDER BY name
OFFSET :startFrom LIMIT :pageSize
```

### <a href="about:blank#_token_based_pagination"></a> Token-based pagination

Basic token-based pagination
```sql
SELECT * AS products, next_page_token() AS nextPageToken
FROM products
OFFSET page_token_offset(:pageToken)
LIMIT 10
```
Token-based pagination with more indicators
```sql
SELECT * AS products,
       next_page_token() AS nextPageToken,
       has_more() AS hasMoreProducts
FROM products
OFFSET page_token_offset(:pageToken)
LIMIT 10
```
Complete token-based pagination example
```sql
SELECT * AS products,
       next_page_token() AS nextPageToken,
       has_more() AS hasMore,
       total_count() AS totalCount
FROM products
WHERE category = :category
OFFSET page_token_offset(:pageToken)
LIMIT :pageSize
```

## <a href="about:blank#_implementation_in_java"></a> Implementation in Java

### <a href="about:blank#_count_based_pagination_2"></a> Count-based pagination

Parameter class for count-based pagination
```java
public record PageRequest(String category, int offset, int pageSize) {}
```
Query method for count-based pagination
```java
@Query("""
    SELECT * AS products,
           total_count() AS totalCount
    FROM products
    WHERE category = :category
    ORDER BY name
    OFFSET :offset LIMIT :pageSize
    """)
public QueryEffect<ProductsPage> getProductsPage(PageRequest request) {
  return queryResult();
}
```
Response type for count-based pagination
```java
public record ProductsPage(
  List<Product> products,
  int totalCount
) {}
```

### <a href="about:blank#_token_based_pagination_2"></a> Token-based pagination

Parameter class for token-based pagination
```java
public record TokenPageRequest(String category, String pageToken, int pageSize) {}
```
Query method for token-based pagination
```java
@Query("""
    SELECT * AS products,
           next_page_token() AS nextPageToken,
           has_more() AS hasMore
    FROM products
    WHERE category = :category
    OFFSET page_token_offset(:pageToken)
    LIMIT :pageSize
    """)
public QueryEffect<TokenProductsPage> getProductsWithToken(TokenPageRequest request) {
  return queryResult();
}
```
Response type for token-based pagination
```java
public record TokenProductsPage(
  List<Product> products,
  String nextPageToken,
  boolean hasMore
) {}
```
Client usage for token-based pagination
```java
// First page request uses empty token
TokenPageRequest request = new TokenPageRequest("Electronics", "", 10);
TokenProductsPage response = client.forView()
  .method(ProductView::getProductsWithToken)
  .invoke(request);

// Next page uses token from previous response
if (!response.nextPageToken().isEmpty()) {
  TokenPageRequest nextRequest = new TokenPageRequest(
    "Electronics",
    response.nextPageToken(),
    10
  );
}
```

## <a href="about:blank#_notes"></a> Notes

- The `offset_value` must be non-negative
- Count-based offsets (numeric literals or parameters) can lead to inconsistent results if data changes between queries
- Count-based offsets work together with `ORDER BY` to provide consistent ordering
- Token-based pagination is more resilient to data changes but does not support `ORDER BY`
- For token-based pagination, use an empty string as the token for the first page request
- With token-based pagination, the last page is reached when an empty token is returned
- Token-based pagination is generally preferred for production applications dealing with frequently changing data

## <a href="about:blank#_performance_considerations"></a> Performance considerations

- Token-based pagination typically provides better performance for deep pagination (many pages into the result set)
- Consider using token-based pagination for mobile applications and APIs where data consistency between requests is important
- For user interfaces that need to show specific page numbers, count-based offsets may still be necessary
- If showing total counts, be aware that computing the count may be expensive for large result sets

## <a href="about:blank#_related_features"></a> Related features

- [LIMIT clause](limit.html) - Limits the maximum number of returned rows
- [ORDER BY clause](order-by.html) - Sorts results before applying the offset (count-based pagination only)
- [Pagination](../concepts/pagination.html) - Complete guide to pagination approaches
- [page_token_offset() function](functions/page-token-offset.html) - Implements token-based pagination
- [next_page_token() function](functions/next-page-token.html) - Generates tokens for pagination
- [has_more() function](functions/has-more.html) - Checks if more results exist

<!-- <footer> -->
<!-- <nav> -->
[LIMIT](limit.html) [ORDER BY](order-by.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->