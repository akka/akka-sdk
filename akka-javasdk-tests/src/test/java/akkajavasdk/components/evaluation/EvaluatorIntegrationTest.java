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
 * Integration test of the full stateless-evaluator flow: an agent interaction (agent model stubbed
 * with {@link TestModelProvider}) fires a trigger for the bound {@link ResponseQualityEvaluator},
 * which fetches the interaction from the ledger and judges it with an LLM-as-judge agent (also
 * stubbed) in a single handler.
 *
 * <p>The counterpart of {@link WorkflowEvaluatorIntegrationTest} for an evaluator that is not a
 * workflow: here the runtime records the outcome the handler returns, rather than the evaluator
 * reporting it from a built-in record step. Each outcome is asserted on the evaluation as recorded
 * in the ledger.
 */
@ExtendWith(Junit5LogCapturing.class)
public class EvaluatorIntegrationTest extends TestKitSupport {

  private final TestModelProvider agentModel = new TestModelProvider();
  private final TestModelProvider judgeModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(StatelessEvaluatedAgent.class, agentModel)
        .withModelProvider(QualityJudge.class, judgeModel)
        .withAdditionalConfig(
            """
            akka.javasdk.evaluation.evaluators.response-quality-evaluator {
              agents {
                stateless-evaluated-agent { trigger = interaction }
              }
            }
            """);
  }

  @BeforeEach
  public void beforeEach() {
    ResponseQualityEvaluator.clearEvaluationIds();
  }

  @AfterEach
  public void afterEach() {
    agentModel.reset();
    judgeModel.reset();
  }

  @Test
  public void runsEvaluationForBoundAgentInteraction() {
    agentModel.fixedResponse("You can reset your password under account settings.");
    judgeModel.fixedResponse(
        """
        { "passed": true, "score": 0.9, "reason": "clear and helpful" }
        """
            .stripIndent());

    EvaluationRecord record = evaluationFor("How do I reset my password?");

    assertThat(record.evaluatorComponentId()).isEqualTo("response-quality-evaluator");
    assertThat(record.agentComponentId()).isEqualTo("stateless-evaluated-agent");
    assertThat(record.interactionId()).isNotBlank();
    assertThat(record.trigger()).isEqualTo(EvaluationRecord.Trigger.ON_INTERACTION);
    assertThat(record.outcome()).isInstanceOf(EvaluationRecord.Outcome.Verdict.class);

    var evaluation = record.evaluation().orElseThrow();
    assertThat(evaluation.passed()).isTrue();
    assertThat(evaluation.score()).hasValue(0.9);
    assertThat(evaluation.explanation()).isEqualTo("clear and helpful");
    assertThat(evaluation.attributes())
        .containsEntry("finalText", "You can reset your password under account settings.");
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

    var evaluation = record.evaluation().orElseThrow();
    assertThat(evaluation.passed()).isFalse();
    assertThat(evaluation.explanation()).isEqualTo("dismissive and unhelpful");
  }

  /** An evaluator that ran but reached no verdict reports it, and the reason is recorded. */
  @Test
  public void recordsInconclusiveOutcome() {
    agentModel.fixedResponse("Have you tried turning it off and on again?");
    judgeModel.fixedResponse(
        """
        { "passed": true, "score": 0.5, "reason": "" }
        """
            .stripIndent());

    EvaluationRecord record = evaluationFor("My laptop is slow.");

    assertThat(record.outcome())
        .isEqualTo(
            new EvaluationRecord.Outcome.Inconclusive("judge returned no reason for its verdict"));
    assertThat(record.evaluation()).isEmpty();
  }

  /**
   * A failing evaluation is a recorded outcome, not a lost one: the judge call blows up, and the
   * runtime records the failure so the trigger is not retried forever.
   */
  @Test
  public void recordsFailedOutcomeWhenTheJudgeFails() {
    agentModel.fixedResponse("Try restarting the service.");
    judgeModel.whenMessage(msg -> true).failWith(new RuntimeException("judge model exploded"));

    EvaluationRecord record = evaluationFor("Why is my service down?");

    assertThat(record.outcome()).isInstanceOf(EvaluationRecord.Outcome.Failed.class);
    assertThat(record.evaluation()).isEmpty();
  }

  /**
   * Ask the evaluated agent, then await the evaluation the interaction triggered, as recorded in
   * the ledger: first the id the evaluator ran with, then the record the runtime writes once the
   * evaluation terminates.
   */
  private EvaluationRecord evaluationFor(String question) {
    String answer =
        componentClient
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(StatelessEvaluatedAgent::ask)
            .invoke(question);
    assertThat(answer).isNotBlank();

    Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(ResponseQualityEvaluator.evaluationIds()).hasSize(1));

    String evaluationId = ResponseQualityEvaluator.evaluationIds().iterator().next();

    return Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
        .ignoreException(NoSuchElementException.class)
        .until(() -> getLedgerClient().getEvaluation(evaluationId), record -> record != null);
  }
}
