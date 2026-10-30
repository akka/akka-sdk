/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.evaluation.Subject;
import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.Junit5LogCapturing;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Full-integration test of an evaluation started on request: {@link EvaluatedWorkflow} starts an
 * evaluation of its own run through the component client, the runtime queues the trigger and runs
 * {@link WorkflowRunEvaluator}, which is bound to no agent, and the ledger records the verdict
 * under the workflow run.
 */
@ExtendWith(Junit5LogCapturing.class)
public class WorkflowEvaluationIntegrationTest extends TestKitSupport {

  @Test
  public void workflowStartsAnEvaluationOfItsRun() {
    var workflowId = UUID.randomUUID().toString();
    componentClient.forWorkflow(workflowId).method(EvaluatedWorkflow::run).invoke("What is 2+2?");

    var state =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .until(
                () ->
                    componentClient.forWorkflow(workflowId).method(EvaluatedWorkflow::get).invoke(),
                found -> found.evaluationId() != null);

    List<EvaluationRecord> evaluations =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .until(
                () -> getLedgerClient().getWorkflowEvaluations("evaluated-workflow", workflowId),
                found -> !found.isEmpty());

    assertThat(evaluations).hasSize(1);
    var record = evaluations.getFirst();
    assertThat(record.evaluationId()).isEqualTo(state.evaluationId());
    assertThat(record.evaluatorComponentId()).isEqualTo("workflow-run-evaluator");
    assertThat(record.trigger()).isEqualTo(EvaluationRecord.Trigger.MANUAL);
    assertThat(record.subject())
        .isEqualTo(new EvaluationRecord.Subject.Workflow(workflowId, "evaluated-workflow"));
    assertThat(record.attributes()).isEqualTo(Map.of("question", "What is 2+2?"));
    var evaluation = record.evaluation().orElseThrow();
    assertThat(evaluation.explanation()).isEqualTo("run " + workflowId + " answered What is 2+2?");
    assertThat(evaluation.attributes()).isEqualTo(Map.of("question", "What is 2+2?"));

    assertThat(getLedgerClient().getEvaluation(state.evaluationId()).subject())
        .isEqualTo(record.subject());
  }

  @Test
  public void startingTheSameEvaluationAgainReturnsTheSameId() {
    var run = new Subject.Workflow(UUID.randomUUID().toString(), "evaluated-workflow");
    var client = componentClient.forEvaluator("workflow-run-evaluator");

    var first = client.startEvaluation(run, Map.of("question", "again"));
    var second = client.startEvaluation(run, Map.of("question", "again"));
    var other = client.startEvaluation(run, Map.of("question", "other"));

    assertThat(second).isEqualTo(first);
    assertThat(other).isNotEqualTo(first);
  }

  @Test
  public void rejectsAnUnknownEvaluator() {
    var run = new Subject.Workflow(UUID.randomUUID().toString(), "evaluated-workflow");

    assertThatThrownBy(
            () -> componentClient.forEvaluator("no-such-evaluator").startEvaluation(run, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no-such-evaluator");
  }
}
