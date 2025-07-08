package akka.ask.common;

import com.mongodb.client.MongoClient;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.mongodb.MongoDbEmbeddingStore;

public class MongoDbUtils {

  public static EmbeddingStore<TextSegment> embeddingStore(MongoClient mongoClient) {
    return MongoDbEmbeddingStore.builder()
      .fromClient(mongoClient)
      // TODO: make db name, collection name and index name configurable
      .databaseName("akka-docs")
      .collectionName("embeddings")
      .indexName("default")
      .createIndex(true)
      .build();
  }
}
