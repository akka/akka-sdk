/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration test of the full durable-evaluator flow: an agent interaction (agent model stubbed
 * with {@link TestModelProvider}) fires a trigger for the bound {@link
 * ResponseQualityDurableEvaluator}, which runs its evaluation in durable steps — fetching the
 * transcript in one step and judging it with an LLM-as-judge agent (also stubbed) in another —
 * until the verdict is recorded.
 *
 * <p>The verdict is asserted on the evaluation as recorded in the ledger, so the built-in record
 * step is covered too: the record only exists once that step has reported the outcome to the
 * runtime.
 */
@ExtendWith(Junit5LogCapturing.class)
public class DurableEvaluatorIntegrationTest extends TestKitSupport {

  private final TestModelProvider agentModel = new TestModelProvider();
  private final TestModelProvider judgeModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(EvaluatedAgent.class, agentModel)
        .withModelProvider(QualityJudge.class, judgeModel)
        .withAdditionalConfig(
            """
            akka.javasdk.evaluation.evaluators.response-quality-durable-evaluator {
              agents {
                wf-evaluated-agent { trigger = interaction }
              }
            }
            """);
  }

  @AfterEach
  public void afterEach() {
    agentModel.reset();
    judgeModel.reset();
  }

  @Test
  public void runsMultiStepEvaluationForBoundAgentInteraction() {
    agentModel.fixedResponse("You can reset your password under account settings.");
    judgeModel.fixedResponse(
        """
        { "passed": true, "score": 0.9, "reason": "clear and helpful" }
        """
            .stripIndent());

    EvaluationRecord record = evaluationFor("How do I reset my password?");

    assertThat(record.evaluatorComponentId()).isEqualTo("response-quality-durable-evaluator");
    assertThat(record.agentComponentId()).isEqualTo("wf-evaluated-agent");
    assertThat(record.interactionId()).isNotBlank();
    assertThat(record.trigger()).isEqualTo(EvaluationRecord.Trigger.ON_INTERACTION);
    assertThat(record.outcome()).isInstanceOf(EvaluationRecord.Outcome.Verdict.class);

    assertThat(record.evaluations()).hasSize(1);
    var evaluation = record.evaluations().getFirst();
    assertThat(evaluation.passed()).isTrue();
    assertThat(evaluation.score()).hasValue(0.9);
    assertThat(evaluation.explanation()).isEqualTo("clear and helpful");
  }

  @Test
  public void recordsFailingVerdictFromTheJudge() {
    agentModel.fixedResponse("I don't know, figure it out yourself.");
    judgeModel.fixedResponse(
        """
        { "passed": false, "score": 0.1, "reason": "dismissive and unhelpful" }
        """
            .stripIndent());

    EvaluationRecord record = evaluationFor("How do I export my data?");

    assertThat(record.evaluations()).hasSize(1);
    var evaluation = record.evaluations().get(0);
    assertThat(evaluation.passed()).isFalse();
    assertThat(evaluation.explanation()).isEqualTo("dismissive and unhelpful");
  }

  /**
   * Ask the evaluated agent, then await the evaluation the interaction triggered, as recorded in
   * the ledger by the built-in record step when the evaluation terminates. The reply carries the id
   * of the interaction, which is what the evaluations are recorded against.
   */
  private EvaluationRecord evaluationFor(String question) {
    var reply =
        componentClient
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(EvaluatedAgent::ask)
            .withDetailedReply()
            .invoke(question);
    assertThat(reply.value()).isNotBlank();

    String interactionId = reply.interactionId().orElseThrow();

    List<EvaluationRecord> records =
        Awaitility.await()
            .atMost(30, TimeUnit.SECONDS)
            .until(
                () -> getLedgerClient().getEvaluations(interactionId), found -> !found.isEmpty());

    assertThat(records).hasSize(1);
    assertThat(records.getFirst().interactionId()).isEqualTo(interactionId);
    return records.getFirst();
  }
}
