<!-- <nav> -->
- [Akka](../../index.html)
- [AI RAG agent part 3: Executing RAG queries](rag.html)

<!-- </nav> -->

# AI RAG agent part 3: Executing RAG queries

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

## <a href="about:blank#_overview"></a> Overview

In this step of the guide to building the *Ask Akka* application, you’ll be creating a class that wraps the OpenAI API and the MongoDB client API. It’s this class that will provide the abstraction for the rest of the application to use when making RAG queries. You’ll use Akka’s `@Setup` to configure the dependency injection for this class.

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)

## <a href="about:blank#_unfamiliar_with_concepts_like_vectors_embeddings_or_rag"></a> Unfamiliar with concepts like vectors, embeddings or RAG?

We recommend reviewing our [foundational explainer on AI concepts](../../concepts/ai-concepts-video.html). It offers helpful background that will deepen your understanding of the technologies and patterns used throughout this tutorial.

## <a href="about:blank#_creating_the_knowledge_class"></a> Creating the Knowledge class

We’re going to add a utility that will retrieve content from MongoDB that is related to the user’s query.

The following is the basic RAG-specific code that you can add to a new file `Knowledge.java` in `src/main/java/akka/ask/agent/application/`.

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
    UserMessage augmented = (UserMessage) contentInjector
        .inject(result.contents(), chatMessage); // (5)
    return augmented.singleText();
  }

}
```

| **1** | We use the RAG support from Langchain4j, which consist of a `ContentRetriever` |
| **2** | and a `RetrievalAugmentor`. |
| **3** | Create a request from the user question. |
| **4** | Augment the request with relevant content. |
| **5** | Construct the new user message that includes the retrieved content. |

## <a href="about:blank#_use_the_knowledge_in_the_agent"></a> Use the knowledge in the agent

[AskAkkaAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/agent/application/AskAkkaAgent.java)
```java
@ComponentId("ask-akka-agent")
@AgentDescription(name = "Ask Akka", description = "Expert in Akka")
public class AskAkkaAgent extends Agent {
  private final Knowledge knowledge;

  private static final String SYSTEM_MESSAGE =
      """
      You are a very enthusiastic Akka representative who loves to help people!
      Given the following sections from the Akka SDK documentation, answer the question
      using only that information, outputted in markdown format.
      If you are unsure and the text is not explicitly written in the documentation, say:
      Sorry, I don't know how to help with that.
      """.stripIndent(); // (1)

  public AskAkkaAgent(Knowledge knowledge) { // (2)
    this.knowledge = knowledge;
  }

  public StreamEffect ask(String question) {
    var enrichedQuestion = knowledge.addKnowledge(question); // (3)

    return streamEffects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(enrichedQuestion) // (4)
        .thenReply();
  }

}
```

| **1** | System message including instructions about the included Akka documentation. |
| **2** | Inject the `Knowledge`. |
| **3** | Retrieve relevant content and augment the question. |
| **4** | Use the question and retrieved content in the request to the LLM. |
To be able to inject the `Knowledge` we need to add it to the `Bootstrap`:

[Bootstrap.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/Bootstrap.java)
```java
@Setup
public class Bootstrap implements ServiceSetup {

  @Override
  public DependencyProvider createDependencyProvider() {
    MongoClient mongoClient = MongoClients.create(KeyUtils.readMongoDbUri());

    Knowledge knowledge = new Knowledge(mongoClient);

    return new DependencyProvider() {
      @Override
      public <T> T getDependency(Class<T> cls) {
        if (cls.equals(MongoClient.class)) {
          return (T) mongoClient;
        }

        if (cls.equals(Knowledge.class)) {
          return (T) knowledge;
        }

        return null;
      }
    };
  }
}
```

## <a href="about:blank#_running_the_service"></a> Running the service

Start your service locally:

```command
mvn compile exec:java
```
In another shell, you can now use `curl` to send requests to this Endpoint.

```command
curl localhost:9000/api/ask --header "Content-Type: application/json" -XPOST \
--data '{ "userId": "001", "sessionId": "foo", "question":"What are the core components of Akka?"}'
```
In the first part of this guide, the AI model couldn’t answer that question meaningfully, but now it will answer something like:

```none
1. Event Sourced Entities ...
2. Key Value Entities ...
3. HTTP Endpoints ...
...
```

## <a href="about:blank#_next_steps"></a> Next steps

Next we’ll create [UI endpoints](endpoints.html).

<!-- <footer> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->