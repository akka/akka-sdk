<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Integrations](index.html)
- [Data & knowledge](data-and-knowledge.html)

<!-- </nav> -->

# Data & knowledge

Akka connects to any data store with a Java client library. No proprietary adapters or Akka-specific wrappers are needed.

## <a href="about:blank#_vector_databases"></a> Vector databases

Use vector databases for semantic search and RAG workflows. Connect via their Java client libraries:

- **Pinecone** — [Java client](https://docs.pinecone.io/docs/java-client)
- **Weaviate** — [Java client](https://weaviate.io/developers/weaviate/client-libraries/java)
- **Qdrant** — [Java client](https://qdrant.tech/documentation/interfaces/)
- **Chroma** — via REST API or community Java clients
- **pgvector** — via JDBC with the pgvector extension

## <a href="about:blank#_relational_databases"></a> Relational databases

- **PostgreSQL** — via R2DBC or JDBC
- **MySQL** — via JDBC

## <a href="about:blank#_nosql_databases"></a> NoSQL databases

- **MongoDB** — [Java driver](https://www.mongodb.com/docs/drivers/java/sync/current/)
- **DynamoDB** — [AWS SDK for Java](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/dynamodb.html)
- **Cassandra** — [DataStax Java driver](https://docs.datastax.com/en/developer/java-driver/latest/)

## <a href="about:blank#_search_engines"></a> Search engines

- **Elasticsearch** — [Java API client](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/index.html)
- **OpenSearch** — [Java client](https://opensearch.org/docs/latest/clients/java/)

## <a href="about:blank#_no_caching_layer_needed"></a> No caching layer needed

Akka’s Entity components (Event Sourced Entities, Key Value Entities) are in-memory systems of durable record. All data is durable, immutable, and in-memory with sub-10ms access. There is no need for a separate cache like Redis. Your Entities *are* your cache — with persistence guarantees that Redis cannot provide.

## <a href="about:blank#_see_also"></a> See also

- [Event Sourced Entities](../event-sourced-entities.html)
- [Key Value Entities](../key-value-entities.html)
- [RAG & knowledge use case](../use-cases/rag-and-knowledge.html)

<!-- <footer> -->
<!-- <nav> -->
[AI & models](ai-and-models.html) [Object storage](object-storage.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->