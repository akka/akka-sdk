/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.Done;
import akka.javasdk.agent.SessionHistory;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMemoryEntity.AddInteractionCmd;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.UserMessage;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static akka.Done.done;
import static org.assertj.core.api.Assertions.assertThat;

public class SessionMemoryEntityTest {

  private static final String COMPONENT_ID = "test-component";
  private static final Config config = ConfigFactory.load();
  private static final int TOKENS = 10; // Default token count for testing

  private SessionMemoryEntity.GetHistoryCmd emptyGetHistory = new SessionMemoryEntity.GetHistoryCmd(Optional.empty());

  @Test
  public void shouldAddMessageToHistory() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    var timestamp = System.currentTimeMillis();
    String userMsg = "Hello, how are you?";
    String aiMsg = "I'm fine, thanks for asking!";
    UserMessage userMessage = new UserMessage(timestamp, userMsg, COMPONENT_ID);
    var aiMessage = new AiMessage(timestamp, aiMsg, COMPONENT_ID, TOKENS, TOKENS);

    // when
    EventSourcedResult<Done> result = testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage, aiMessage));

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // Check events - ignoring timestamp comparison
    var events = result.getAllEvents();
    assertThat(events).hasSize(2);
    assertThat(events.getFirst()).isInstanceOf(SessionMemoryEntity.Event.UserMessageAdded.class);
    var userEvent = (SessionMemoryEntity.Event.UserMessageAdded) events.getFirst();
    assertThat(userEvent.componentId()).isEqualTo(COMPONENT_ID);
    assertThat(userEvent.message()).isEqualTo(userMsg);
    assertThat(userEvent.timestamp()).isEqualTo(timestamp);

    assertThat(events.get(1)).isInstanceOf(SessionMemoryEntity.Event.AiMessageAdded.class);
    var aiEvent = (SessionMemoryEntity.Event.AiMessageAdded) events.get(1);
    assertThat(aiEvent.componentId()).isEqualTo(COMPONENT_ID);
    assertThat(aiEvent.message()).isEqualTo(aiMsg);
    assertThat(aiEvent.inputTokens()).isEqualTo(TOKENS);
    assertThat(aiEvent.outputTokens()).isEqualTo(TOKENS);
    assertThat(aiEvent.timestamp()).isEqualTo(timestamp);

    // when retrieving history
    EventSourcedResult<SessionHistory> historyResult =
      testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(historyResult.getReply().messages()).containsExactly(
      userMessage,
      aiMessage);
  }

  @Test
  public void shouldAddMultipleMessagesToHistory() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    var timestamp = System.currentTimeMillis();
    String userMsg1 = "Hello";
    String aiMsg1 = "Hi there!";
    String userMsg2 = "How are you?";
    String aiMsg2 = "I'm doing great!";

    var userMessage1 = new UserMessage(timestamp, userMsg1, COMPONENT_ID);
    var aiMessage1 = new AiMessage(timestamp, aiMsg1, COMPONENT_ID, TOKENS, TOKENS);
    var userMessage2 = new UserMessage(timestamp + 1, userMsg2, COMPONENT_ID);
    var aiMessage2 = new AiMessage(timestamp + 1, aiMsg2, COMPONENT_ID, TOKENS, TOKENS);

    // when
    testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage1, aiMessage1));
    EventSourcedResult<Done> result = testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage2, aiMessage2));

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // when retrieving history
    EventSourcedResult<SessionHistory> historyResult =
      testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(historyResult.getReply().messages()).containsExactly(
      userMessage1,
      aiMessage1,
      userMessage2,
      aiMessage2);
  }

  @Test
  public void shouldBeCompactable() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    var timestamp = System.currentTimeMillis();

    var userMessage1 = new UserMessage(timestamp, "Hello", COMPONENT_ID);
    var aiMessage1 = new AiMessage(timestamp, "Hi there!", COMPONENT_ID, TOKENS, TOKENS);

    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(userMessage1, aiMessage1));

    // when
    EventSourcedResult<SessionHistory> historyResult =
        testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);
    var sequenceNumber = historyResult.getReply().sequenceNumber();
    assertThat(sequenceNumber).isEqualTo(2L);
    var userMessage2 = new UserMessage(timestamp, "Hey", COMPONENT_ID);
    var aiMessage2 = new AiMessage(timestamp, "Hi!", COMPONENT_ID, TOKENS / 2, TOKENS / 2);
    var cmd = new SessionMemoryEntity.CompactionCmd(userMessage2, aiMessage2, sequenceNumber);
    EventSourcedResult<Done> compactResult = testKit.method(SessionMemoryEntity::compactHistory)
        .invoke(cmd);

    // then
    assertThat(compactResult.getReply()).isEqualTo(done());

    // Check event - ignoring timestamp comparison
    var events = compactResult.getAllEvents();
    assertThat(events).hasSize(3);
    assertThat(events.get(0)).isInstanceOf(SessionMemoryEntity.Event.HistoryCleared.class);
    assertThat(events.get(1)).isInstanceOf(SessionMemoryEntity.Event.UserMessageAdded.class);
    assertThat(events.get(2)).isInstanceOf(SessionMemoryEntity.Event.AiMessageAdded.class);

    var aiMsgAdded = (SessionMemoryEntity.Event.AiMessageAdded) events.get(2);
    assertThat(aiMsgAdded.inputTokens()).isEqualTo(TOKENS / 2);
    assertThat(aiMsgAdded.outputTokens()).isEqualTo(TOKENS / 2);

    // when retrieving history after compacting
    EventSourcedResult<SessionHistory> historyResult2 =
        testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(historyResult2.getReply().messages()).containsExactly(
        userMessage2,
        aiMessage2);
  }

  @Test
  public void shouldHandleConcurrentUpdatesWhenCompacting() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    var timestamp = System.currentTimeMillis();

    var userMessage1 = new UserMessage(timestamp, "Hello", COMPONENT_ID);
    var aiMessage1 = new AiMessage(timestamp, "Hi there!", COMPONENT_ID, TOKENS, TOKENS);

    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(userMessage1, aiMessage1));

    // when
    EventSourcedResult<SessionHistory> historyResult =
        testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);
    var sequenceNumber = historyResult.getReply().sequenceNumber();
    assertThat(sequenceNumber).isEqualTo(2L);
    var userMessage2 = new UserMessage(timestamp, "Hey", COMPONENT_ID);
    var aiMessage2 = new AiMessage(timestamp, "Hi!", COMPONENT_ID, TOKENS / 2, TOKENS / 2);
    var cmd = new SessionMemoryEntity.CompactionCmd(userMessage2, aiMessage2, sequenceNumber);

    // but before making the compaction update, there is some other update
    var userMessage3 = new UserMessage(timestamp, "I'm Alice", COMPONENT_ID);
    var aiMessage3 = new AiMessage(timestamp, "Hi Alice, I'm bot", COMPONENT_ID, TOKENS, TOKENS);
    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(userMessage3, aiMessage3));

    EventSourcedResult<Done> compactResult = testKit.method(SessionMemoryEntity::compactHistory)
        .invoke(cmd);

    // then
    assertThat(compactResult.getReply()).isEqualTo(done());

    // Check event
    var events = compactResult.getAllEvents();
    assertThat(events).hasSize(5); // HistoryCleared, User and AI summary, + the concurrent messages

    // when retrieving history after compacting
    EventSourcedResult<SessionHistory> historyResult2 =
        testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(historyResult2.getReply().messages().stream().map(SessionMessage::text).toList()).containsExactly(
        userMessage2.text(),
        aiMessage2.text(),
        userMessage3.text(),
        aiMessage3.text());
  }

  @Test
  public void shouldBeDeletable() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    var timestamp = System.currentTimeMillis();
    String userMsg = "Hello";
    String aiMsg = "Hi there!";

    var userMessage = new UserMessage(timestamp, userMsg, COMPONENT_ID);
    var aiMessage = new AiMessage(timestamp, aiMsg, COMPONENT_ID, TOKENS, TOKENS);

    testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage, aiMessage));

    // when
    EventSourcedResult<Done> clearResult = testKit.method(SessionMemoryEntity::delete).invoke();

    // then
    assertThat(clearResult.getReply()).isEqualTo(done());

    // Check event - ignoring timestamp comparison
    var events = clearResult.getAllEvents();
    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isInstanceOf(SessionMemoryEntity.Event.Deleted.class);

    // when retrieving history after clearing
    EventSourcedResult<SessionHistory> historyResult =
      testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(historyResult.getReply().messages()).isEmpty();
  }

  @Test
  public void shouldGetEmptyHistoryWhenNoMessagesAdded() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));

    // when
    EventSourcedResult<SessionHistory> historyResult =
      testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(historyResult.getReply().messages()).isEmpty();
  }

  @Test
  public void shouldRemoveOldestMessagesWhenLimitIsReached() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    var timestamp = System.currentTimeMillis();

    // Calculate the total bytes needed for each message
    String userMsg1 = "First message";      // 13 bytes
    String aiMsg1 = "First response";       // 14 bytes
    String userMsg2 = "Second message";     // 14 bytes
    String aiMsg2 = "Second response";      // 15 bytes
    String userMsg3 = "Third message";      // 13 bytes
    String aiMsg3 = "Third response";       // 14 bytes
    String userMsg4 = "Fourth message";     // 14 bytes
    String aiMsg4 = "Fourth response";      // 15 bytes

    var userMessage1 = new UserMessage(timestamp, userMsg1, COMPONENT_ID);
    var aiMessage1 = new AiMessage(timestamp, aiMsg1, COMPONENT_ID, TOKENS, TOKENS);
    var userMessage2 = new UserMessage(timestamp + 1, userMsg2, COMPONENT_ID);
    var aiMessage2 = new AiMessage(timestamp + 1, aiMsg2, COMPONENT_ID, TOKENS, TOKENS);
    var userMessage3 = new UserMessage(timestamp + 2, userMsg3, COMPONENT_ID);
    var aiMessage3 = new AiMessage(timestamp + 2, aiMsg3, COMPONENT_ID, TOKENS, TOKENS);
    var userMessage4 = new UserMessage(timestamp + 3, userMsg4, COMPONENT_ID);
    var aiMessage4 = new AiMessage(timestamp + 3, aiMsg4, COMPONENT_ID, TOKENS, TOKENS);

    // Set buffer size to just fit messages 2, 3, and 4 (total 85 bytes)
    // userMsg2(14) + aiMsg2(15) + userMsg3(13) + aiMsg3(14) + userMsg4(14) + aiMsg4(15) = 85 bytes
    var limitedBuffer = new SessionMemoryEntity.LimitedWindow(85);
    testKit.method(SessionMemoryEntity::setLimitedWindow).invoke(limitedBuffer);

    // when
    testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage1, aiMessage1));
    testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage2, aiMessage2));
    testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage3, aiMessage3));
    EventSourcedResult<Done> result = testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage4, aiMessage4));

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // when retrieving history
    EventSourcedResult<SessionHistory> historyResult =
      testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then - only the 3 most recent interactions should be present
    assertThat(historyResult.getReply().messages()).containsExactly(
      userMessage2,
      aiMessage2,
      userMessage3,
      aiMessage3,
      userMessage4,
      aiMessage4);
    assertThat(historyResult.getReply().messages().size()).isEqualTo(6);
  }

  @Test
  public void shouldMaintainCorrectSizeAfterMultipleOperations() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    var timestamp = System.currentTimeMillis();

    // Calculate the total bytes needed for each message
    String userMsg1 = "First message";      // 13 bytes
    String aiMsg1 = "First response";       // 14 bytes
    String userMsg2 = "Second message";     // 14 bytes
    String aiMsg2 = "Second response";      // 15 bytes
    String userMsg3 = "Third message";      // 13 bytes
    String aiMsg3 = "Third response";       // 14 bytes

    var userMessage1 = new UserMessage(timestamp, userMsg1, COMPONENT_ID);
    var aiMessage1 = new AiMessage(timestamp, aiMsg1, COMPONENT_ID, TOKENS, TOKENS);
    var userMessage2 = new UserMessage(timestamp + 1, userMsg2, COMPONENT_ID);
    var aiMessage2 = new AiMessage(timestamp + 1, aiMsg2, COMPONENT_ID, TOKENS, TOKENS);
    var userMessage3 = new UserMessage(timestamp + 2, userMsg3, COMPONENT_ID);
    var aiMessage3 = new AiMessage(timestamp + 2, aiMsg3, COMPONENT_ID, TOKENS, TOKENS);

    // Set buffer size to just fit messages 1 and 2 (total 56 bytes)
    // userMsg1(13) + aiMsg1(14) + userMsg2(14) + aiMsg2(15) = 56 bytes
    var limitedBuffer = new SessionMemoryEntity.LimitedWindow(56);
    testKit.method(SessionMemoryEntity::setLimitedWindow).invoke(limitedBuffer);

    // when adding first interaction
    testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage1, aiMessage1));
    EventSourcedResult<SessionHistory> result1 =
      testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(result1.getReply().messages()).containsExactly(
      new UserMessage(timestamp, userMsg1, COMPONENT_ID),
      new AiMessage(timestamp, aiMsg1, COMPONENT_ID, TOKENS, TOKENS));
    assertThat(result1.getReply().messages().size()).isEqualTo(2);

    // when adding second interaction (reaching the limit)
    testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage2, aiMessage2));
    EventSourcedResult<SessionHistory> result2 =
      testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(result2.getReply().messages()).containsExactly(
      new UserMessage(timestamp, userMsg1, COMPONENT_ID),
      new AiMessage(timestamp, aiMsg1, COMPONENT_ID, TOKENS, TOKENS),
      new UserMessage(timestamp + 1, userMsg2, COMPONENT_ID),
      new AiMessage(timestamp + 1, aiMsg2, COMPONENT_ID, TOKENS, TOKENS));
    assertThat(result2.getReply().messages().size()).isEqualTo(4);

    // when adding third interaction (exceeding the limit)
    testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage3, aiMessage3));
    EventSourcedResult<SessionHistory> result3 =
      testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then - first interaction should be removed
    assertThat(result3.getReply().messages()).containsExactly(
      new UserMessage(timestamp + 1, userMsg2, COMPONENT_ID),
      new AiMessage(timestamp + 1, aiMsg2, COMPONENT_ID, TOKENS, TOKENS),
      new UserMessage(timestamp + 2, userMsg3, COMPONENT_ID),
      new AiMessage(timestamp + 2, aiMsg3, COMPONENT_ID, TOKENS, TOKENS));
    assertThat(result3.getReply().messages().size()).isEqualTo(4);
  }

  @Test
  public void shouldRejectInvalidBufferSize() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    var invalidBuffer = new SessionMemoryEntity.LimitedWindow(0);

    // when
    EventSourcedResult<Done> result = testKit.method(SessionMemoryEntity::setLimitedWindow).invoke(invalidBuffer);

    // then
    assertThat(result.isError()).isTrue();
    assertThat(result.getError()).contains("Maximum size must be greater than 0");
  }

  @Test
  public void shouldSkipWhenFirstMessageGreaterBySize() {
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    var timestamp = System.currentTimeMillis();
    // Create a message larger than the buffer
    String largeUserMsg = "A".repeat(100);
    String largeAiMsg = "B".repeat(100);
    var userMessage = new UserMessage(timestamp, largeUserMsg, COMPONENT_ID);
    var aiMessage = new AiMessage(timestamp, largeAiMsg, COMPONENT_ID, TOKENS, TOKENS);

    // Set buffer size smaller than a single message
    var limitedBuffer = new SessionMemoryEntity.LimitedWindow(50);
    testKit.method(SessionMemoryEntity::setLimitedWindow).invoke(limitedBuffer);

    // Add the large interaction
    testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(userMessage, aiMessage));

    // Retrieve history
    EventSourcedResult<SessionHistory> historyResult =
      testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // The history should be empty, as the first interaction cannot fit
    assertThat(historyResult.getReply().messages()).isEmpty();
  }

  @Test
  public void shouldTrackTotalTokenUsage() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    var timestamp = System.currentTimeMillis();
    String userMsg1 = "Hello";
    String aiMsg1 = "Hi!";
    String userMsg2 = "How are you?";
    String aiMsg2 = "I'm good, thanks!";
    var um1 = new UserMessage(timestamp, userMsg1, COMPONENT_ID);
    var aim1 = new AiMessage(timestamp, aiMsg1, COMPONENT_ID, 5, 3);
    var firstTotal = aim1.inputTokens() + aim1.outputTokens();

    var um2 = new UserMessage(timestamp +1, userMsg2, COMPONENT_ID);
    // input from second AiMessage sums up with the firstTotal
    var aim2 = new AiMessage(timestamp + 1, aiMsg2, COMPONENT_ID, firstTotal + 7, 6);

    // the last message has the total (total from aim1 is already added to it)
    var totalTokens = aim2.inputTokens() + aim2.outputTokens();

    // when
    testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(um1, aim1));

    testKit.method(SessionMemoryEntity::addInteraction)
      .invoke(new AddInteractionCmd(um2, aim2));

    // then
    var state = testKit.getState();
    assertThat(state.totalTokenUsage()).isEqualTo(totalTokens);

  }

  @Test
  public void shouldReturnOnlyLastNMessages() {
    // Create test kit with the configuration
    EventSourcedTestKit<SessionMemoryEntity.State, SessionMemoryEntity.Event, SessionMemoryEntity> testKit =
      EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    var timestamp = System.currentTimeMillis();

    // Add several interactions
    String[] userMsgs = {"U1", "U2", "U3", "U4"};
    String[] aiMsgs = {"A1", "A2", "A3", "A4"};
    for (int i = 0; i < userMsgs.length; i++) {
      testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(
          new UserMessage(timestamp, userMsgs[i], COMPONENT_ID),
          new AiMessage(timestamp, aiMsgs[i], COMPONENT_ID, TOKENS, TOKENS)));
    }

    // Request only the last 4 messages (should be: U3, A3, U4, A4)
    var lastN = 4;
    EventSourcedResult<SessionHistory> result = testKit
      .method(SessionMemoryEntity::getHistory)
      .invoke(new SessionMemoryEntity.GetHistoryCmd(Optional.of(lastN)));

    // The expected last 4 messages
    var expected = List.of(
      new UserMessage(timestamp, "U3", COMPONENT_ID),
      new AiMessage(timestamp, "A3", COMPONENT_ID, TOKENS, TOKENS),
      new UserMessage(timestamp, "U4", COMPONENT_ID),
      new AiMessage(timestamp, "A4", COMPONENT_ID, TOKENS, TOKENS)
    );

    assertThat(result.getReply().messages()).containsExactlyElementsOf(expected);
  }

  @Test
  public void shouldReturnEmptyHistoryWithLastN() {
    EventSourcedTestKit<SessionMemoryEntity.State, SessionMemoryEntity.Event, SessionMemoryEntity> testKit =
      EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));

    var lastN = 4;
    EventSourcedResult<SessionHistory> result = testKit
      .method(SessionMemoryEntity::getHistory)
      .invoke(new SessionMemoryEntity.GetHistoryCmd(Optional.of(lastN)));

    assertThat(result.getReply().messages()).isEmpty();
  }

}
