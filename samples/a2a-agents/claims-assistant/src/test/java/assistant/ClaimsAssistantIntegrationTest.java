package assistant;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.MockA2aAgent;
import akka.javasdk.testkit.MockA2aAgent.Reply;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import assistant.api.ClaimsAssistantEndpoint;
import assistant.application.ClaimReviewRequestWorkflow;
import assistant.application.ClaimsAssistantAgent;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ClaimsAssistantIntegrationTest extends TestKitSupport {

  private static final MockA2aAgent claims = MockA2aAgent.create("claims")
    .withSkill(
      "claim-status",
      "Tells the status of a claim",
      Reply.text("Claim 42 is approved, payment on its way")
    )
    .withSkill(
      "review-claim",
      "Reviews a claim",
      Reply.workingThenCompleted(Duration.ofSeconds(1), "approved")
    )
    .start();

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(ClaimsAssistantAgent.class, model)
      .withMockedA2aAgent("claims", claims)
      .withAdditionalConfig(
        """
        akka.javasdk.agent.openai.api-key = n/a
        akka.javasdk.a2a.remote-agents.claims.poll-interval = 500ms
        akka.javasdk.a2a.remote-agents.claims.card-url = "%s"
        """.formatted(claims.cardUrl())
      );
  }

  @AfterEach
  public void reset() {
    claims.reset();
  }

  @AfterAll
  public static void stopMock() {
    claims.close();
  }

  @Test
  public void callsTheClaimStatusSkillAsATool() {
    model
      .whenMessage(message -> message.contains("claim 42"))
      .reply(
        new ToolInvocationRequest(
          "claims_claim-status",
          "{\"message\":\"What is the status of claim 42?\"}"
        )
      );
    model
      .whenToolResult(result -> result.name().equals("claims_claim-status"))
      .thenReply(result -> new AiResponse("The claims agent says: " + result.content()));

    var reply = httpClient
      .POST("/assistant")
      .withRequestBody(new ClaimsAssistantEndpoint.Question("Is claim 42 approved?"))
      .responseBodyAs(String.class)
      .invoke()
      .body();

    assertThat(reply).isEqualTo(
      "The claims agent says: Claim 42 is approved, payment on its way"
    );
    assertThat(claims.received())
      .singleElement()
      .satisfies(received -> {
        assertThat(received.skillId()).isEqualTo("claim-status");
      });
  }

  @Test
  public void followsTheReviewTaskFromAWorkflowStep() {
    var claimId = UUID.randomUUID().toString();

    httpClient
      .POST("/reviews/" + claimId)
      .withRequestBody(new ClaimsAssistantEndpoint.ReviewRequest("P-1234"))
      .invoke();

    Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var review = componentClient
          .forWorkflow(claimId)
          .method(ClaimReviewRequestWorkflow::get)
          .invoke();
        assertThat(review.outcome()).isEqualTo("reviewed");
        assertThat(review.review()).contains("approved");
      });
  }
}
