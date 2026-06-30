/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.evaluation.Subject;
import akka.javasdk.testkit.EvaluatorResult;
import akka.javasdk.testkit.EvaluatorTestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.Junit5LogCapturing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration test: an Evaluator delegates a multi-step evaluation to a durable workflow through a
 * real component client and suspends (asyncEffect) until the workflow completes.
 */
@ExtendWith(Junit5LogCapturing.class)
public class WorkflowEvaluatorTest extends TestKitSupport {

  @Test
  public void recordsWorkflowResult() {
    EvaluatorTestKit<WorkflowEvaluator> evaluatorTestKit =
        EvaluatorTestKit.of(
            () -> new WorkflowEvaluator(componentClient, testKit.getMaterializer()));

    // the evaluator builds the transcript "interaction-7" (13 chars), which the workflow scores
    Subject subject = new Subject.AgentInteraction("support-agent", "session-1", "writer-1", 7);

    EvaluatorResult result = evaluatorTestKit.evaluate(subject, "eval-workflow-1");

    assertThat(result.isAsync()).isTrue();
    assertThat(result.isRecord()).isTrue();
    assertThat(result.getEvaluations()).hasSize(1);
    var evaluation = result.getEvaluations().get(0);
    assertThat(evaluation.passed()).isTrue(); // workflow completed
    assertThat(evaluation.score().orElseThrow()).isEqualTo(13.0);
    assertThat(evaluation.explanation()).isEqualTo("scored 13");
  }
}
