/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.testkit.EvaluatorResult;
import akka.javasdk.testkit.EvaluatorTestKit;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class SimpleEvaluatorTest {

  private final EvaluatorTestKit<SimpleEvaluator> testKit =
      EvaluatorTestKit.of(SimpleEvaluator::new);

  private Subject agentInteraction(long sequenceNr) {
    return new Subject.AgentInteraction("support-agent", "session-1", "writer-1", sequenceNr);
  }

  @Test
  public void recordsEvaluation() {
    EvaluatorResult result = testKit.evaluate(agentInteraction(5), "eval-42");

    assertTrue(result.isRecord());
    assertFalse(result.isError());
    assertFalse(result.isAsync());

    assertEquals(1, result.getEvaluations().size());
    Evaluation evaluation = result.getEvaluations().get(0);
    assertTrue(evaluation.passed());
    assertEquals(0.9, evaluation.score().orElseThrow());
    assertEquals("good", evaluation.label().orElseThrow());
    assertEquals("support-agent", evaluation.attributes().get("agent"));
    // the judge session id is derived from the evaluation id, never the bare id
    assertTrue(evaluation.explanation().contains("eval-42-judge-default"));
  }

  @Test
  public void reportsError() {
    EvaluatorResult result = testKit.evaluate(agentInteraction(-1));

    assertTrue(result.isError());
    assertFalse(result.isRecord());
    assertEquals("cannot evaluate interaction -1", result.getError());
  }

  @Test
  public void resolvesAsyncEffect() {
    EvaluatorResult result = testKit.evaluate(agentInteraction(0));

    assertTrue(result.isAsync());
    // async effect resolves to the terminal record
    assertTrue(result.isRecord());
    assertEquals(1, result.getEvaluations().size());
    assertEquals("async verdict", result.getEvaluations().get(0).explanation());
    assertEquals(0.5, result.getEvaluations().get(0).score().orElseThrow());
  }

  @Test
  public void worksWithFlowInteractionSubject() {
    Subject flow =
        new Subject.FlowInteraction(
            "flow-1", "support-agent", Optional.of("instance-1"), "session-1", 3);

    EvaluatorResult result = testKit.evaluate(flow);

    assertTrue(result.isRecord());
    assertEquals("support-agent", result.getEvaluations().get(0).attributes().get("agent"));
  }
}
