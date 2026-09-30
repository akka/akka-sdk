package com.example.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.application.ActivityAgent;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class TranscriptJudgeEvaluatorIntegrationTest extends TestKitSupport {

  // tag::settings[]
  private final TestModelProvider agentModel = new TestModelProvider();
  private final TestModelProvider judgeModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(ActivityAgent.class, agentModel)
      .withModelProvider(QualityJudge.class, judgeModel)
      .withAdditionalConfig(
        """
        akka.javasdk.evaluation.evaluators.transcript-judge-durable-evaluator {
          agents {
            activity-agent { trigger = interaction }
          }
        }
        """
      ); // <1>
  }

  // end::settings[]

  @Test
  public void recordsVerdictForBoundAgentInteraction() {
    // tag::test[]
    agentModel.fixedResponse("Try the hiking trail by the lake.");
    judgeModel.fixedResponse(
      """
      { "passed": true, "score": 0.9, "reason": "relevant and specific" }
      """
    );

    var reply = componentClient
      .forAgent()
      .inSession(UUID.randomUUID().toString())
      .method(ActivityAgent::query)
      .withDetailedReply()
      .invoke("What can I do this weekend?"); // <1>
    String interactionId = reply.interactionId().orElseThrow();

    List<EvaluationRecord> records = Awaitility.await()
      .atMost(30, TimeUnit.SECONDS)
      .until(
        () -> getLedgerClient().getEvaluations(interactionId),
        found -> !found.isEmpty()
      ); // <2>

    var evaluation = records.getFirst().evaluation().orElseThrow(); // <3>
    assertThat(evaluation.passed()).isTrue();
    assertThat(evaluation.score()).hasValue(0.9);
    // end::test[]
  }
}
