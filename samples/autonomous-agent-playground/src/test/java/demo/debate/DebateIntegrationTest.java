package demo.debate;

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
import demo.debate.api.DebateEndpoint;
import demo.debate.application.Advocate;
import demo.debate.application.Critic;
import demo.debate.application.DebateModerator;
import demo.debate.application.DebateTasks;
import demo.debate.application.DebateTasks.DebateResult;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class DebateIntegrationTest extends TestKitSupport {

  private final TestModelProvider moderatorModel = new TestModelProvider()
    .withMessageSelector(DebateIntegrationTest::preferUserMessage);
  private final TestModelProvider advocateModel = new TestModelProvider()
    .withMessageSelector(DebateIntegrationTest::preferToolResult);
  private final TestModelProvider criticModel = new TestModelProvider()
    .withMessageSelector(DebateIntegrationTest::preferToolResult);

  /** Select the last tool result if present, otherwise the last message. */
  private static TestModelProvider.InputMessage preferToolResult(
    List<TestModelProvider.InputMessage> messages
  ) {
    return messages
      .stream()
      .filter(m -> m instanceof TestModelProvider.ToolResult)
      .reduce((a, b) -> b)
      .orElse(messages.getLast());
  }

  /** Select the last user message if present, otherwise the last message. */
  private static TestModelProvider.InputMessage preferUserMessage(
    List<TestModelProvider.InputMessage> messages
  ) {
    return messages
      .stream()
      .filter(m -> m instanceof TestModelProvider.UserMessage)
      .reduce((a, b) -> b)
      .orElse(messages.getLast());
  }

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(DebateModerator.class, moderatorModel)
      .withModelProvider(Advocate.class, advocateModel)
      .withModelProvider(Critic.class, criticModel);
  }

  @Test
  public void shouldRunScriptedDebate() {
    // Moderator: respond to user messages based on content.
    // Uses preferUserMessage so scripted step instructions drive the flow.
    moderatorModel.fixedResponse(msg -> {
      if (msg instanceof TestModelProvider.UserMessage um) {
        var text = um.text();
        if (text.startsWith("Task:")) {
          return new AiResponse(
            startScriptedConversation(
              "AI in education",
              List.of(new ParticipantRef("advocate"), new ParticipantRef("critic")),
              List.of(
                new ScriptStep("advocate", "Argue in favor of AI in education"),
                new ScriptStep("critic", "Argue against AI in education"),
                new ScriptStep("moderator", "Synthesize both perspectives")
              )
            )
          );
        }
        if (text.contains("provide_prompt")) {
          return new AiResponse(
            providePrompt("Please present your perspective on AI in education.")
          );
        }
        if (text.contains("submit_turn")) {
          return new AiResponse(
            submitTurn("Both sides raise valid points about AI in education.")
          );
        }
        if (text.contains("Moderation capability")) {
          // Conversation completed — moderator sees available participants again
          return new AiResponse(
            completeTask(
              new DebateResult(
                "AI in education",
                "Both sides presented valid points about AI in education.",
                List.of(
                  "AI enhances personalized learning",
                  "AI risks reducing critical thinking"
                )
              )
            )
          );
        }
        return new AiResponse("Acknowledged.");
      }
      return new AiResponse("Acknowledged.");
    });

    // Participants: submit turn whenever given the floor
    advocateModel.fixedResponse(msg -> {
      if (
        msg instanceof TestModelProvider.ToolResult toolResult &&
        toolResult.name().equals(SUBMIT_TURN)
      ) {
        return new AiResponse("Turn submitted.");
      }
      return new AiResponse(
        submitTurn("AI enhances personalized learning for every student.")
      );
    });

    criticModel.fixedResponse(msg -> {
      if (
        msg instanceof TestModelProvider.ToolResult toolResult &&
        toolResult.name().equals(SUBMIT_TURN)
      ) {
        return new AiResponse("Turn submitted.");
      }
      return new AiResponse(
        submitTurn("Over-reliance on AI risks reducing critical thinking skills.")
      );
    });

    var response = httpClient
      .POST("/debate")
      .withRequestBody(new DebateEndpoint.DebateRequest("Should AI be used in education?"))
      .responseBodyAs(DebateEndpoint.DebateResponse.class)
      .invoke()
      .body();

    var taskId = response.taskId();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(DebateTasks.DEBATE);
        var result = snapshot.result().orElseThrow();
        assertThat(result.topic()).isEqualTo("AI in education");
        assertThat(result.synthesis()).contains("Both sides");
        assertThat(result.keyArguments()).hasSize(2);
      });
  }
}
