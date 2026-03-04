<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [View reference](../index.html)
- [View concepts](index.html)
- [Pagination](pagination.html)

<!-- </nav> -->

# Pagination

Pagination is the practice of dividing large query result sets into smaller, manageable chunks or "pages." This concept page explains pagination approaches in Akka Views, their characteristics, and how to implement them effectively.

## <a href="about:blank#_why_pagination_is_important"></a> Why Pagination Is Important

When working with large data sets, pagination offers several benefits:

- **Performance**: Reduces memory usage and processing time by handling only a subset of results
- **Response Time**: Enables faster initial display of data to users
- **User Experience**: Provides a manageable way to navigate through large sets of data
- **Resource Efficiency**: Minimizes network bandwidth and client-side memory usage

## <a href="about:blank#_pagination_approaches"></a> Pagination Approaches

Akka Views support two main pagination approaches, each with distinct characteristics and use cases:

### <a href="about:blank#_count_based_pagination"></a> Count-based Pagination

Count-based pagination uses numeric offsets to skip a certain number of rows and return a specific count of results. This is the traditional approach used in many database systems.

**Implementation:**

```sql
SELECT * FROM products
OFFSET 20 LIMIT 10
```
**Characteristics:**

- Simple implementation and concept
- Works well with static data
- Supports arbitrary jumping to specific pages (e.g., page 5, page 10)
- Can use parameters for dynamic values: `OFFSET :startFrom LIMIT :pageSize`
**Limitations:**

- Can lead to missing or duplicated items if data changes between page requests
- Performance can degrade for large offsets as the database must still process all skipped rows
- Less suitable for real-time or frequently changing data

### <a href="about:blank#_token_based_pagination"></a> Token-based Pagination

Token-based pagination (also known as cursor-based pagination) uses opaque tokens that mark the position after the last returned row. These tokens are used to retrieve subsequent pages.

**Implementation:**

```sql
SELECT * AS products, next_page_token() AS nextPageToken
FROM products
OFFSET page_token_offset(:pageToken)
LIMIT 10
```
**Characteristics:**

- More resilient to data changes between requests
- More efficient for large result sets
- Consistent results even when items are added or removed
- Better performance with large data sets
**Limitations:**

- Cannot jump to arbitrary pages; must traverse pages sequentially
- More complex implementation
- Tokens are opaque and should be treated as black boxes

## <a href="about:blank#_implementing_pagination"></a> Implementing Pagination

### <a href="about:blank#_count_based_pagination_flow"></a> Count-based Pagination Flow

1. **Initial Request**: Start with `OFFSET 0 LIMIT pageSize`
2. **Subsequent Requests**: Calculate offset as `page * pageSize` for each page
3. **Navigation**: Allow moving forward, backward, or jumping to specific pages
**Example in Java:**

```java
public record PageRequest(int page, int pageSize) { }

public record PageResponse<T>(List<T> items, int currentPage, int pageSize, int totalCount) { }

@Query("SELECT * AS items, total_count() AS totalCount FROM products OFFSET :offset LIMIT :pageSize")
public QueryEffect<ProductsPage> getProductsPage(int offset, int pageSize) {
    return queryResult();
}

// In client code:
int page = 2;
int pageSize = 10;
int offset = page * pageSize;
ProductsPage response = componentClient.forView().method(ProductView::getProductsPage)
    .invoke(offset, pageSize);

// Calculate total pages
int totalPages = (int) Math.ceil((double) response.totalCount() / pageSize);
```

### <a href="about:blank#_token_based_pagination_flow"></a> Token-based Pagination Flow

1. **First Request**: Use an empty string as the `pageToken`
2. **Store Token**: Save the `nextPageToken` from the response
3. **Subsequent Requests**: Pass the saved token as `pageToken` in the next request
4. **Last Page Detection**: When an empty token is returned, you’ve reached the end
**Example in Java:**

```java
public record TokenPageRequest(String pageToken) { }

public record TokenPageResponse<T>(List<T> items, String nextPageToken, boolean hasMore) { }

@Query("""
    SELECT * AS items,
           next_page_token() AS nextPageToken,
           has_more() AS hasMore
    FROM products
    OFFSET page_token_offset(:pageToken)
    LIMIT 10
    """)
public QueryEffect<ProductsTokenPage> getProductsWithToken(String pageToken) {
    return queryResult();
}

// In client code:
String token = ""; // Empty for first page
ProductsTokenPage response;
do {
    response = componentClient.forView().method(ProductView::getProductsWithToken)
        .invoke(token);

    // Process the current page
    processItems(response.items());

    // Update token for next page
    token = response.nextPageToken();
} while (!token.isEmpty());
```

