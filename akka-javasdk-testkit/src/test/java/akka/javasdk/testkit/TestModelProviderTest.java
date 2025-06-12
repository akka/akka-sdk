/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestModelProviderTest {

  private TestModelProvider testModelProvider;

  @BeforeEach
  void setUp() {
    testModelProvider = new TestModelProvider();
  }

  @Test
  void testTokenizeWithSimpleText() {
    List<String> tokens = testModelProvider.tokenize("Hello world");
    assertThat(tokens).containsExactly("Hello", " ", "world");
  }

  @Test
  void testTokenizeWithPunctuation() {
    List<String> tokens = testModelProvider.tokenize("Hello, world!");
    assertThat(tokens).containsExactly("Hello", ",", " ", "world", "!");
  }

  @Test
  void testTokenizeWithMultipleSeparators() {
    List<String> tokens = testModelProvider.tokenize("Hello, world! How are you?");
    assertThat(tokens).containsExactly("Hello", ",", " ", "world", "!", " ", "How", " ", "are", " ", "you", "?");
  }

  @Test
  void testTokenizeWithAllSeparators() {
    List<String> tokens = testModelProvider.tokenize("a, b. c! d? e; f:");
    assertThat(tokens).containsExactly("a", ",", " ", "b", ".", " ", "c", "!", " ", "d", "?", " ", "e", ";", " ", "f", ":");
  }

  @Test
  void testTokenizeWithEmptyString() {
    List<String> tokens = testModelProvider.tokenize("");
    assertThat(tokens).isEmpty();
  }

  @Test
  void testTokenizeWithOnlySpaces() {
    List<String> tokens = testModelProvider.tokenize("   ");
    assertThat(tokens).containsExactly(" ", " ", " ");
  }

  @Test
  void testTokenizeWithSingleWord() {
    List<String> tokens = testModelProvider.tokenize("Hello");
    assertThat(tokens).containsExactly("Hello");
  }

  @Test
  void testTokenizeWithConsecutiveSeparators() {
    List<String> tokens = testModelProvider.tokenize("Hello,, world!!");
    assertThat(tokens).containsExactly("Hello", ",", ",", " ", "world", "!", "!");
  }

  @Test
  void testCreateChatModel() {
    Object chatModel = testModelProvider.createChatModel();
    assertThat(chatModel).isNotNull()
        .isInstanceOf(ChatModel.class);
  }

  @Test
  void testCreateStreamingChatModel() {
    Object streamingChatModel = testModelProvider.createStreamingChatModel();
    assertThat(streamingChatModel).isNotNull()
        .isInstanceOf(StreamingChatModel.class);
  }

  @Test
  void testFixedResponse() {
    testModelProvider.fixedResponse("Test response");

    ChatModel chatModel = (ChatModel) testModelProvider.createChatModel();
    ChatRequest request = ChatRequest.builder()
        .messages(List.of(new UserMessage("Any question")))
        .build();

    ChatResponse response = chatModel.doChat(request);

    assertThat(response.aiMessage().text()).isEqualTo("Test response");
    assertThat(response.modelName()).isEqualTo("test-model");
    assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
  }

  @Test
  void testMockResponseWithPredicate() {
    testModelProvider.mockResponse(text -> text.contains("hello"), "Hello response");
    testModelProvider.mockResponse(text -> text.contains("goodbye"), "Goodbye response");

    ChatModel chatModel = (ChatModel) testModelProvider.createChatModel();

    // Test first predicate
    ChatRequest helloRequest = ChatRequest.builder()
        .messages(List.of(new UserMessage("Say hello to me")))
        .build();
    ChatResponse helloResponse = chatModel.doChat(helloRequest);
    assertThat(helloResponse.aiMessage().text()).isEqualTo("Hello response");

    // Test second predicate
    ChatRequest goodbyeRequest = ChatRequest.builder()
        .messages(List.of(new UserMessage("Say goodbye to me")))
        .build();
    ChatResponse goodbyeResponse = chatModel.doChat(goodbyeRequest);
    assertThat(goodbyeResponse.aiMessage().text()).isEqualTo("Goodbye response");
  }

  @Test
  void testMockResponseNoMatch() {
    testModelProvider.mockResponse(text -> text.contains("specific"), "Specific response");

    ChatModel chatModel = (ChatModel) testModelProvider.createChatModel();
    ChatRequest request = ChatRequest.builder()
        .messages(List.of(new UserMessage("Random question")))
        .build();

    assertThatThrownBy(() -> chatModel.doChat(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No response defined in TestModelProvider");
  }

  @Test
  void testStreamingChatModel() {
    testModelProvider.fixedResponse("Hello world!");

    StreamingChatModel streamingChatModel = (StreamingChatModel) testModelProvider.createStreamingChatModel();
    TestStreamingHandler handler = new TestStreamingHandler();

    ChatRequest request = ChatRequest.builder()
        .messages(List.of(new UserMessage("Test question")))
        .build();

    streamingChatModel.doChat(request, handler);

    // Verify partial responses (tokens) - now separators are separate tokens
    assertThat(handler.partialResponses).hasSize(4)
        .containsExactly("Hello", " ", "world", "!");

    // Verify complete response
    assertThat(handler.completeResponse).isNotNull();
    assertThat(handler.completeResponse.aiMessage().text()).isEqualTo("Hello world!");
    assertThat(handler.completeResponse.modelName()).isEqualTo("test-model");
    assertThat(handler.completeResponse.finishReason()).isEqualTo(FinishReason.STOP);
  }

  @Test
  void testReset() {
    testModelProvider.fixedResponse("Initial response");

    ChatModel chatModel = (ChatModel) testModelProvider.createChatModel();
    ChatRequest request = ChatRequest.builder()
        .messages(List.of(new UserMessage("Test question")))
        .build();

    // Verify initial response works
    ChatResponse response = chatModel.doChat(request);
    assertThat(response.aiMessage().text()).isEqualTo("Initial response");

    // Reset and verify no responses are available
    testModelProvider.reset();

    assertThatThrownBy(() -> chatModel.doChat(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No response defined in TestModelProvider");
  }

  @Test
  void testChatRequestWithoutUserMessage() {
    testModelProvider.fixedResponse("Test response");

    ChatModel chatModel = (ChatModel) testModelProvider.createChatModel();
    ChatRequest request = ChatRequest.builder()
        .messages(List.of(new AiMessage("AI message only")))
        .build();

    assertThatThrownBy(() -> chatModel.doChat(request))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("No user message found");
  }

  @Test
  void testMultipleResponsePredicatesFirstMatch() {
    testModelProvider.mockResponse(text -> text.contains("first"), "First response");
    testModelProvider.mockResponse(text -> text.contains("first"), "Second response");

    ChatModel chatModel = (ChatModel) testModelProvider.createChatModel();
    ChatRequest request = ChatRequest.builder()
        .messages(List.of(new UserMessage("This is the first test")))
        .build();

    ChatResponse response = chatModel.doChat(request);
    assertThat(response.aiMessage().text()).isEqualTo("First response");
  }

  @Test
  void testStreamingWithComplexTokenization() {
    testModelProvider.fixedResponse("Hello, world! How are you?");

    StreamingChatModel streamingChatModel = (StreamingChatModel) testModelProvider.createStreamingChatModel();
    TestStreamingHandler handler = new TestStreamingHandler();

    ChatRequest request = ChatRequest.builder()
        .messages(List.of(new UserMessage("Complex test")))
        .build();

    streamingChatModel.doChat(request, handler);

    // Verify all tokens are streamed correctly - now with separate separators
    List<String> expectedTokens = List.of("Hello", ",", " ", "world", "!", " ", "How", " ", "are", " ", "you", "?");
    assertThat(handler.partialResponses).hasSize(expectedTokens.size())
        .containsExactlyElementsOf(expectedTokens);

    // Verify complete response
    assertThat(handler.completeResponse).isNotNull();
    assertThat(handler.completeResponse.aiMessage().text()).isEqualTo("Hello, world! How are you?");
  }

  @Test
  void testStreamingWithEmptyResponse() {
    testModelProvider.fixedResponse("");

    StreamingChatModel streamingChatModel = (StreamingChatModel) testModelProvider.createStreamingChatModel();
    TestStreamingHandler handler = new TestStreamingHandler();

    ChatRequest request = ChatRequest.builder()
        .messages(List.of(new UserMessage("Empty test")))
        .build();

    streamingChatModel.doChat(request, handler);

    // No partial responses for empty string
    assertThat(handler.partialResponses).isEmpty();

    // But complete response should still be called
    assertThat(handler.completeResponse).isNotNull();
    assertThat(handler.completeResponse.aiMessage().text()).isEmpty();
  }

  // Test implementation of StreamingChatResponseHandler
  private static class TestStreamingHandler implements StreamingChatResponseHandler {
    final List<String> partialResponses = new ArrayList<>();
    ChatResponse completeResponse;
    Throwable error;

    @Override
    public void onPartialResponse(String partialResponse) {
      partialResponses.add(partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
      this.completeResponse = completeResponse;
    }

    @Override
    public void onError(Throwable error) {
      this.error = error;
    }
  }
}
