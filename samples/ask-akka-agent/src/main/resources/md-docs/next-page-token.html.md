<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Functions](index.html)
- [next_page_token()](next-page-token.html)

<!-- </nav> -->

# next_page_token()

The `next_page_token()` function generates an opaque token representing the position after the last returned row in a query result. This token can be used in a subsequent query with `page_token_offset()` to continue retrieving results from that position.

## <a href="about:blank#_syntax"></a> Syntax

```sql
next_page_token() AS alias
```

## <a href="about:blank#_elements"></a> Elements

`AS alias` Required aliasing for the token in the query result. The alias determines the field name in the response object.

## <a href="about:blank#_features"></a> Features

Token Generation Creates an encoded string representing the position in the result set for continuation in a subsequent query.

Pagination State Encapsulates information about sorting, filtering, and position within the result set without exposing implementation details.

Last Page Detection Returns an empty string when there are no more results, indicating the last page has been reached.

## <a href="about:blank#_examples"></a> Examples

Basic usage with required alias
```sql
SELECT * AS products, next_page_token() AS nextPageToken
FROM products
OFFSET page_token_offset(:pageToken)
LIMIT 10
```
With sorting
```sql
SELECT * AS products, next_page_token() AS nextPageToken
FROM products
ORDER BY price DESC
OFFSET page_token_offset(:pageToken)
LIMIT 10
```
With filtering and has_more indicator
```sql
SELECT * AS products,
       next_page_token() AS nextPageToken,
       has_more() AS hasMoreProducts
FROM products
WHERE category = :category
ORDER BY name ASC
OFFSET page_token_offset(:pageToken)
LIMIT 10
```

## <a href="about:blank#_usage_flow"></a> Usage Flow

1. **Include in Query**: Add the function to your SELECT clause with an alias:

```sql
SELECT * AS products, next_page_token() AS nextPageToken
```
2. **First Page Request**: For the first page, use an empty string as the input token:

```java
public record PageRequest(String pageToken) {}

// First request uses empty string
PageRequest request = new PageRequest("");
```
3. **Response Structure**: The query response includes the token:

```java
public record PageResponse(List<Product> products, String nextPageToken) {}
```
4. **Next Page Request**: Use the received token for subsequent requests:

```java
// Next request uses token from previous response
PageRequest nextRequest = new PageRequest(previousResponse.nextPageToken());
```
5. **Last Page Detection**: Check for an empty token to detect the last page:

```java
if (response.nextPageToken().isEmpty()) {
    // Last page reached
}
```

## <a href="about:blank#_notes"></a> Notes

- The `next_page_token()` function must have an alias specified with `AS`
- The token is an opaque string that should not be parsed, modified, or stored long-term
- An empty string token indicates there are no more results
- Tokens are specific to a query structure - they cannot be used with different queries
- Tokens may expire after some time - they are intended for immediate use in pagination
- The function can be used with or without `page_token_offset()`, but they are typically used together

## <a href="about:blank#_related_features"></a> Related Features

- [page_token_offset() function](page-token-offset.html) - Uses the token to determine offset position
- [has_more() function](has-more.html) - Alternative way to check for more results
- [Pagination](../../concepts/pagination.html) - Complete guide to pagination approaches
- [OFFSET clause](../offset.html) - Works with page_token_offset()
- [LIMIT clause](../limit.html) - Controls page size

<!-- <footer> -->
<!-- <nav> -->
[has_more()](has-more.html) [page_token_offset()](page-token-offset.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->