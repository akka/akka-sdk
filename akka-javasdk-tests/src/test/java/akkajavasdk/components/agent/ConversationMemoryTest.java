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
import org.junit.jupiter.api.Test;

import static akka.Done.done;
import static org.assertj.core.api.Assertions.assertThat;

public class ConversationMemoryTest {

  private static final String COMPONENT_ID = "test-component";

  @Test
  public void shouldAddMessageToHistory() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);
    String userMsg = "Hello, how are you?";
    String aiMsg = "I'm fine, thanks for asking!";

    // when
    EventSourcedResult<Done> result = testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMsg, aiMsg));

    // then
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(result.getAllEvents()).containsExactly(
        new ConversationMemory.Event.UserMessageAdded(COMPONENT_ID, userMsg),
        new ConversationMemory.Event.AiMessageAdded(COMPONENT_ID, aiMsg));

    // when retrieving history
    EventSourcedResult<ConversationHistory> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).containsExactly(
        new UserMessage(userMsg),
        new AiMessage(aiMsg));
  }

  @Test
  public void shouldAddMultipleMessagesToHistory() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);
    String userMsg1 = "Hello";
    String aiMsg1 = "Hi there!";
    String userMsg2 = "How are you?";
    String aiMsg2 = "I'm doing great!";

    // when
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMsg1, aiMsg1));
    EventSourcedResult<Done> result = testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMsg2, aiMsg2));

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // when retrieving history
    EventSourcedResult<ConversationHistory> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).containsExactly(
        new UserMessage(userMsg1),
        new AiMessage(aiMsg1),
        new UserMessage(userMsg2),
        new AiMessage(aiMsg2));
  }

  @Test
  public void shouldBeDeletable() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);
    String userMsg = "Hello";
    String aiMsg = "Hi there!";

    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMsg, aiMsg));

    // when
    EventSourcedResult<Done> clearResult = testKit.method(ConversationMemory::delete).invoke();

    // then
    assertThat(clearResult.getReply()).isEqualTo(done());
    assertThat(clearResult.getAllEvents()).containsExactly(new ConversationMemory.Event.Deleted());

    // when retrieving history after clearing
    EventSourcedResult<ConversationHistory> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).isEmpty();
  }

  @Test
  public void shouldGetEmptyHistoryWhenNoMessagesAdded() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);

    // when
    EventSourcedResult<ConversationHistory> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).isEmpty();
  }

  @Test
  public void shouldRemoveOldestMessagesWhenLimitIsReached() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);
    
    // Calculate the total bytes needed for each message
    String userMsg1 = "First message";      // 13 bytes
    String aiMsg1 = "First response";       // 14 bytes
    String userMsg2 = "Second message";     // 14 bytes
    String aiMsg2 = "Second response";      // 15 bytes
    String userMsg3 = "Third message";      // 13 bytes
    String aiMsg3 = "Third response";       // 14 bytes
    String userMsg4 = "Fourth message";     // 14 bytes
    String aiMsg4 = "Fourth response";      // 15 bytes
    
    // Set buffer size to just fit messages 2, 3, and 4 (total 85 bytes)
    // userMsg2(14) + aiMsg2(15) + userMsg3(13) + aiMsg3(14) + userMsg4(14) + aiMsg4(15) = 85 bytes
    var limitedBuffer = new ConversationMemory.LimitedWindow(85);
    testKit.method(ConversationMemory::setLimitedWindow).invoke(limitedBuffer);

    // when
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMsg1, aiMsg1));
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMsg2, aiMsg2));
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMsg3, aiMsg3));
    EventSourcedResult<Done> result = testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMsg4, aiMsg4));

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // when retrieving history
    EventSourcedResult<ConversationHistory> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then - only the 3 most recent interactions should be present
    assertThat(historyResult.getReply().messages()).containsExactly(
        new UserMessage(userMsg2),
        new AiMessage(aiMsg2),
        new UserMessage(userMsg3),
        new AiMessage(aiMsg3),
        new UserMessage(userMsg4),
        new AiMessage(aiMsg4));
    assertThat(historyResult.getReply().messages().size()).isEqualTo(6);
  }

  @Test
  public void shouldMaintainCorrectSizeAfterMultipleOperations() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);
    
    // Calculate the total bytes needed for each message
    String userMsg1 = "First message";      // 13 bytes
    String aiMsg1 = "First response";       // 14 bytes
    String userMsg2 = "Second message";     // 14 bytes
    String aiMsg2 = "Second response";      // 15 bytes
    String userMsg3 = "Third message";      // 13 bytes
    String aiMsg3 = "Third response";       // 14 bytes
    
    // Set buffer size to just fit messages 1 and 2 (total 56 bytes)
    // userMsg1(13) + aiMsg1(14) + userMsg2(14) + aiMsg2(15) = 56 bytes
    var limitedBuffer = new ConversationMemory.LimitedWindow(56);
    testKit.method(ConversationMemory::setLimitedWindow).invoke(limitedBuffer);

    // when adding first interaction
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMsg1, aiMsg1));
    EventSourcedResult<ConversationHistory> result1 = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(result1.getReply().messages()).containsExactly(
        new UserMessage(userMsg1),
        new AiMessage(aiMsg1));
    assertThat(result1.getReply().messages().size()).isEqualTo(2);

    // when adding second interaction (reaching the limit)
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMsg2, aiMsg2));
    EventSourcedResult<ConversationHistory> result2 = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(result2.getReply().messages()).containsExactly(
        new UserMessage(userMsg1),
        new AiMessage(aiMsg1),
        new UserMessage(userMsg2),
        new AiMessage(aiMsg2));
    assertThat(result2.getReply().messages().size()).isEqualTo(4);

    // when adding third interaction (exceeding the limit)
    testKit.method(ConversationMemory::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMsg3, aiMsg3));
    EventSourcedResult<ConversationHistory> result3 = testKit.method(ConversationMemory::getHistory).invoke();

    // then - first interaction should be removed
    assertThat(result3.getReply().messages()).containsExactly(
        new UserMessage(userMsg2),
        new AiMessage(aiMsg2),
        new UserMessage(userMsg3),
        new AiMessage(aiMsg3));
    assertThat(result3.getReply().messages().size()).isEqualTo(4);
  }

  @Test
  public void shouldRejectInvalidBufferSize() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);
    var invalidBuffer = new ConversationMemory.LimitedWindow(0);

    // when
    EventSourcedResult<Done> result = testKit.method(ConversationMemory::setLimitedWindow).invoke(invalidBuffer);

    // then
    assertThat(result.isError()).isTrue();
    assertThat(result.getError()).contains("Maximum size must be greater than 0");
  }
}
