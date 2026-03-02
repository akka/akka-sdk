<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Integrations](integrations/index.html)
- [Retrieval-Augmented Generation (RAG)](rag.html)

<!-- </nav> -->

# Retrieval-Augmented Generation (RAG)

An AI model only knows about information that it was trained with. Domain-specific knowledge or the latest documentation must be given as input to the AI model as additional context.

It would be inefficient, costly, or not even possible to provide all content in the request to the AI. A technique to provide relevant content is called Retrieval-Augmented Generation (RAG). This is typically implemented by performing a semantic search on a vector database to find relevant content, which is then added to the user message in the request to the AI model.

Implementing RAG involves two main stages:

- Data Ingestion: The source documents (e.g., product documentation, articles) are loaded, split into manageable chunks, converted into numerical representations (embeddings) using an embedding model, and then stored in a vector database.
- Retrieval and Generation: When a user asks a question, the system first retrieves the most relevant chunks from the vector database and then passes them to the language model along with the original question to generate an answer.

## <a href="about:blank#_using_langchain4j"></a> Using Langchain4J

There are many libraries that can be used for integrating with vector databases. Here is one concrete example using [Langchain4J](https://docs.langchain4j.dev/tutorials/rag).

[Knowledge.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/agent/application/Knowledge.java)
```java
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
    var contentRetriever = EmbeddingStoreContentRetriever.builder() // (1)
      .embeddingStore(MongoDbUtils.embeddingStore(mongoClient))
      .embeddingModel(OpenAiUtils.embeddingModel())
      .maxResults// (10)
      .minScore(0.1)
      .build();

    this.retrievalAugmentor = DefaultRetrievalAugmentor.builder() // (2)
      .contentRetriever(contentRetriever)
      .build();
  }

  public String addKnowledge(String question) {
    var chatMessage = new UserMessage(question); // (3)
    var metadata = Metadata.from(chatMessage, null, null);
    var augmentationRequest = new AugmentationRequest(chatMessage, metadata);

    var result = retrievalAugmentor.augment(augmentationRequest); // (4)
    UserMessage augmented = (UserMessage) contentInjector.inject(
      result.contents(),
      chatMessage
    ); // (5)
    return augmented.singleText();
  }
}
```

| **1** | We use the RAG support from Langchain4j, which consist of a `ContentRetriever` |
| **2** | and a `RetrievalAugmentor`. |
| **3** | Create a request from the user question. |
| **4** | Augment the request with relevant content. |
| **5** | Construct the new user message that includes the retrieved content. |
This `Knowledge` class would then be used in an agent to enrich the user message.

The guide [AI agent that performs a RAG workflow](../getting-started/ask-akka-agent/index.html) illustrates how to create embeddings for vector databases, and how to add knowledge to fixed LLMs.

## <a href="about:blank#_enrich_the_context_from_other_components"></a> Enrich the context from other components

Sometimes a similar retrieval-and-augment approach can be used without a vector database, especially when the required context is structured and can be fetched directly. This follows the same RAG pattern but targets specific data sources like entities or views. It may look like this:

ActivityAgent.java
```java
@Component(id = "activity-agent")
public class ActivityAgent extends Agent {

  public record Request(String userId, String message) {}

  private static final String SYSTEM_MESSAGE =
    """
    You are an activity agent. Your job is to suggest activities in the
    real world. Like for example, a team building activity, sports, an
    indoor or outdoor game, board games, a city trip, etc.
    """.stripIndent();

  private final ComponentClient componentClient;

  public ActivityAgent(ComponentClient componentClient) { // (1)
    this.componentClient = componentClient;
  }

  public Effect<String> query(Request request) {
    var profile = componentClient // (2)
      .forEventSourcedEntity(request.userId)
      .method(UserProfileEntity::getProfile)
      .invoke();

    var userMessage = request.message + "\nPreferences: " + profile.preferences; // (3)

    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(userMessage).thenReply();
  }
}
```

| **1** | Inject the `ComponentClient` as a constructor parameter. |
| **2** | Retrieve preferences from an entity. |
| **3** | Enrich the user message with the preferences. |

<!-- <footer> -->
<!-- <nav> -->
[Streaming](streaming.html) [Setup and configuration](setup-and-configuration/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->