package akka.ask.agent.application;

// tag::class[]
import akka.ask.common.MongoDbUtils;
import akka.ask.common.OpenAiUtils;
import com.mongodb.client.MongoClient;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Metadata;

public class Knowledge {
  private final RetrievalAugmentor retrievalAugmentor;
  private final ContentInjector contentInjector = new DefaultContentInjector();

  public Knowledge(MongoClient mongoClient) {
    var contentRetriever = EmbeddingStoreContentRetriever.builder() // <1>
        .embeddingStore(MongoDbUtils.embeddingStore(mongoClient))
        .embeddingModel(OpenAiUtils.embeddingModel())
        .maxResults(10)
        .minScore(0.1)
        .build();

    this.retrievalAugmentor = DefaultRetrievalAugmentor.builder() // <2>
        .contentRetriever(contentRetriever)
        .build();
  }

  public String addKnowledge(String question) {
    var chatMessage = new UserMessage(question); // <3>
    var metadata = Metadata.from(chatMessage, null, null);
    var augmentationRequest = new AugmentationRequest(chatMessage, metadata);

    var result = retrievalAugmentor.augment(augmentationRequest); // <4>
    UserMessage augmented = (UserMessage) contentInjector
        .inject(result.contents(), chatMessage); // <5>
    return augmented.singleText();
  }

}
// end::class[]
