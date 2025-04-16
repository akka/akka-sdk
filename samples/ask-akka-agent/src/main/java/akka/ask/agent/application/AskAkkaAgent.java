package akka.ask.agent.application;

import akka.Done;
import akka.NotUsed;
import akka.ask.common.MongoDbUtils;
import akka.ask.common.OpenAiUtils;
import akka.javasdk.client.ComponentClient;
import akka.stream.javadsl.Source;
import com.mongodb.client.MongoClient;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * This services works as an interface to the AI.
 * It returns the AI response as a stream and can be used to stream out a response using for example SSE in an
 * HttpEndpoint.
 *
 * The service is configured as a RAG agent that uses the OpenAI API to generate responses based on the Akka SDK documentation.
 * It uses a MongoDB Atlas index to retrieve relevant documentation sections for the user's question.
 *
 * Moreover, the whole RAG setup is done through LangChain4j APIs.
 *
 * The chat memory is preserved on a SessionEntity. At start of each new exchange, the existing chat memory is
 * retrieved and include into the chat context. Once the exchange finished, the latest pair of messages (user message
 * and AI message) are saved to the SessionEntity.
 *
 */
public class AskAkkaAgent {

  private final static Logger logger = LoggerFactory.getLogger(AskAkkaAgent.class);
  private final ComponentClient componentClient;
  private final MongoClient mongoClient;

  private final String sysMessage = """
    You are a very enthusiastic Akka representative who loves to help people!
    Given the following sections from the Akka SDK documentation, answer the question using only that information, outputted in markdown format. 
    If you are unsure and the text is not explicitly written in the documentation, say:
    Sorry, I don't know how to help with that.
    """;

  // this langchain4j Assistant emits the response as a stream
  // check AkkaStreamUtils.toAkkaSource to see how this stream is converted to an Akka Source
  interface Assistant {
    TokenStream chat(String message);
  }

  public AskAkkaAgent(ComponentClient componentClient, MongoClient mongoClient) {
    this.componentClient = componentClient;
    this.mongoClient = mongoClient;

  }

  private CompletionStage<Done> addExchange(String compositeEntityId, SessionEntity.Exchange conversation) {
    return componentClient
      .forEventSourcedEntity(compositeEntityId)
      .method(SessionEntity::addExchange)
      .invokeAsync(conversation);
  }

  /**
   * Fetches the history of the conversation for a given sessionId.
   */
  private CompletionStage<List<ChatMessage>> fetchHistory(String  entityId) {
    return componentClient
        .forEventSourcedEntity( entityId)
        .method(SessionEntity::getHistory).invokeAsync()
        .thenApply(messages -> messages.messages().stream().map(this::toChatMessage).toList());
  }

  private ChatMessage toChatMessage(SessionEntity.Message msg) {
    return switch (msg.type()) {
      case AI -> new AiMessage(msg.content());
      case USER -> new UserMessage(msg.content());
    };
  }

  /**
   * This method build the RAG setup using LangChain4j APIs.
   */
  private Assistant createAssistant(String sessionId,  List<ChatMessage> messages) {

    var chatLanguageModel = OpenAiUtils.streamingChatModel();

    var contentRetriever = EmbeddingStoreContentRetriever.builder()
      .embeddingStore(MongoDbUtils.embeddingStore(mongoClient))
      .embeddingModel(OpenAiUtils.embeddingModel())
      .maxResults(10)
      .minScore(0.1)
      .build();

    var retrievalAugmentor =
      DefaultRetrievalAugmentor.builder()
        .contentRetriever(contentRetriever)
        .build();

    var chatMemoryStore = new InMemoryChatMemoryStore();
    chatMemoryStore.updateMessages(sessionId, messages);


    var chatMemory  = MessageWindowChatMemory.builder()
      .maxMessages(2000)
      .chatMemoryStore(chatMemoryStore)
      .build();

    return AiServices.builder(Assistant.class)
      .streamingChatLanguageModel(chatLanguageModel)
      .chatMemory(chatMemory)
      .retrievalAugmentor(retrievalAugmentor)
      .systemMessageProvider(__ -> sysMessage)
      .build();
  }

  /**
   * The 'ask' method takes the user question run it through the RAG agent and returns the response as a stream.
   */
  public Source<StreamedResponse, NotUsed> ask(String userId, String sessionId, String userQuestion) {

    // we want the SessionEntity id to be unique for each user session,
    // therefore we use a composite key of userId and sessionId
    var compositeEntityId = userId + ":" + sessionId;

    // we fetch the history (if any) and create the assistant
    // note that both calls are async, once we have the history,
    // we can build the assistant using the previous chat memory
    var assistantFut =
      fetchHistory(sessionId)
        .thenApply(messages -> createAssistant(sessionId, messages));

    // below we take the assistant future and build a Source to stream out the response
    return Source
        .completionStage(assistantFut)
        // once we have the assistant, we run the query and get the response streamed back
      .flatMapConcat(assistant -> AkkaStreamUtils.toAkkaSource(assistant.chat(userQuestion)))
        .mapAsync(1, res -> {

          if (res.finished()) {// is the last message?
            logger.debug("Exchange finished. Total input tokens {}, total output tokens {}", res.inputTokens(), res.outputTokens());

            // when we have a finished response, we are ready to save the exchange to the SessionEntity
            // note that the exchange is saved atomically in a single command
            // since the pair question/answer belong together
            var exchange = new SessionEntity.Exchange(
              userId,
              sessionId,
              userQuestion, res.inputTokens(),
              res.content(), res.outputTokens()
            );

            // since the full response has already been streamed,
            // the last message can be transformed to an empty message
            return addExchange(compositeEntityId, exchange)
              .thenApply(__ -> StreamedResponse.empty());
          }
          else {
            logger.debug("partial message '{}'", res.content());
            // other messages are streamed out to the caller
            // (those are the responseTokensCount emitted by the llm)
            return CompletableFuture.completedFuture(res);
          }
        });

  }

}
