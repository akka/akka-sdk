<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Functions](index.html)
- [has_more()](has-more.html)

<!-- </nav> -->

# has_more()

The `has_more()` function returns a boolean value indicating whether there are additional results available beyond the current page. It provides a convenient way to determine if more pages exist without having to check if the next page token is empty.

## <a href="about:blank#_syntax"></a> Syntax

```sql
has_more() AS alias
```

## <a href="about:blank#_elements"></a> Elements

`AS alias` Required aliasing for the boolean result in the query response. The alias determines the field name in the response object.

## <a href="about:blank#_features"></a> Features

Continuation Check Provides a simple boolean indicator that can be used in user interfaces to show or hide "load more" or pagination controls.

Works with All Paging Methods Can be used with both token-based pagination and traditional offset/limit pagination.

Zero Additional Fetch Determines if more results exist without requiring an extra query to attempt to fetch the next page.

## <a href="about:blank#_examples"></a> Examples

Basic usage with LIMIT
```sql
SELECT * AS products, has_more() AS moreAvailable
FROM products
LIMIT 10
```
With token-based pagination
```sql
SELECT * AS products,
       next_page_token() AS nextPageToken,
       has_more() AS hasMore
FROM products
OFFSET page_token_offset(:pageToken)
LIMIT 10
```
With filtering and ordering
```sql
SELECT * AS products, has_more() AS moreProducts
FROM products
WHERE category = :category
ORDER BY price ASC
OFFSET :startFrom LIMIT :pageSize
```
Checking for more without including next page token
```sql
SELECT * AS products, has_more() AS hasMoreProducts
FROM products
LIMIT 20
```

## <a href="about:blank#_usage_flow"></a> Usage Flow

1. **Include in Query**: Add the function to your SELECT clause with an alias:

```sql
SELECT * AS products, has_more() AS moreAvailable
```
2. **Response Structure**: Define a response type including the boolean field:

```java
public record ProductsResponse(List<Product> products, boolean moreAvailable) {}
```
3. **Client Logic**: Use the boolean to control pagination UI:

```java
if (response.moreAvailable()) {
    // Show "Load More" button or next page control
} else {
    // Hide pagination controls - we're at the end
}
```

## <a href="about:blank#_notes"></a> Notes

- The `has_more()` function must have an alias specified with `AS`
- The function returns `true` if there are more results beyond the current page, `false` if the current page contains the last results
- When used with `LIMIT`, it checks if there are more than `LIMIT` results matching the query
- The function is particularly useful for infinite scrolling or "load more" UI patterns
- The function can be used alongside `next_page_token()` to provide both a boolean indicator and a token for the next page

## <a href="about:blank#_related_features"></a> Related Features

- [next_page_token() function](next-page-token.html) - Generates tokens for subsequent page requests
- [page_token_offset() function](page-token-offset.html) - Uses a token to determine offset position
- [Pagination](../../concepts/pagination.html) - Complete guide to pagination approaches
- [LIMIT clause](../limit.html) - Controls page size and works with has_more()

<!-- <footer> -->
<!-- <nav> -->
[total_count()](total-count.html) [next_page_token()](next-page-token.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->