## <a href="about:blank#_enhancing_pagination"></a> Enhancing Pagination

### <a href="about:blank#_including_total_count"></a> Including Total Count

To include the total number of results in a paginated response, use the `total_count()` function:

```sql
SELECT * AS items, total_count() AS totalCount
FROM products
OFFSET :offset LIMIT :pageSize
```
This allows clients to:

- Display "showing X-Y of Z results" information
- Calculate the total number of pages
- Show appropriate pagination controls

### <a href="about:blank#_determining_if_more_results_exist"></a> Determining If More Results Exist

To check if there are more results beyond the current page, use the `has_more()` function:

```sql
SELECT * AS items, has_more() AS hasMore
FROM products
LIMIT :pageSize
```
This is particularly useful for "load more" patterns where you want to show a button only if more data is available.

### <a href="about:blank#_combining_with_sorting"></a> Combining with Sorting

For consistent pagination results, always combine pagination with explicit sorting:

```sql
SELECT * FROM products
ORDER BY name ASC
OFFSET :offset LIMIT :pageSize
```
Without explicit sorting, the order of results is not guaranteed, which can lead to unpredictable pagination behavior.

## <a href="about:blank#_ui_pagination_patterns"></a> UI Pagination Patterns

Different pagination approaches support different UI patterns:

### <a href="about:blank#_page_numbers"></a> Page Numbers

Best supported by count-based pagination:

- "Page 1, 2, 3…​" navigation
- "First/Previous/Next/Last" buttons
- Jump to specific page

```java
// Calculate values for UI
int totalPages = (int) Math.ceil((double) totalCount / pageSize);
int currentPage = offset / pageSize;
boolean hasNextPage = currentPage < totalPages - 1;
boolean hasPreviousPage = currentPage > 0;
```

### <a href="about:blank#_infinite_scroll_load_more"></a> Infinite Scroll / Load More

Best supported by token-based pagination:

- Continuous scrolling that loads more content when reaching the bottom
- "Load more" button that appends additional results
- One-way traversal through results

```java
// Check if "Load More" button should be shown
boolean showLoadMore = !nextPageToken.isEmpty() || response.hasMore();
```

## <a href="about:blank#_best_practices"></a> Best Practices

### <a href="about:blank#_choosing_the_right_approach"></a> Choosing the Right Approach

- **Use count-based pagination when**:

  - The data set is relatively stable
  - Users need to jump to specific pages
  - You need simple implementation
- **Use token-based pagination when**:

  - Data changes frequently
  - Dealing with very large result sets
  - Consistency between requests is critical
  - Implementing infinite scroll or "load more" patterns

### <a href="about:blank#_performance_optimization"></a> Performance Optimization

- Use reasonable page sizes (typically 10-50 items)
- Always include `ORDER BY` with paginated queries
- Consider using `has_more()` instead of `total_count()` when the exact total isn’t needed
- For large data sets, prefer token-based pagination

### <a href="about:blank#_user_experience"></a> User Experience

- Display clear pagination controls appropriate for your approach
- Show the current range and total count when available (e.g., "Showing 21-30 of 145")
- Cache previously loaded pages when appropriate
- Consider prefetching the next page for smoother navigation

## <a href="about:blank#_related_features"></a> Related Features

- [LIMIT clause](../syntax/limit.html) - Limiting the number of returned rows
- [OFFSET clause](../syntax/offset.html) - Skipping rows in results
- [next_page_token() function](../syntax/functions/next-page-token.html) - Creating pagination tokens
- [page_token_offset() function](../syntax/functions/page-token-offset.html) - Using pagination tokens
- [has_more() function](../syntax/functions/has-more.html) - Checking for more results
- [total_count() function](../syntax/functions/total-count.html) - Getting the total count of results
- [ORDER BY clause](../syntax/order-by.html) - Sorting results for consistent pagination

<!-- <footer> -->
<!-- <nav> -->
[Array types](array-types.html) [Advanced views](advanced-views.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->