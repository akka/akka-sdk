<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View query syntax](index.html)
- [LIMIT](limit.html)

<!-- </nav> -->

# LIMIT

The `LIMIT` clause restricts the number of rows returned by a query, specifying the maximum number of results to return. It’s primarily used for pagination and controlling result set size.

## <a href="about:blank#_syntax"></a> Syntax

```sql
LIMIT <row_count>
```

## <a href="about:blank#_elements"></a> Elements

`row_count` The maximum number of rows to return. This can be:

- A literal number (e.g., `10`)
- A parameter (e.g., `:pageSize`)

## <a href="about:blank#_features"></a> Features

Result limiting Caps the number of rows returned by the query to improve performance and manage memory usage.

Pagination control When combined with `OFFSET`, enables standard pagination by specifying how many items appear on each page.

Resource optimization Prevents retrieving unnecessarily large result sets, improving query performance and reducing memory usage.

## <a href="about:blank#_examples"></a> Examples

### <a href="about:blank#_basic_usage"></a> Basic usage

Limit to 10 rows
```sql
SELECT * FROM products
LIMIT 10
```
Limit using a parameter
```sql
SELECT * FROM products
LIMIT :maxResults
```

### <a href="about:blank#_pagination_scenarios"></a> Pagination scenarios

Combined with OFFSET for basic pagination
```sql
SELECT * FROM products
OFFSET 20 LIMIT 10
```
Combined with ORDER BY for consistent offset-based pagination
```sql
SELECT * FROM products
ORDER BY price DESC
OFFSET 20 LIMIT 10
```
Token-based pagination (note: no ORDER BY)
```sql
SELECT * AS products, next_page_token() AS nextPageToken
FROM products
OFFSET page_token_offset(:pageToken)
LIMIT 10
```

### <a href="about:blank#_advanced_pagination_examples"></a> Advanced pagination examples

Pagination with total count
```sql
SELECT * AS products,
       total_count() AS totalCount
FROM products
LIMIT :pageSize
```
Pagination with has_more indicator
```sql
SELECT * AS products,
       has_more() AS hasMorePages
FROM products
LIMIT 10
```
Complete pagination example
```sql
SELECT * AS products,
       next_page_token() AS nextPageToken,
       has_more() AS hasMore,
       total_count() AS totalCount
FROM products
WHERE category = :category
ORDER BY price ASC
LIMIT :pageSize
```

## <a href="about:blank#_implementation_in_java"></a> Implementation in Java

Java parameter class for multiple query parameters
```java
public record ProductsRequest(String category, int pageSize) {}
```
Query method with a single parameter object
```java
@Query("SELECT * FROM products WHERE category = :category LIMIT :pageSize")
public QueryEffect<ProductsResponse> getProductsByCategory(ProductsRequest request) {
  return queryResult();
}
```
Response type for limited results
```java
public record ProductsResponse(List<Product> products) {}
```
Client usage example
```java
// Get first 10 products
ProductsRequest request = new ProductsRequest("Electronics", 10);
ProductsResponse response = client.forView()
  .method(ProductView::getProductsByCategory)
  .invoke(request);
```

## <a href="about:blank#_notes"></a> Notes

- If no `LIMIT` is explicitly specified:

  - A default limit of 10000 items is applied for regular queries
  - A more restrictive default limit of 1000 items is applied when results are being projected into a collection
- These default limits help prevent accidentally retrieving extremely large result sets
- When using token-based pagination without specifying a `LIMIT`, a default page size of 100 is used
- For predictable pagination, always combine `LIMIT` with `ORDER BY` when using offset-based pagination
- Results cannot be sorted with `ORDER BY` when using token-based paging (`page_token_offset`)
- Using `has_more()` with `LIMIT` can efficiently indicate if there are additional results beyond the current page
- Very large limit values may impact performance - use reasonable page sizes for better user experience
- It’s generally better to explicitly specify a LIMIT value that makes sense for your use case rather than relying on the defaults

## <a href="about:blank#_performance_considerations"></a> Performance considerations

- Choose an appropriate limit size based on your use case:

  - UI pagination: typically 10-50 items per page
  - API responses: typically 50-100 items per page
  - Data processing: balance between memory usage and request count
- For large data sets, consider using `has_more()` instead of `total_count()` as it’s more efficient

## <a href="about:blank#_related_features"></a> Related features

- [OFFSET clause](offset.html) - Skips a specified number of rows
- [ORDER BY clause](order-by.html) - Sorts results before applying the limit
- [Pagination](../concepts/pagination.html) - Complete guide to pagination approaches
- [has_more() function](functions/has-more.html) - Checks if there are more results
- [next_page_token() function](functions/next-page-token.html) - For token-based pagination
- [total_count() function](functions/total-count.html) - Gets total matching row count

<!-- <footer> -->
<!-- <nav> -->
[AS](as.html) [OFFSET](offset.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->