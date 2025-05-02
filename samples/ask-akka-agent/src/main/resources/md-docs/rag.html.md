

<-nav->

- [  Akka](../../index.html)
- [  Developing](../index.html)
- [  Samples](../samples.html)
- [  AI RAG Agent](index.html)
- [  Executing RAG queries](rag.html)



</-nav->



# Executing RAG queries

## [](about:blank#_overview) Overview

In this step of the guide to building the *Ask Akka* application, you’ll be creating a class that wraps the OpenAI API and the MongoDB client API. It’s this class that will provide the abstraction for the rest of the application to use when making RAG queries. You’ll use Akka’s `@Setup` to configure the dependency injection for this class.

## [](about:blank#_prerequisites) Prerequisites

- Java 21, we recommend[  Eclipse Adoptium](https://adoptium.net/marketplace/)
- [  Apache Maven](https://maven.apache.org/install.html)   version 3.9 or later
- <a href="https://curl.se/download.html"> `curl`   command-line tool</a>
- An[  Akka account](https://console.akka.io/register)
- [  Docker Engine](https://docs.docker.com/get-started/get-docker/)   27 or later

## [](about:blank#_updating_the_bootstrap) Updating the bootstrap

In the previous section we created a bootstrap class that set up dependency injection for the MongoDB client. This client needs to be injected into the indexing workflow to use MongoDB as the vector store. We can just add a few lines to the `createDependencyProvider` method to also create an instance of the class we’ll build next, `AskAkkaAgent`

[Bootstrap.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/Bootstrap.java)
```java
if (cls.equals(AskAkkaAgent.class)) {
  return (T) new AskAkkaAgent(componentClient, mongoClient);
}
```

## [](about:blank#_creating_the_akka_agent_class) Creating the Akka Agent class

We know we’re going to be writing a utility that interacts with the LLM for us. Here the choice of how to accomplish this is far more subjective and based more on people’s Java preferences than their knowledge of Akka. In this case, we’ve opted to put the logic behind the `AskAkkaAgent` class and supporting utilities.

The following is the basic shell of the class before we add RAG-specific code to it.

[AskAkkaAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/agent/application/AskAkkaAgent.java)
```java
public class AskAkkaAgent {

  private final static Logger logger = LoggerFactory.getLogger(AskAkkaAgent.class);
  private final ComponentClient componentClient;
  private final MongoClient mongoClient;

  private final String sysMessage = """
      You are a very enthusiastic Akka representative who loves to help people!
      Given the following sections from the Akka SDK documentation, answer the question using only that information, outputted in markdown format.
      If you are unsure and the text is not explicitly written in the documentation, say:
      Sorry, I don't know how to help with that.
      """; // (1)

  // this langchain4j Assistant emits the response as a stream
  // check AkkaStreamUtils.toAkkaSource to see how this stream is converted to an
  // Akka Source
  interface Assistant {
    TokenStream chat(String message);
  }

  public AskAkkaAgent(ComponentClient componentClient, MongoClient mongoClient) { // (2)
    this.componentClient = componentClient;
    this.mongoClient = mongoClient;

  }

  private CompletionStage<Done> addExchange(String compositeEntityId,
      SessionEntity.Exchange conversation) { // (3)
    return componentClient
        .forEventSourcedEntity(compositeEntityId)
        .method(SessionEntity::addExchange)
        .invokeAsync(conversation);
  }

  /**
   * Fetches the history of the conversation for a given sessionId.
   */
  private List<ChatMessage> fetchHistory(String entityId) {
    var messages = componentClient
        .forEventSourcedEntity(entityId)
        .method(SessionEntity::getHistory).invoke();
    return messages.messages().stream().map(this::toChatMessage).toList();
  }

  private ChatMessage toChatMessage(SessionEntity.Message msg) {
    return switch (msg.type()) {
      case AI -> new AiMessage(msg.content());
      case USER -> new UserMessage(msg.content());
    };
  }

}
```

| **  1** | This is the*  system prompt*   . This will be sent along with context and history for each LLM call |
| **  2** | The `MongoClient`   instance will be injected by the boot strap setup class |
| **  3** | This function gets called after each LLM output stream finishes |

Next we add the `createAssistant` method. This is almost entirely made up of `langchain4j` code and not specific to Akka. The purpose of this function is to use langchain4j’s `AiServices` builder class to set up retrieval augmentation (MongoDB) and the chat model (Open AI).

[AskAkkaAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/agent/application/AskAkkaAgent.java)
```java
private Assistant createAssistant(String sessionId, List<ChatMessage> messages) {

  var chatLanguageModel = OpenAiUtils.streamingChatModel();

  var contentRetriever = EmbeddingStoreContentRetriever.builder()
      .embeddingStore(MongoDbUtils.embeddingStore(mongoClient))
      .embeddingModel(OpenAiUtils.embeddingModel()) // (1)
      .maxResults// (10)
      .minScore(0.1)
      .build();

  var retrievalAugmentor = DefaultRetrievalAugmentor.builder()
      .contentRetriever(contentRetriever)
      .build();

  var chatMemoryStore = new InMemoryChatMemoryStore();
  chatMemoryStore.updateMessages(sessionId, messages); // (2)

  var chatMemory = MessageWindowChatMemory.builder()
      .maxMessages// (2000)
      .chatMemoryStore(chatMemoryStore)
      .build();

  return AiServices.builder(Assistant.class)
      .streamingChatLanguageModel(chatLanguageModel)
      .chatMemory(chatMemory)
      .retrievalAugmentor(retrievalAugmentor)
      .systemMessageProvider(__ -> sysMessage)
      .build(); // (3)
}

/**
 * The 'ask' method takes the user question run it through the RAG agent and
 * returns the response as a stream.
 */
public Source<StreamedResponse, NotUsed> ask(String userId, String sessionId, String userQuestion) {

  // we want the SessionEntity id to be unique for each user session,
  // therefore we use a composite key of userId and sessionId
  var compositeEntityId = userId + ":" + sessionId;

  // we fetch the history (if any) and create the assistant
  var messages = fetchHistory(sessionId);
  var assistant = createAssistant(sessionId, messages);

  // below we take the assistant future and build a Source to stream out the
  // response
  return AkkaStreamUtils.toAkkaSource(assistant.chat(userQuestion))
      .mapAsync(1, res -> {

        if (res.finished()) {// is the last message?
          logger.debug("Exchange finished. Total input tokens {}, total output tokens {}", res.inputTokens(),
              res.outputTokens());

          // when we have a finished response, we are ready to save the exchange to the
          // SessionEntity
          // note that the exchange is saved atomically in a single command
          // since the pair question/answer belong together
          var exchange = new SessionEntity.Exchange(
              userId,
              sessionId,
              userQuestion, res.inputTokens(),
              res.content(), res.outputTokens()); // (4)

          // since the full response has already been streamed,
          // the last message can be transformed to an empty message
          return addExchange(compositeEntityId, exchange)
              .thenApply(__ -> StreamedResponse.empty());
        } else {
          logger.debug("partial message '{}'", res.content());
          // other messages are streamed out to the caller
          // (those are the responseTokensCount emitted by the llm)
          return CompletableFuture.completedFuture(res); // (5)
        }
      });
}
```

| **  1** | Use the Open AI embedding model with MongoDB Atlas as the embedding store |
| **  2** | Set the message history for this instance |
| **  3** | Plug everything together using `AiServices`   from langchain4j |
| **  4** | We’ve received the full output stream from the LLM, so tell the session entity about it |
| **  5** | This is just a part of the stream so keep streaming to the original caller |

Next we need a utility to form a bridge between langchain4j and Akka.

## [](about:blank#_creating_a_streaming_source) Creating a streaming source

In the preceding code, we call `AkkaStreamUtils.toAkkaSource` on the result of `assistant.chat(userQuestion)` . This is a utility method that converts the stream of tokens returned by langchain4j’s `chat` method into an Akka stream *source* . We do that so that any endpoint component (shown in the next guide) can stream meaningful results. The tokens get converted into meaningful results via an asynchronous *map*.

The code for this method delves into a bit of advanced Akka code in order to create a stream from a langchain4j object, but it’s only a few lines of code without comments.

[AkkaStreamUtils.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/agent/application/AkkaStreamUtils.java)
```java
public static Source<StreamedResponse, NotUsed> toAkkaSource(TokenStream tokenStream) { // (1)
  return Source
      .<StreamedResponse>queue// (10000) // (2)
      .mapMaterializedValue(queue -> {
        // responseTokensCount emitted by tokenStream are passed to the queue
        // that ultimately feeds the Akka Source
        tokenStream
            // the partial responses are the tokens that are streamed out as the response
            .onPartialResponse(msg -> queue.offer(StreamedResponse.partial(msg))) // (3)
            // on completion, we receive a ChatResponse that contains the full response text
            // + token usage
            // we emit this last message so we can easily add it to the SessionEntity and
            // store the exchange
            .onCompleteResponse(res -> {
              queue.offer(
                  StreamedResponse.lastMessage(
                      res.aiMessage().text(),
                      res.tokenUsage().inputTokenCount(),
                      res.tokenUsage().outputTokenCount())); // (4)
              queue.complete();
            })
            .onError(queue::fail)
            .start();

        return NotUsed.getInstance();
      });
}
```

| **  1** | Input is a langchain4j token stream, output is an Akka stream source |
| **  2** | `Source.queue`   builds a new source backed by a queue, this one has a max length of 10,000 |
| **  3** | If we get tokens before we finish, we add them to the stream (via `offer`   ) |
| **  4** | If the token stream is finished, then we `offer`   and then `complete` |

## [](about:blank#_next_steps) Next steps

Next we’ll create streaming endpoints that provide clients with real-time access to LLM output.

|  | **  Coming soon!**   We are actively working on building and vetting this content. We will announce more content as it arrives. |



<-footer->


<-nav->
[Knowledge indexing with a workflow](indexer.html) [Operating - Self-managed nodes](../../self-managed/index.html)

</-nav->


</-footer->


<-aside->


</-aside->
