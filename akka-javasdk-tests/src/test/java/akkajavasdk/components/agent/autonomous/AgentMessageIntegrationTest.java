/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.failTask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.autonomous.AgentMessage;
import akka.javasdk.agent.autonomous.AgentSetup;
import akka.javasdk.agent.autonomous.MessageState;
import akka.javasdk.agent.autonomous.MessageStatus;
import akka.javasdk.agent.autonomous.Notification;
import akka.javasdk.client.AutonomousAgentClient;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kalix.runtime.agent.autonomous.AutonomousAgentSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Sends messages from a client to a running autonomous agent. */
public class AgentMessageIntegrationTest extends TestKitSupport {

  private final TestModelProvider testAgentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(TestAutonomousAgent.class, testAgentModel);
  }

  @AfterEach
  public void afterEach() {
    testAgentModel.reset();
  }

  @Test
  public void aHeldMessageReachesTheNextTask() {
    var text = "Use metric units. " + UUID.randomUUID();
    testAgentModel.fixedResponse(failTask("the message was not shown"));
    testAgentModel
        .whenUserMessage(
            userMessage ->
                userMessage.contents().stream()
                    .anyMatch(
                        content ->
                            content instanceof MessageContent.TextMessageContent textContent
                                && textContent.text().contains(text)))
        .reply(completeTask(new TestTasks.TestResult("metric", 1)));

    var agentClient =
        componentClient.forAutonomousAgent(TestAutonomousAgent.class, UUID.randomUUID().toString());
    var notifications = subscribe(agentClient);
    agentClient.setup(AgentSetup.create());

    var messageId = UUID.randomUUID().toString();
    var message = AgentMessage.of(text).messageId(messageId);

    var acknowledgement = agentClient.sendMessage(message);
    assertThat(acknowledgement.messageId()).isEqualTo(messageId);
    assertThat(acknowledgement.status()).isEqualTo(new MessageStatus.Held(Optional.empty()));
    assertThat(acknowledgement.duplicate()).isFalse();

    var retry = agentClient.sendMessage(message);
    assertThat(retry.messageId()).isEqualTo(messageId);
    assertThat(retry.status()).isEqualTo(new MessageStatus.Held(Optional.empty()));
    assertThat(retry.duplicate()).isTrue();

    var stateBefore = agentClient.getState();
    assertThat(stateBefore.pendingMessages())
        .containsExactly(new MessageState(messageId, new MessageStatus.Held(Optional.empty())));
    assertThat(stateBefore.recentMessages()).isEmpty();

    var taskId =
        componentClient
            .forTask(UUID.randomUUID().toString())
            .create(TestTasks.TEST_TASK.instructions("Report the temperature."));
    agentClient.assignTasks(taskId);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isPresent();
            });

    var consumed =
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .until(
                () -> agentClient.sendMessage(message),
                ack -> ack.status() instanceof MessageStatus.Consumed);
    assertThat(consumed.messageId()).isEqualTo(messageId);
    assertThat(consumed.duplicate()).isTrue();
    var consumedStatus = (MessageStatus.Consumed) consumed.status();
    assertThat(consumedStatus.taskId()).isEqualTo(taskId);
    assertThat(consumedStatus.interactionId()).isNotEmpty();

    var stateAfter = agentClient.getState();
    assertThat(stateAfter.pendingMessages()).isEmpty();
    assertThat(stateAfter.recentMessages())
        .containsExactly(new MessageState(messageId, consumedStatus));

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(ofType(notifications, Notification.MessagesConsumed.class))
                    .containsExactly(
                        new Notification.MessagesConsumed(
                            taskId, List.of(messageId), consumedStatus.interactionId())));
    assertThat(ofType(notifications, Notification.MessagesDropped.class)).isEmpty();

    // the ledger writes the record next to the outcome of the model call, so it can lag
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        getLedgerClient()
                            .getInteraction(consumedStatus.interactionId())
                            .inputText())
                    .contains(text));
  }

  @Test
  public void aMessageWhoseTaskEndsFirstIsDropped() throws InterruptedException {
    var called = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    // complete_task ends the model call, so no later request shows the message
    testAgentModel.fixedResponse(
        input -> {
          called.countDown();
          try {
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return new AiResponse(completeTask(new TestTasks.TestResult("done", 1)));
        });

    var agentClient =
        componentClient.forAutonomousAgent(TestAutonomousAgent.class, UUID.randomUUID().toString());
    var notifications = subscribe(agentClient);
    agentClient.setup(AgentSetup.create());

    var taskId =
        componentClient
            .forTask(UUID.randomUUID().toString())
            .create(TestTasks.TEST_TASK.instructions("Report the temperature."));
    agentClient.assignTasks(taskId);
    assertThat(called.await(10, TimeUnit.SECONDS)).isTrue();

    var messageId = UUID.randomUUID().toString();
    var message = AgentMessage.of("Use metric units.").messageId(messageId);
    var acknowledgement = agentClient.sendMessage(message);
    // the agent stored the message before the model call ends
    assertThat(acknowledgement.status()).isEqualTo(new MessageStatus.ForCurrentTask(taskId));
    assertThat(acknowledgement.duplicate()).isFalse();

    release.countDown();

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isPresent();
            });

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(ofType(notifications, Notification.MessagesDropped.class))
                    .containsExactly(new Notification.MessagesDropped(taskId, List.of(messageId))));
    assertThat(ofType(notifications, Notification.MessagesConsumed.class)).isEmpty();

    var dropped = new MessageStatus.Dropped(taskId);
    assertThat(agentClient.getState().recentMessages())
        .containsExactly(new MessageState(messageId, dropped));
    var retry = agentClient.sendMessage(message);
    assertThat(retry.status()).isEqualTo(dropped);
    assertThat(retry.duplicate()).isTrue();
  }

  @Test
  public void aMessageForATaskThatIsNotHereIsRejected() {
    var agentClient =
        componentClient.forAutonomousAgent(TestAutonomousAgent.class, UUID.randomUUID().toString());
    agentClient.setup(AgentSetup.create());

    var acknowledgement =
        agentClient.sendMessage(
            AgentMessage.of("Use metric units.").taskId(UUID.randomUUID().toString()));

    assertThat(acknowledgement.messageId()).isNotEmpty();
    assertThat(acknowledgement.status()).isInstanceOf(MessageStatus.Rejected.class);
    assertThat(((MessageStatus.Rejected) acknowledgement.status()).reason()).isNotEmpty();
    assertThat(acknowledgement.duplicate()).isFalse();
  }

  @Test
  public void aMessageToAnInstanceThatDoesNotExistFails() {
    var agentClient =
        componentClient.forAutonomousAgent(TestAutonomousAgent.class, UUID.randomUUID().toString());

    assertThatThrownBy(
            () ->
                agentClient.sendMessage(
                    AgentMessage.of("Use metric units.").messageId(UUID.randomUUID().toString())))
        .isInstanceOf(AutonomousAgentSupport.InstanceNotFoundException.class)
        .hasMessageContaining("not found");
  }

  private List<Notification> subscribe(AutonomousAgentClient agentClient) {
    var notifications = new CopyOnWriteArrayList<Notification>();
    agentClient.notificationStream().runForeach(notifications::add, testKit.getMaterializer());
    return notifications;
  }

  private static <T extends Notification> List<T> ofType(
      List<Notification> notifications, Class<T> type) {
    return notifications.stream().filter(type::isInstance).map(type::cast).toList();
  }
}
