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
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration test of the full workflow-evaluator flow: an agent interaction (agent model stubbed
 * with {@link TestModelProvider}) fires a trigger for the bound {@link
 * ResponseQualityWorkflowEvaluator}, which runs its evaluation as a workflow — fetching the
 * transcript in one step and judging it with an LLM-as-judge agent (also stubbed) in another —
 * until the verdict is recorded.
 *
 * <p>The verdict is asserted on the evaluation as recorded in the ledger, so the built-in record
 * step is covered too: the record only exists once that step has reported the outcome to the
 * runtime.
 */
@ExtendWith(Junit5LogCapturing.class)
public class WorkflowEvaluatorIntegrationTest extends TestKitSupport {

  private final TestModelProvider agentModel = new TestModelProvider();
  private final TestModelProvider judgeModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(EvaluatedAgent.class, agentModel)
        .withModelProvider(QualityJudge.class, judgeModel)
        .withAdditionalConfig(
            """
            akka.javasdk.evaluation.evaluators.response-quality-workflow-evaluator {
              agents {
                wf-evaluated-agent { trigger = interaction }
              }
            }
            """);
  }

  @BeforeEach
  public void beforeEach() {
    ResponseQualityWorkflowEvaluator.clearEvaluationIds();
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

    String answer =
        componentClient
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(EvaluatedAgent::ask)
            .invoke("How do I reset my password?");
    assertThat(answer).isNotBlank();

    EvaluationRecord record = awaitRecordedEvaluation();

    assertThat(record.evaluatorComponentId()).isEqualTo("response-quality-workflow-evaluator");
    assertThat(record.agentComponentId()).isEqualTo("wf-evaluated-agent");
    assertThat(record.interactionId()).isNotBlank();
    assertThat(record.trigger()).isEqualTo(EvaluationRecord.Trigger.ON_INTERACTION);
    assertThat(record.outcome()).isInstanceOf(EvaluationRecord.Outcome.Verdict.class);

    var evaluation = record.evaluation().orElseThrow();
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

    String answer =
        componentClient
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(EvaluatedAgent::ask)
            .invoke("How do I export my data?");
    assertThat(answer).isNotBlank();

    EvaluationRecord record = awaitRecordedEvaluation();

    var evaluation = record.evaluation().orElseThrow();
    assertThat(evaluation.passed()).isFalse();
    assertThat(evaluation.explanation()).isEqualTo("dismissive and unhelpful");
  }

  /**
   * Await the evaluation the agent interaction triggered, as recorded in the ledger: first the id
   * the evaluator ran with, then the record the built-in record step writes when the evaluation
   * terminates.
   */
  private EvaluationRecord awaitRecordedEvaluation() {
    Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(ResponseQualityWorkflowEvaluator.evaluationIds()).hasSize(1));

    String evaluationId = ResponseQualityWorkflowEvaluator.evaluationIds().iterator().next();

    return Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
        .ignoreException(NoSuchElementException.class)
        .until(() -> getLedgerClient().getEvaluation(evaluationId), record -> record != null);
  }
}
