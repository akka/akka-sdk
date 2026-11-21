package assistant;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.MockA2aAgent;
import akka.javasdk.testkit.MockA2aAgent.Reply;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import assistant.api.ClaimsAssistantEndpoint;
import assistant.application.ClaimReviewRequestWorkflow;
import assistant.application.ClaimsAssistantAgent;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class ClaimReviewInputRequiredIntegrationTest extends TestKitSupport {

  private static final MockA2aAgent claims = MockA2aAgent.create("claims")
    .withSkill(
      "review-claim",
      "Reviews a claim",
      Reply.inputRequired("What is the policy number?", "approved")
    )
    .start();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(
      ClaimsAssistantAgent.class,
      new TestModelProvider()
    )
      .withMockedA2aAgent("claims", claims)
      .withAdditionalConfig(
        """
        akka.javasdk.agent.openai.api-key = n/a
        akka.javasdk.a2a.remote-agents.claims.poll-interval = 500ms
        akka.javasdk.a2a.remote-agents.claims.card-url = "%s"
        """.formatted(claims.cardUrl())
      );
  }

  @AfterAll
  public static void stopMock() {
    claims.close();
  }

  @Test
  public void answersTheQuestionOfTheRemoteTask() {
    var claimId = UUID.randomUUID().toString();

    httpClient
      .POST("/reviews/" + claimId)
      .withRequestBody(new ClaimsAssistantEndpoint.ReviewRequest(null))
      .invoke();

    var asked = awaitReview(claimId, review -> review.awaitingInput() != null);
    assertThat(asked.awaitingInput()).isEqualTo("What is the policy number?");
    assertThat(claims.received().getFirst().text()).isEqualTo("Review claim " + claimId);

    httpClient
      .POST("/reviews/" + claimId + "/answer")
      .withRequestBody(new ClaimsAssistantEndpoint.Answer("P-1234"))
      .invoke();

    var done = awaitReview(claimId, review -> review.outcome() != null);
    assertThat(done.outcome()).isEqualTo("reviewed");
    assertThat(done.review()).contains("approved");
    assertThat(done.awaitingInput()).isNull();
    assertThat(claims.received().getLast().taskId()).isEqualTo(asked.remoteTaskId());
    assertThat(claims.received().getLast().contextId()).isEqualTo(asked.remoteContextId());
    assertThat(claims.received().getLast().text()).isEqualTo("P-1234");
  }

  private ClaimReviewRequestWorkflow.State awaitReview(
    String claimId,
    java.util.function.Predicate<ClaimReviewRequestWorkflow.State> condition
  ) {
    return Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .until(
        () ->
          componentClient
            .forWorkflow(claimId)
            .method(ClaimReviewRequestWorkflow::get)
            .invoke(),
        condition::test
      );
  }
}
