/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.SUBMIT_TURN;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.providePrompt;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.startScriptedConversation;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.submitTurn;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.ParticipantRef;
import akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.ScriptStep;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for scripted moderation tools: startScriptedConversation, providePrompt,
 * submitTurn, broadcast.
 */
public class ScriptedModerationIntegrationTest extends TestKitSupport {

  private final TestModelProvider moderatorModel =
      new TestModelProvider()
          .withMessageSelector(ScriptedModerationIntegrationTest::preferUserMessage);
  private final TestModelProvider debaterAModel =
      new TestModelProvider()
          .withMessageSelector(ScriptedModerationIntegrationTest::preferToolResult);
  private final TestModelProvider debaterBModel =
      new TestModelProvider()
          .withMessageSelector(ScriptedModerationIntegrationTest::preferToolResult);

  private static TestModelProvider.InputMessage preferToolResult(
      List<TestModelProvider.InputMessage> messages) {
    return messages.stream()
        .filter(m -> m instanceof TestModelProvider.ToolResult)
        .reduce((a, b) -> b)
        .orElse(messages.getLast());
  }

  private static TestModelProvider.InputMessage preferUserMessage(
      List<TestModelProvider.InputMessage> messages) {
    return messages.stream()
        .filter(m -> m instanceof TestModelProvider.UserMessage)
        .reduce((a, b) -> b)
        .orElse(messages.getLast());
  }

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(TestModerator.class, moderatorModel)
        .withModelProvider(DebaterA.class, debaterAModel)
        .withModelProvider(DebaterB.class, debaterBModel);
  }

  @AfterEach
  public void afterEach() {
    moderatorModel.reset();
    moderatorModel.withMessageSelector(ScriptedModerationIntegrationTest::preferUserMessage);
    debaterAModel.reset();
    debaterAModel.withMessageSelector(ScriptedModerationIntegrationTest::preferToolResult);
    debaterBModel.reset();
    debaterBModel.withMessageSelector(ScriptedModerationIntegrationTest::preferToolResult);
  }

  @Test
  public void shouldRunScriptedConversation() {
    // Moderator: responds to user messages based on content to drive scripted conversation.
    moderatorModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.UserMessage um) {
            var text = um.text();
            if (text.startsWith("Task:")) {
              // Task assigned — start scripted conversation
              return new AiResponse(
                  startScriptedConversation(
                      "Test topic",
                      List.of(new ParticipantRef("debater-a"), new ParticipantRef("debater-b")),
                      List.of(
                          new ScriptStep("debater-a", "Argue in favor"),
                          new ScriptStep("debater-b", "Argue against"),
                          new ScriptStep("moderator", "Synthesize"))));
            }
            if (text.contains("provide_prompt")) {
              return new AiResponse(providePrompt("Please present your perspective."));
            }
            if (text.contains("submit_turn")) {
              return new AiResponse(submitTurn("Both sides raise valid points."));
            }
            if (text.contains("Moderation capability")) {
              // Conversation completed
              return new AiResponse(
                  completeTask(
                      new TestTasks.ModerationResult(
                          "Test topic", "Balanced conclusion reached.")));
            }
            return new AiResponse("Acknowledged.");
          }
          return new AiResponse("Acknowledged.");
        });

    // Participants: submit turn whenever given the floor
    debaterAModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.ToolResult toolResult
              && toolResult.name().equals(SUBMIT_TURN)) {
            return new AiResponse("Turn submitted.");
          }
          return new AiResponse(submitTurn("I argue in favor of the topic."));
        });

    debaterBModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.ToolResult toolResult
              && toolResult.name().equals(SUBMIT_TURN)) {
            return new AiResponse("Turn submitted.");
          }
          return new AiResponse(submitTurn("I argue against the topic."));
        });

    var taskId =
        componentClient
            .forAutonomousAgent(TestModerator.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.MODERATE.instructions("Moderate a debate on the test topic."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.MODERATE);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().topic()).isEqualTo("Test topic");
              assertThat(snapshot.result().conclusion()).contains("Balanced conclusion");
            });
  }
}
