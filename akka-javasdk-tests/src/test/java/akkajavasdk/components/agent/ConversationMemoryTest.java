/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.Done;
import akka.javasdk.agent.ConversationHistory;
import akka.javasdk.agent.ConversationMemory;
import akka.javasdk.agent.ConversationMemory.AddInteractionCmd;
import akka.javasdk.agent.ConversationMessage.AiMessage;
import akka.javasdk.agent.ConversationMessage.UserMessage;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import static akka.Done.done;
import static org.assertj.core.api.Assertions.assertThat;

public class ConversationMemoryTest {

  private static final String COMPONENT_ID = "test-component";
  private static final Config config = ConfigFactory.load();
  private static final int tokenCount = 10; // Default token count for testing

  @Test
  public void shouldAddMessageToHistory() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new ConversationMemory(config));
    String userMsg = "Hello, how are you?";
    String aiMsg = "I'm fine, thanks for asking!";
    UserMessage userMessage = new UserMessage(userMsg, tokenCount);
    var aiMessage = new AiMessage(aiMsg, tokenCount);

    // when
    EventSourcedResult<Done> result = testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage, aiMessage));

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // Check events - ignoring timestamp comparison
    var events = result.getAllEvents();
    assertThat(events).hasSize(2);
    assertThat(events.get(0)).isInstanceOf(ConversationMemory.Event.UserMessageAdded.class);
    var userEvent = (ConversationMemory.Event.UserMessageAdded) events.get(0);
    assertThat(userEvent.componentId()).isEqualTo(COMPONENT_ID);
    assertThat(userEvent.message()).isEqualTo(userMsg);
    assertThat(userEvent.tokens()).isEqualTo(tokenCount);

    assertThat(events.get(1)).isInstanceOf(ConversationMemory.Event.AiMessageAdded.class);
    var aiEvent = (ConversationMemory.Event.AiMessageAdded) events.get(1);
    assertThat(aiEvent.componentId()).isEqualTo(COMPONENT_ID);
    assertThat(aiEvent.message()).isEqualTo(aiMsg);
    assertThat(aiEvent.tokens()).isEqualTo(tokenCount);

    // when retrieving history
    EventSourcedResult<ConversationHistory> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).containsExactly(
        new UserMessage(userMsg, tokenCount),
        new AiMessage(aiMsg, tokenCount));
  }

  @Test
  public void shouldAddMultipleMessagesToHistory() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new ConversationMemory(config));
    String userMsg1 = "Hello";
    String aiMsg1 = "Hi there!";
    String userMsg2 = "How are you?";
    String aiMsg2 = "I'm doing great!";

    var userMessage1 = new UserMessage(userMsg1, tokenCount);
    var aiMessage1 = new AiMessage(aiMsg1, tokenCount);
    var userMessage2 = new UserMessage(userMsg2, tokenCount);
    var aiMessage2 = new AiMessage(aiMsg2, tokenCount);

    // when
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage1, aiMessage1));
    EventSourcedResult<Done> result = testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage2, aiMessage2));

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // when retrieving history
    EventSourcedResult<ConversationHistory> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).containsExactly(
        new UserMessage(userMsg1, tokenCount),
        new AiMessage(aiMsg1, tokenCount),
        new UserMessage(userMsg2, tokenCount),
        new AiMessage(aiMsg2, tokenCount));
  }

  @Test
  public void shouldBeDeletable() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new ConversationMemory(config));
    String userMsg = "Hello";
    String aiMsg = "Hi there!";

    var userMessage = new UserMessage(userMsg, tokenCount);
    var aiMessage = new AiMessage(aiMsg, tokenCount);

    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage, aiMessage));

    // when
    EventSourcedResult<Done> clearResult = testKit.method(ConversationMemory::delete).invoke();

    // then
    assertThat(clearResult.getReply()).isEqualTo(done());

    // Check event - ignoring timestamp comparison
    var events = clearResult.getAllEvents();
    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isInstanceOf(ConversationMemory.Event.Deleted.class);

    // when retrieving history after clearing
    EventSourcedResult<ConversationHistory> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).isEmpty();
  }

  @Test
  public void shouldGetEmptyHistoryWhenNoMessagesAdded() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new ConversationMemory(config));

    // when
    EventSourcedResult<ConversationHistory> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).isEmpty();
  }

  @Test
  public void shouldRemoveOldestMessagesWhenLimitIsReached() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new ConversationMemory(config));

    // Calculate the total bytes needed for each message
    String userMsg1 = "First message";      // 13 bytes
    String aiMsg1 = "First response";       // 14 bytes
    String userMsg2 = "Second message";     // 14 bytes
    String aiMsg2 = "Second response";      // 15 bytes
    String userMsg3 = "Third message";      // 13 bytes
    String aiMsg3 = "Third response";       // 14 bytes
    String userMsg4 = "Fourth message";     // 14 bytes
    String aiMsg4 = "Fourth response";      // 15 bytes

    var userMessage1 = new UserMessage(userMsg1, tokenCount);
    var aiMessage1 = new AiMessage(aiMsg1, tokenCount);
    var userMessage2 = new UserMessage(userMsg2, tokenCount);
    var aiMessage2 = new AiMessage(aiMsg2, tokenCount);
    var userMessage3 = new UserMessage(userMsg3, tokenCount);
    var aiMessage3 = new AiMessage(aiMsg3, tokenCount);
    var userMessage4 = new UserMessage(userMsg4, tokenCount);
    var aiMessage4 = new AiMessage(aiMsg4, tokenCount);

    // Set buffer size to just fit messages 2, 3, and 4 (total 85 bytes)
    // userMsg2(14) + aiMsg2(15) + userMsg3(13) + aiMsg3(14) + userMsg4(14) + aiMsg4(15) = 85 bytes
    var limitedBuffer = new ConversationMemory.LimitedWindow(85);
    testKit.method(ConversationMemory::setLimitedWindow).invoke(limitedBuffer);

    // when
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage1, aiMessage1));
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage2, aiMessage2));
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage3, aiMessage3));
    EventSourcedResult<Done> result = testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage4, aiMessage4));

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // when retrieving history
    EventSourcedResult<ConversationHistory> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then - only the 3 most recent interactions should be present
    assertThat(historyResult.getReply().messages()).containsExactly(
        new UserMessage(userMsg2, tokenCount),
        new AiMessage(aiMsg2, tokenCount),
        new UserMessage(userMsg3, tokenCount),
        new AiMessage(aiMsg3, tokenCount),
        new UserMessage(userMsg4, tokenCount),
        new AiMessage(aiMsg4, tokenCount));
    assertThat(historyResult.getReply().messages().size()).isEqualTo(6);
  }

  @Test
  public void shouldMaintainCorrectSizeAfterMultipleOperations() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new ConversationMemory(config));

    // Calculate the total bytes needed for each message
    String userMsg1 = "First message";      // 13 bytes
    String aiMsg1 = "First response";       // 14 bytes
    String userMsg2 = "Second message";     // 14 bytes
    String aiMsg2 = "Second response";      // 15 bytes
    String userMsg3 = "Third message";      // 13 bytes
    String aiMsg3 = "Third response";       // 14 bytes

    var userMessage1 = new UserMessage(userMsg1, tokenCount);
    var aiMessage1 = new AiMessage(aiMsg1, tokenCount);
    var userMessage2 = new UserMessage(userMsg2, tokenCount);
    var aiMessage2 = new AiMessage(aiMsg2, tokenCount);
    var userMessage3 = new UserMessage(userMsg3, tokenCount);
    var aiMessage3 = new AiMessage(aiMsg3, tokenCount);

    // Set buffer size to just fit messages 1 and 2 (total 56 bytes)
    // userMsg1(13) + aiMsg1(14) + userMsg2(14) + aiMsg2(15) = 56 bytes
    var limitedBuffer = new ConversationMemory.LimitedWindow(56);
    testKit.method(ConversationMemory::setLimitedWindow).invoke(limitedBuffer);

    // when adding first interaction
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage1, aiMessage1));
    EventSourcedResult<ConversationHistory> result1 = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(result1.getReply().messages()).containsExactly(
        new UserMessage(userMsg1, tokenCount),
        new AiMessage(aiMsg1, tokenCount));
    assertThat(result1.getReply().messages().size()).isEqualTo(2);

    // when adding second interaction (reaching the limit)
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage2, aiMessage2));
    EventSourcedResult<ConversationHistory> result2 = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(result2.getReply().messages()).containsExactly(
        new UserMessage(userMsg1, tokenCount),
        new AiMessage(aiMsg1, tokenCount),
        new UserMessage(userMsg2, tokenCount),
        new AiMessage(aiMsg2, tokenCount));
    assertThat(result2.getReply().messages().size()).isEqualTo(4);

    // when adding third interaction (exceeding the limit)
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage3, aiMessage3));
    EventSourcedResult<ConversationHistory> result3 = testKit.method(ConversationMemory::getHistory).invoke();

    // then - first interaction should be removed
    assertThat(result3.getReply().messages()).containsExactly(
        new UserMessage(userMsg2, tokenCount),
        new AiMessage(aiMsg2, tokenCount),
        new UserMessage(userMsg3, tokenCount),
        new AiMessage(aiMsg3, tokenCount));
    assertThat(result3.getReply().messages().size()).isEqualTo(4);
  }

  @Test
  public void shouldRejectInvalidBufferSize() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new ConversationMemory(config));
    var invalidBuffer = new ConversationMemory.LimitedWindow(0);

    // when
    EventSourcedResult<Done> result = testKit.method(ConversationMemory::setLimitedWindow).invoke(invalidBuffer);

    // then
    assertThat(result.isError()).isTrue();
    assertThat(result.getError()).contains("Maximum size must be greater than 0");
  }

  @Test
  public void shouldSkipWhenFirstMessageGreaterBySize() {
    var testKit = EventSourcedTestKit.of(() -> new ConversationMemory(config));
    // Create a message larger than the buffer
    String largeUserMsg = "A".repeat(100);
    String largeAiMsg = "B".repeat(100);
    var userMessage = new UserMessage(largeUserMsg, tokenCount);
    var aiMessage = new AiMessage(largeAiMsg, tokenCount);

    // Set buffer size smaller than a single message
    var limitedBuffer = new ConversationMemory.LimitedWindow(50);
    testKit.method(ConversationMemory::setLimitedWindow).invoke(limitedBuffer);

    // Add the large interaction
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage, aiMessage));

    // Retrieve history
    EventSourcedResult<ConversationHistory> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // The history should be empty, as the first interaction cannot fit
    assertThat(historyResult.getReply().messages()).isEmpty();
  }

}