<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [View reference](../../index.html)
- [View query syntax](../index.html)
- [Functions](index.html)
- [text_search()](text-search.html)

<!-- </nav> -->

# text_search()

The `text_search()` function provides advanced text search capabilities, allowing you to search text columns for words or phrases with automatic tokenization and language-specific normalization.

## <a href="about:blank#_syntax"></a> Syntax

```sql
text_search(<column>, <query>, [<configuration>])
```

## <a href="about:blank#_elements"></a> Elements

`column` The text column to search within. This should be a column containing text content.

`query` The search query, which can be a literal string or a parameter. The query can contain multiple words that will be searched for using an AND logic (all words must be present).

`configuration` Optional language configuration to use for text search. If not specified, a simple configuration without language-specific features is used.

## <a href="about:blank#_features"></a> Features

Word Tokenization Automatically breaks text into tokens (words) based on language-specific rules.

Text Normalization Handles stemming, stop words, and other language-specific normalizations based on the chosen configuration.

Multi-word Search When a query contains multiple words, finds entries that contain all of those words (combined with logical AND).

Linguistic Support Supports multiple language configurations for more accurate matching in different languages.

## <a href="about:blank#_supported_language_configurations"></a> Supported Language Configurations

The following language configurations are supported:

- `'danish'`
- `'dutch'`
- `'english'`
- `'finnish'`
- `'french'`
- `'german'`
- `'hungarian'`
- `'italian'`
- `'norwegian'`
- `'portuguese'`
- `'romanian'`
- `'russian'`
- `'simple'` (default)
- `'spanish'`
- `'swedish'`
- `'turkish'`

## <a href="about:blank#_examples"></a> Examples

Basic text search with default configuration
```sql
SELECT * FROM articles
WHERE text_search(content, 'database')
```
Search with a parameter
```sql
SELECT * FROM articles
WHERE text_search(content, :searchQuery)
```
Search with language configuration
```sql
SELECT * FROM articles
WHERE text_search(content, 'database system', 'english')
```
Search in multiple languages
```sql
SELECT * FROM articles
WHERE text_search(content_english, :searchQuery, 'english')
   OR text_search(content_spanish, :searchQuery, 'spanish')
```
Combine text search with other conditions
```sql
SELECT * FROM articles
WHERE text_search(content, :searchQuery, 'english')
  AND category = 'technology'
  AND published_date > '2022-01-01'
```

## <a href="about:blank#_how_text_search_works"></a> How Text Search Works

The `text_search()` function:

1. Tokenizes the column text into words based on the language configuration
2. Applies language-specific normalization (stemming, stop words removal)
3. Tokenizes and normalizes the query text in the same way
4. Searches for matches where all query words are present in the column text
5. Returns true for rows where all query words are found, false otherwise

## <a href="about:blank#_notes"></a> Notes

- Text search is only available for deployed services and cannot be used in local testing environments
- For optimal performance, the text column should be indexed for text search
- The default `'simple'` configuration provides basic tokenization without language-specific features
- Language-specific configurations provide better matching through stemming (e.g., matching "running" with "run") and stop word removal
- Multiple words in the query are combined with AND logic (all words must be present)
- Text search is case-insensitive by default

## <a href="about:blank#_related_features"></a> Related Features

- [WHERE clause](../where.html) - Used with text_search() for filtering
- [LIKE operator](../operators/like.html) - Simpler pattern matching alternative

<!-- <footer> -->
<!-- <nav> -->
[DISTINCT](distinct.html) [total_count()](total-count.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->