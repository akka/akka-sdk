/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.BROADCAST;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.COMPLETE_TASK;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.DIRECT;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.END_CONVERSATION;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.START_CONVERSATION;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.SUBMIT_TURN;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.broadcast;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.direct;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.endConversation;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.startDirectedConversation;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.submitTurn;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.ParticipantRef;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for directed moderation tools: startDirectedConversation, direct,
 * endConversation, submitTurn.
 */
public class DirectedModerationIntegrationTest extends TestKitSupport {

  private final TestModelProvider moderatorModel =
      new TestModelProvider()
          .withMessageSelector(DirectedModerationIntegrationTest::preferToolResult);
  private final TestModelProvider debaterAModel =
      new TestModelProvider()
          .withMessageSelector(DirectedModerationIntegrationTest::preferToolResult);
  private final TestModelProvider debaterBModel =
      new TestModelProvider()
          .withMessageSelector(DirectedModerationIntegrationTest::preferToolResult);

  private static TestModelProvider.InputMessage preferToolResult(
      List<TestModelProvider.InputMessage> messages) {
    return messages.stream()
        .filter(m -> m instanceof TestModelProvider.ToolResult)
        .reduce((a, b) -> b)
        .orElse(messages.getLast());
  }

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(DirectedModerator.class, moderatorModel)
        .withModelProvider(DebaterA.class, debaterAModel)
        .withModelProvider(DebaterB.class, debaterBModel);
  }

  @AfterEach
  public void afterEach() {
    moderatorModel.reset();
    moderatorModel.withMessageSelector(DirectedModerationIntegrationTest::preferToolResult);
    debaterAModel.reset();
    debaterAModel.withMessageSelector(DirectedModerationIntegrationTest::preferToolResult);
    debaterBModel.reset();
    debaterBModel.withMessageSelector(DirectedModerationIntegrationTest::preferToolResult);
  }

  @Test
  public void shouldRunDirectedConversation() {
    // Moderator: drive directed conversation through tool result responses.
    moderatorModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.UserMessage) {
            // Task assigned — start directed conversation
            return new AiResponse(
                startDirectedConversation(
                    "Directed debate",
                    3,
                    List.of(new ParticipantRef("debater-a"), new ParticipantRef("debater-b"))));
          }
          if (msg instanceof TestModelProvider.ToolResult toolResult) {
            return switch (toolResult.name()) {
              case START_CONVERSATION ->
                  new AiResponse(direct("debater-a", "Make your opening argument."));
              case DIRECT -> {
                if (toolResult.content().contains("[debater-a]")) {
                  // Broadcast context before directing the second participant
                  yield new AiResponse(broadcast("Consider both perspectives carefully."));
                }
                // debater-b responded — end conversation
                yield new AiResponse(endConversation());
              }
              case BROADCAST ->
                  new AiResponse(direct("debater-b", "Counter the opening argument."));
              case END_CONVERSATION ->
                  new AiResponse(
                      completeTask(
                          "{\"topic\":\"Directed debate\",\"conclusion\":\"Agreement reached.\"}"));
              case COMPLETE_TASK -> new AiResponse("Done.");
              default -> new AiResponse("Acknowledged.");
            };
          }
          return new AiResponse("Acknowledged.");
        });

    // Participants: submit turn whenever given the floor
    debaterAModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.ToolResult tr && tr.name().equals(SUBMIT_TURN)) {
            return new AiResponse("Turn submitted.");
          }
          return new AiResponse(submitTurn("My opening argument in favor."));
        });

    debaterBModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.ToolResult tr && tr.name().equals(SUBMIT_TURN)) {
            return new AiResponse("Turn submitted.");
          }
          return new AiResponse(submitTurn("My counter-argument against."));
        });

    var taskId =
        componentClient
            .forAutonomousAgent(DirectedModerator.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.MODERATE.instructions("Run a directed debate on the topic."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.MODERATE);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().topic()).isEqualTo("Directed debate");
              assertThat(snapshot.result().conclusion()).contains("Agreement reached");
            });
  }
}
