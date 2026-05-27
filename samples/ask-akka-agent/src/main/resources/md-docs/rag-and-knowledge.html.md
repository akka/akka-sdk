<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Use cases](index.html)
- [RAG & knowledge](rag-and-knowledge.html)

<!-- </nav> -->

# RAG & knowledge

Build retrieval-augmented generation (RAG) systems that ground LLM responses in your own data. This pattern covers knowledge indexing, vector embeddings for semantic search, and combining retrieved context with conversational memory to produce accurate, sourced answers.

## <a href="about:blank#_overview"></a> Overview

An AI model only knows about information that it was trained with. Domain-specific knowledge or the latest documentation must be given as input to the AI model as additional context.

It would be inefficient, costly, or not even possible to provide all content in the request to the AI. Retrieval-Augmented Generation (RAG) solves this by performing a semantic search on a vector database to find relevant content, which is then added to the user message in the request to the AI model.

Implementing RAG involves two main stages:

- **Data ingestion:** Source documents (e.g., product documentation, articles) are loaded, split into manageable chunks, converted into numerical representations (embeddings) using an embedding model, and stored in a vector database.
- **Retrieval and generation:** When a user asks a question, the system retrieves the most relevant chunks from the vector database and passes them to the language model along with the original question to generate an answer.

### <a href="about:blank#_when_to_use_this_pattern"></a> When to use this pattern

- You need an Agent that answers questions grounded in your organization’s documents or data
- Your application requires semantic search over a knowledge base with vector embeddings
- You want to combine retrieved context with conversation history for accurate responses
- You need to index and update knowledge sources as they change over time

### <a href="about:blank#_akka_components_involved"></a> Akka components involved

- **Agents** — conversational interface that queries knowledge and generates grounded responses
- **Workflows** — orchestrate the knowledge indexing pipeline
- **HTTP Endpoints** — expose RAG-powered Agents and knowledge ingestion APIs

## <a href="about:blank#_using_langchain4j"></a> Using LangChain4J

There are many libraries for integrating with vector databases. Here is a concrete example using [LangChain4J](https://docs.langchain4j.dev/tutorials/rag).

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
      .maxResults(10)
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

| **1** | Use the RAG support from LangChain4J, which consists of a `ContentRetriever` |
| **2** | and a `RetrievalAugmentor`. |
| **3** | Create a request from the user question. |
| **4** | Augment the request with relevant content. |
| **5** | Construct the new user message that includes the retrieved content. |
This `Knowledge` class is then used in an Agent to enrich the user message.

## <a href="about:blank#_enrich_the_context_from_other_components"></a> Enrich the context from other components

Sometimes a similar retrieval-and-augment approach can be used without a vector database, especially when the required context is structured and can be fetched directly. This follows the same RAG pattern but targets specific data sources like Entities or Views:

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
| **2** | Retrieve preferences from an Entity. |
| **3** | Enrich the user message with the preferences. |

## <a href="about:blank#_sample_projects"></a> Sample projects

- [ask-akka-agent](https://github.com/akka-samples/ask-akka-agent) — RAG Agent that answers questions about Akka documentation using vector search and conversational memory
The guide [AI Agent that performs a RAG Workflow](../../getting-started/ask-akka-agent/index.html) illustrates how to create embeddings for vector databases and how to add knowledge to fixed LLMs.

## <a href="about:blank#_see_also"></a> See also

- [Agents](../agents.html)
- [Data & knowledge integrations](../integrations/data-and-knowledge.html)
- [RAG chat Agent tutorial](../../getting-started/ask-akka-agent/index.html)

<!-- <footer> -->
<!-- <nav> -->
[Memory & state](memory-and-state.html) [Streaming AI](streaming-ai.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->