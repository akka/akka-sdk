package demo.peerreview;

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
import demo.peerreview.api.PeerReviewEndpoint;
import demo.peerreview.application.ComplianceReviewer;
import demo.peerreview.application.ReviewModerator;
import demo.peerreview.application.ReviewTasks;
import demo.peerreview.application.ReviewTasks.ReviewResult;
import demo.peerreview.application.StyleReviewer;
import demo.peerreview.application.TechnicalReviewer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class PeerReviewIntegrationTest extends TestKitSupport {

  private final TestModelProvider moderatorModel = new TestModelProvider()
    .withMessageSelector(PeerReviewIntegrationTest::preferUserMessage);
  private final TestModelProvider technicalModel = new TestModelProvider()
    .withMessageSelector(PeerReviewIntegrationTest::preferToolResult);
  private final TestModelProvider styleModel = new TestModelProvider()
    .withMessageSelector(PeerReviewIntegrationTest::preferToolResult);
  private final TestModelProvider complianceModel = new TestModelProvider()
    .withMessageSelector(PeerReviewIntegrationTest::preferToolResult);

  private static TestModelProvider.InputMessage preferToolResult(
    List<TestModelProvider.InputMessage> messages
  ) {
    return messages
      .stream()
      .filter(m -> m instanceof TestModelProvider.ToolResult)
      .reduce((a, b) -> b)
      .orElse(messages.getLast());
  }

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
      .withModelProvider(ReviewModerator.class, moderatorModel)
      .withModelProvider(TechnicalReviewer.class, technicalModel)
      .withModelProvider(StyleReviewer.class, styleModel)
      .withModelProvider(ComplianceReviewer.class, complianceModel);
  }

  @Test
  public void shouldRunScriptedPeerReview() {
    // Moderator: respond to user messages based on content.
    // Uses preferUserMessage so scripted step instructions drive the flow.
    moderatorModel.fixedResponse(msg -> {
      if (msg instanceof TestModelProvider.UserMessage um) {
        var text = um.text();
        if (text.startsWith("Task:")) {
          return new AiResponse(
            startScriptedConversation(
              "API design document review",
              List.of(
                new ParticipantRef("technical-reviewer"),
                new ParticipantRef("style-reviewer"),
                new ParticipantRef("compliance-reviewer")
              ),
              List.of(
                new ScriptStep("technical-reviewer", "Review for technical accuracy"),
                new ScriptStep("style-reviewer", "Review for clarity and style"),
                new ScriptStep("compliance-reviewer", "Review for compliance"),
                new ScriptStep("moderator", "Synthesize all reviews")
              )
            )
          );
        }
        if (text.contains("provide_prompt")) {
          return new AiResponse(
            providePrompt("Please review this document from your area of expertise.")
          );
        }
        if (text.contains("submit_turn")) {
          return new AiResponse(
            submitTurn(
              "Based on all reviews, the document is approved with minor revisions needed."
            )
          );
        }
        if (text.contains("Moderation capability")) {
          // Conversation completed — moderator sees available participants again
          return new AiResponse(
            completeTask(
              new ReviewResult(
                "API design document",
                "Approved with minor revisions.",
                List.of(
                  "Technical: API contracts are well-defined",
                  "Style: Clear and readable",
                  "Compliance: Meets data handling requirements"
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
    technicalModel.fixedResponse(msg -> {
      if (msg instanceof TestModelProvider.ToolResult tr && tr.name().equals(SUBMIT_TURN)) {
        return new AiResponse("Turn submitted.");
      }
      return new AiResponse(submitTurn("API contracts are well-defined and consistent."));
    });

    styleModel.fixedResponse(msg -> {
      if (msg instanceof TestModelProvider.ToolResult tr && tr.name().equals(SUBMIT_TURN)) {
        return new AiResponse("Turn submitted.");
      }
      return new AiResponse(
        submitTurn("Document is clear and readable with good structure.")
      );
    });

    complianceModel.fixedResponse(msg -> {
      if (msg instanceof TestModelProvider.ToolResult tr && tr.name().equals(SUBMIT_TURN)) {
        return new AiResponse("Turn submitted.");
      }
      return new AiResponse(submitTurn("Meets data handling and privacy requirements."));
    });

    var response = httpClient
      .POST("/peerreview")
      .withRequestBody(new PeerReviewEndpoint.ReviewRequest("Review the API design document"))
      .responseBodyAs(PeerReviewEndpoint.ReviewResponse.class)
      .invoke()
      .body();

    var taskId = response.taskId();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(ReviewTasks.REVIEW);
        var result = snapshot.result().orElseThrow();
        assertThat(result.document()).isEqualTo("API design document");
        assertThat(result.assessment()).contains("Approved");
        assertThat(result.reviewerFindings()).hasSize(3);
      });
  }
}
