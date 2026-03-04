<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Functions](index.html)
- [page_token_offset()](page-token-offset.html)

<!-- </nav> -->

# page_token_offset()

The `page_token_offset()` function implements token-based pagination by using an opaque token from a previous query to determine the starting position for the current query. This approach provides more stable pagination when underlying data changes between page requests.

## <a href="about:blank#_syntax"></a> Syntax

```sql
OFFSET page_token_offset(:parameter_name)
```

## <a href="about:blank#_elements"></a> Elements

`:parameter_name` The parameter containing the page token. This token is typically obtained from the `next_page_token()` function in a previous query result.

## <a href="about:blank#_features"></a> Features

Token-based Pagination Enables cursor-based pagination that maintains consistency even when data changes between page requests.

Stable Paging Prevents the skipping or duplication of results that can occur with numeric offsets when data is added or removed between page requests.

Context Preservation The token encapsulates information about the exact position in the result set, including sort order and filter conditions.

## <a href="about:blank#_examples"></a> Examples

Basic token-based pagination
```sql
SELECT * AS products, next_page_token() AS nextPageToken
FROM products
OFFSET page_token_offset(:pageToken)
LIMIT 10
```
Token-based pagination with sorting
```sql
SELECT * AS products, next_page_token() AS nextPageToken
FROM products
ORDER BY price DESC
OFFSET page_token_offset(:pageToken)
LIMIT 10
```
Complete pagination example with has_more check
```sql
SELECT * AS products,
       next_page_token() AS nextPageToken,
       has_more() AS hasMoreProducts
FROM products
WHERE category = :category
ORDER BY price ASC
OFFSET page_token_offset(:pageToken)
LIMIT 10
```

## <a href="about:blank#_usage_flow"></a> Usage Flow

1. **First Page Request**: For the first page request, use an empty string as the token value:

```java
String pageToken = ""; // Empty string for first page
```
2. **Process Response**: The query response includes results and a next page token:

```json
{
  "products": [...],
  "nextPageToken": "eyJvZmZzZXQiOjEwLCJzb3J0IjoicHJpY2UgREVTQyJ9"
}
```
3. **Request Next Page**: Use the received token for the next page request:

```java
String pageToken = response.nextPageToken(); // Token from previous response
```
4. **Detect Last Page**: When the last page is reached, an empty token is returned:

```java
if (response.nextPageToken().isEmpty()) {
    // Last page reached
}
```

## <a href="about:blank#_notes"></a> Notes

- The page token is an opaque string and should be treated as a black box - do not attempt to parse or modify it
- For the first page request, use an empty string as the page token
- When the last page is reached, an empty string is returned as the next page token
- Tokens are specific to a particular query structure - changing the query conditions or sort order invalidates existing tokens
- If no `LIMIT` is specified with `page_token_offset()`, a default page size of 100 is used

## <a href="about:blank#_related_features"></a> Related Features

- [next_page_token() function](next-page-token.html) - Generates tokens for subsequent page requests
- [has_more() function](has-more.html) - Indicates whether more results exist
- [Pagination](../../concepts/pagination.html) - Complete guide to pagination approaches
- [OFFSET clause](../offset.html) - General offset functionality
- [LIMIT clause](../limit.html) - Controls page size

<!-- <footer> -->
<!-- <nav> -->
[next_page_token()](next-page-token.html) [View concepts](../../concepts/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->