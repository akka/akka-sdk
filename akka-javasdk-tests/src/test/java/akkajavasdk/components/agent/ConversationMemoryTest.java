/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.Done;
import akka.javasdk.agent.AiMessage;
import akka.javasdk.agent.ConversationMemory;
import akka.javasdk.agent.UserMessage;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

import static akka.Done.done;
import static org.assertj.core.api.Assertions.assertThat;

public class ConversationMemoryTest {

  @Test
  public void shouldAddMessageToHistory() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);
    String msg = "Hello, how are you?";

    // when
    EventSourcedResult<Done> result = testKit.method(ConversationMemory::addUserMessage).invoke(msg);

    // then
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(result.getAllEvents()).containsExactly(new ConversationMemory.Event.UserMessageAdded(msg));

    // when retrieving history
    EventSourcedResult<ConversationMemory.History> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).containsExactly(UserMessage.of(msg));
  }

  @Test
  public void shouldAddMultipleMessagesToHistory() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);
    var message1 = UserMessage.of("Hello");
    var message2 = AiMessage.of("Hi there!");
    var message3 = UserMessage.of("How are you?");

    // when
    testKit.method(ConversationMemory::addUserMessage).invoke(message1.getText());
    testKit.method(ConversationMemory::addAiMessage).invoke(message2.getText());
    EventSourcedResult<Done> result = testKit.method(ConversationMemory::addUserMessage).invoke(message3.getText());

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // when retrieving history
    EventSourcedResult<ConversationMemory.History> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).containsExactly(message1, message2, message3);
  }

  @Test
  public void shouldBeDeletable() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);
    var message1 = "Hello";
    var message2 = "Hi there!";

    testKit.method(ConversationMemory::addUserMessage).invoke(message1);
    testKit.method(ConversationMemory::addAiMessage).invoke(message2);

    // when
    EventSourcedResult<Done> clearResult = testKit.method(ConversationMemory::delete).invoke();

    // then
    assertThat(clearResult.getReply()).isEqualTo(done());
    assertThat(clearResult.getAllEvents()).containsExactly(new ConversationMemory.Event.Deleted());

    // when retrieving history after clearing
    EventSourcedResult<ConversationMemory.History> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).isEmpty();
  }

  @Test
  public void shouldGetEmptyHistoryWhenNoMessagesAdded() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);

    // when
    EventSourcedResult<ConversationMemory.History> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(historyResult.getReply().messages()).isEmpty();
  }

  @Test
  public void shouldRemoveOldestMessagesWhenLimitIsReached() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);
    var limitedBuffer = new ConversationMemory.LimitedWindow(3);

    // Set buffer size to 3
    testKit.method(ConversationMemory::setLimitedWindow).invoke(limitedBuffer);

    // Add 5 messages (exceeding the limit)
    var message1 = UserMessage.of("First message");
    var message2 = UserMessage.of("Second message");
    var message3 = UserMessage.of("Third message");
    var message4 = UserMessage.of("Fourth message");
    var message5 = UserMessage.of("Fifth message");

    // when
    testKit.method(ConversationMemory::addUserMessage).invoke(message1.getText());
    testKit.method(ConversationMemory::addUserMessage).invoke(message2.getText());
    testKit.method(ConversationMemory::addUserMessage).invoke(message3.getText());
    testKit.method(ConversationMemory::addUserMessage).invoke(message4.getText());
    EventSourcedResult<Done> result = testKit.method(ConversationMemory::addUserMessage).invoke(message5.getText());

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // when retrieving history
    EventSourcedResult<ConversationMemory.History> historyResult = testKit.method(ConversationMemory::getHistory).invoke();

    // then - only the 3 most recent messages should be present
    assertThat(historyResult.getReply().messages()).containsExactly(message3, message4, message5);
    assertThat(historyResult.getReply().size()).isEqualTo(3);
  }

  @Test
  public void shouldMaintainCorrectSizeAfterMultipleOperations() {
    // given
    var testKit = EventSourcedTestKit.of(ConversationMemory::new);
    var limitedBuffer = new ConversationMemory.LimitedWindow(2);

    // Set buffer size to 2
    testKit.method(ConversationMemory::setLimitedWindow).invoke(limitedBuffer);

    // Add messages and check size after each operation
    var message1 = UserMessage.of("First message");
    var message2 = AiMessage.of("Second message");
    var message3 = UserMessage.of("Third message");

    // when adding first message
    testKit.method(ConversationMemory::addUserMessage).invoke(message1.getText());
    EventSourcedResult<ConversationMemory.History> result1 = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(result1.getReply().messages()).containsExactly(message1);
    assertThat(result1.getReply().size()).isEqualTo(1);

    // when adding second message (reaching the limit)
    testKit.method(ConversationMemory::addAiMessage).invoke(message2.getText());
    EventSourcedResult<ConversationMemory.History> result2 = testKit.method(ConversationMemory::getHistory).invoke();

    // then
    assertThat(result2.getReply().messages()).containsExactly(message1, message2);
    assertThat(result2.getReply().size()).isEqualTo(2);

    // when adding third message (exceeding the limit)
    testKit.method(ConversationMemory::addUserMessage).invoke(message3.getText());
    EventSourcedResult<ConversationMemory.History> result3 = testKit.method(ConversationMemory::getHistory).invoke();

    // then - first message should be removed
    assertThat(result3.getReply().messages()).containsExactly(message2, message3);
    assertThat(result3.getReply().size()).isEqualTo(2);
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
