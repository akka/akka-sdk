/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.Done;
import akka.javasdk.agent.SessionHistory;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMemoryEntity.AddInteractionCmd;
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
    String userMsg = "Hello, how are you?";
    String aiMsg = "I'm fine, thanks for asking!";
    UserMessage userMessage = new UserMessage(userMsg, TOKENS);
    var aiMessage = new AiMessage(aiMsg, TOKENS);

    // when
    EventSourcedResult<Done> result = testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage, aiMessage));

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // Check events - ignoring timestamp comparison
    var events = result.getAllEvents();
    assertThat(events).hasSize(2);
    assertThat(events.get(0)).isInstanceOf(SessionMemoryEntity.Event.UserMessageAdded.class);
    var userEvent = (SessionMemoryEntity.Event.UserMessageAdded) events.get(0);
    assertThat(userEvent.componentId()).isEqualTo(COMPONENT_ID);
    assertThat(userEvent.message()).isEqualTo(userMsg);
    assertThat(userEvent.tokens()).isEqualTo(TOKENS);

    assertThat(events.get(1)).isInstanceOf(SessionMemoryEntity.Event.AiMessageAdded.class);
    var aiEvent = (SessionMemoryEntity.Event.AiMessageAdded) events.get(1);
    assertThat(aiEvent.componentId()).isEqualTo(COMPONENT_ID);
    assertThat(aiEvent.message()).isEqualTo(aiMsg);
    assertThat(aiEvent.tokens()).isEqualTo(TOKENS);

    // when retrieving history
    EventSourcedResult<SessionHistory> historyResult =
        testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(historyResult.getReply().messages()).containsExactly(
        new UserMessage(userMsg, TOKENS),
        new AiMessage(aiMsg, TOKENS));
  }

  @Test
  public void shouldAddMultipleMessagesToHistory() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    String userMsg1 = "Hello";
    String aiMsg1 = "Hi there!";
    String userMsg2 = "How are you?";
    String aiMsg2 = "I'm doing great!";

    var userMessage1 = new UserMessage(userMsg1, TOKENS);
    var aiMessage1 = new AiMessage(aiMsg1, TOKENS);
    var userMessage2 = new UserMessage(userMsg2, TOKENS);
    var aiMessage2 = new AiMessage(aiMsg2, TOKENS);

    // when
    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage1, aiMessage1));
    EventSourcedResult<Done> result = testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage2, aiMessage2));

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // when retrieving history
    EventSourcedResult<SessionHistory> historyResult =
        testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(historyResult.getReply().messages()).containsExactly(
        new UserMessage(userMsg1, TOKENS),
        new AiMessage(aiMsg1, TOKENS),
        new UserMessage(userMsg2, TOKENS),
        new AiMessage(aiMsg2, TOKENS));
  }

  @Test
  public void shouldBeDeletable() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));
    String userMsg = "Hello";
    String aiMsg = "Hi there!";

    var userMessage = new UserMessage(userMsg, TOKENS);
    var aiMessage = new AiMessage(aiMsg, TOKENS);

    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage, aiMessage));

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

    // Calculate the total bytes needed for each message
    String userMsg1 = "First message";      // 13 bytes
    String aiMsg1 = "First response";       // 14 bytes
    String userMsg2 = "Second message";     // 14 bytes
    String aiMsg2 = "Second response";      // 15 bytes
    String userMsg3 = "Third message";      // 13 bytes
    String aiMsg3 = "Third response";       // 14 bytes
    String userMsg4 = "Fourth message";     // 14 bytes
    String aiMsg4 = "Fourth response";      // 15 bytes

    var userMessage1 = new UserMessage(userMsg1, TOKENS);
    var aiMessage1 = new AiMessage(aiMsg1, TOKENS);
    var userMessage2 = new UserMessage(userMsg2, TOKENS);
    var aiMessage2 = new AiMessage(aiMsg2, TOKENS);
    var userMessage3 = new UserMessage(userMsg3, TOKENS);
    var aiMessage3 = new AiMessage(aiMsg3, TOKENS);
    var userMessage4 = new UserMessage(userMsg4, TOKENS);
    var aiMessage4 = new AiMessage(aiMsg4, TOKENS);

    // Set buffer size to just fit messages 2, 3, and 4 (total 85 bytes)
    // userMsg2(14) + aiMsg2(15) + userMsg3(13) + aiMsg3(14) + userMsg4(14) + aiMsg4(15) = 85 bytes
    var limitedBuffer = new SessionMemoryEntity.LimitedWindow(85);
    testKit.method(SessionMemoryEntity::setLimitedWindow).invoke(limitedBuffer);

    // when
    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage1, aiMessage1));
    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage2, aiMessage2));
    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage3, aiMessage3));
    EventSourcedResult<Done> result = testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage4, aiMessage4));

    // then
    assertThat(result.getReply()).isEqualTo(done());

    // when retrieving history
    EventSourcedResult<SessionHistory> historyResult =
        testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then - only the 3 most recent interactions should be present
    assertThat(historyResult.getReply().messages()).containsExactly(
        new UserMessage(userMsg2, TOKENS),
        new AiMessage(aiMsg2, TOKENS),
        new UserMessage(userMsg3, TOKENS),
        new AiMessage(aiMsg3, TOKENS),
        new UserMessage(userMsg4, TOKENS),
        new AiMessage(aiMsg4, TOKENS));
    assertThat(historyResult.getReply().messages().size()).isEqualTo(6);
  }

  @Test
  public void shouldMaintainCorrectSizeAfterMultipleOperations() {
    // given
    var testKit = EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));

    // Calculate the total bytes needed for each message
    String userMsg1 = "First message";      // 13 bytes
    String aiMsg1 = "First response";       // 14 bytes
    String userMsg2 = "Second message";     // 14 bytes
    String aiMsg2 = "Second response";      // 15 bytes
    String userMsg3 = "Third message";      // 13 bytes
    String aiMsg3 = "Third response";       // 14 bytes

    var userMessage1 = new UserMessage(userMsg1, TOKENS);
    var aiMessage1 = new AiMessage(aiMsg1, TOKENS);
    var userMessage2 = new UserMessage(userMsg2, TOKENS);
    var aiMessage2 = new AiMessage(aiMsg2, TOKENS);
    var userMessage3 = new UserMessage(userMsg3, TOKENS);
    var aiMessage3 = new AiMessage(aiMsg3, TOKENS);

    // Set buffer size to just fit messages 1 and 2 (total 56 bytes)
    // userMsg1(13) + aiMsg1(14) + userMsg2(14) + aiMsg2(15) = 56 bytes
    var limitedBuffer = new SessionMemoryEntity.LimitedWindow(56);
    testKit.method(SessionMemoryEntity::setLimitedWindow).invoke(limitedBuffer);

    // when adding first interaction
    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage1, aiMessage1));
    EventSourcedResult<SessionHistory> result1 =
        testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(result1.getReply().messages()).containsExactly(
        new UserMessage(userMsg1, TOKENS),
        new AiMessage(aiMsg1, TOKENS));
    assertThat(result1.getReply().messages().size()).isEqualTo(2);

    // when adding second interaction (reaching the limit)
    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage2, aiMessage2));
    EventSourcedResult<SessionHistory> result2 =
        testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then
    assertThat(result2.getReply().messages()).containsExactly(
        new UserMessage(userMsg1, TOKENS),
        new AiMessage(aiMsg1, TOKENS),
        new UserMessage(userMsg2, TOKENS),
        new AiMessage(aiMsg2, TOKENS));
    assertThat(result2.getReply().messages().size()).isEqualTo(4);

    // when adding third interaction (exceeding the limit)
    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage3, aiMessage3));
    EventSourcedResult<SessionHistory> result3 =
        testKit.method(SessionMemoryEntity::getHistory).invoke(emptyGetHistory);

    // then - first interaction should be removed
    assertThat(result3.getReply().messages()).containsExactly(
        new UserMessage(userMsg2, TOKENS),
        new AiMessage(aiMsg2, TOKENS),
        new UserMessage(userMsg3, TOKENS),
        new AiMessage(aiMsg3, TOKENS));
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
    // Create a message larger than the buffer
    String largeUserMsg = "A".repeat(100);
    String largeAiMsg = "B".repeat(100);
    var userMessage = new UserMessage(largeUserMsg, TOKENS);
    var aiMessage = new AiMessage(largeAiMsg, TOKENS);

    // Set buffer size smaller than a single message
    var limitedBuffer = new SessionMemoryEntity.LimitedWindow(50);
    testKit.method(SessionMemoryEntity::setLimitedWindow).invoke(limitedBuffer);

    // Add the large interaction
    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, userMessage, aiMessage));

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
    String userMsg1 = "Hello";
    String aiMsg1 = "Hi!";
    String userMsg2 = "How are you?";
    String aiMsg2 = "I'm good, thanks!";
    var um1 = new UserMessage(userMsg1, 5);
    var aim1 = new AiMessage(aiMsg1, 3);
    var um2 = new UserMessage(userMsg2, 7);
    var aim2 = new AiMessage(aiMsg2, 6);
    var totalTokens = um1.tokens() + aim1.tokens() + um2.tokens() + aim2.tokens();

    // when
    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, um1, aim1));
    testKit.method(SessionMemoryEntity::addInteraction)
        .invoke(new AddInteractionCmd(COMPONENT_ID, um2, aim2));

    // then
    var state = testKit.getState();
    assertThat(state.totalTokenUsage()).isEqualTo(totalTokens);

    // And events should include token usage as well
    var events = testKit.getAllEvents();
    assertThat(events).hasSize(4);
    assertThat(events.get(2)).isInstanceOf(SessionMemoryEntity.Event.UserMessageAdded.class);
    var userEvent = (SessionMemoryEntity.Event.UserMessageAdded) events.get(2);
    assertThat(userEvent.totalTokenUsage()).isEqualTo(totalTokens - aim2.tokens());

    assertThat(events.get(3)).isInstanceOf(SessionMemoryEntity.Event.AiMessageAdded.class);
    var userEvent2 = (SessionMemoryEntity.Event.AiMessageAdded) events.get(3);
    assertThat(userEvent2.totalTokenUsage()).isEqualTo(totalTokens);
  }

  @Test
  public void shouldReturnOnlyLastNMessages() {
    // Create test kit with the configuration
    EventSourcedTestKit<SessionMemoryEntity.State, SessionMemoryEntity.Event, SessionMemoryEntity> testKit =
        EventSourcedTestKit.of(() -> new SessionMemoryEntity(config));

    // Add several interactions
    String[] userMsgs = {"U1", "U2", "U3", "U4"};
    String[] aiMsgs = {"A1", "A2", "A3", "A4"};
    for (int i = 0; i < userMsgs.length; i++) {
      testKit.method(SessionMemoryEntity::addInteraction)
          .invoke(new AddInteractionCmd(COMPONENT_ID, new UserMessage(userMsgs[i], TOKENS), new AiMessage(aiMsgs[i], TOKENS)));
    }

    // Request only the last 4 messages (should be: U3, A3, U4, A4)
    var lastN = 4;
    EventSourcedResult<SessionHistory> result = testKit
        .method(SessionMemoryEntity::getHistory)
        .invoke(new SessionMemoryEntity.GetHistoryCmd(Optional.of(lastN)));

    // The expected last 4 messages
    var expected = List.of(
        new UserMessage("U3", TOKENS),
        new AiMessage("A3", TOKENS),
        new UserMessage("U4", TOKENS),
        new AiMessage("A4", TOKENS)
    );

    assertThat(result.getReply().messages()).containsExactlyElementsOf(expected);
  }

}