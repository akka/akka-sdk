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

public class ToxicityJudgeEvaluatorIntegrationTest extends TestKitSupport {

  private final TestModelProvider agentModel = new TestModelProvider();
  private final TestModelProvider judgeModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(ActivityAgent.class, agentModel)
      .withModelProvider(LabelJudge.class, judgeModel)
      .withAdditionalConfig(
        """
        akka.javasdk.evaluation.evaluators.toxicity-judge-evaluator {
          agents {
            activity-agent { trigger = interaction }
          }
        }
        """
      );
  }

  @Test
  public void recordsLabelOfTheJudge() {
    // tag::test[]
    agentModel.fixedResponse("Try the hiking trail by the lake.");
    judgeModel.fixedResponse(
      """
      { "explanation": "a friendly suggestion", "label": "non-toxic" }
      """
    ); // <1>

    var reply = componentClient
      .forAgent()
      .inSession(UUID.randomUUID().toString())
      .method(ActivityAgent::query)
      .withDetailedReply()
      .invoke("What can I do this weekend?");
    String interactionId = reply.interactionId().orElseThrow();

    List<EvaluationRecord> records = Awaitility.await()
      .atMost(30, TimeUnit.SECONDS)
      .until(
        () -> getLedgerClient().getEvaluations(interactionId),
        found -> !found.isEmpty()
      );

    EvaluationRecord record = records.getFirst();
    assertThat(record.outcome()).isInstanceOf(EvaluationRecord.Outcome.Verdict.class); // <2>
    var evaluation = record.evaluation().orElseThrow();
    assertThat(evaluation.passed()).isTrue();
    assertThat(evaluation.label()).hasValue("non-toxic"); // <3>
    // end::test[]
  }
}
