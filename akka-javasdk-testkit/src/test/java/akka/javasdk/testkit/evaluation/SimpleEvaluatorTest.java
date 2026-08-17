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

  private Subject agentInteraction(String interactionId) {
    return new Subject.Interaction(interactionId, Optional.of("support-agent"), Optional.empty());
  }

  @Test
  public void completesEvaluation() {
    EvaluatorResult result = testKit.evaluate(agentInteraction("interaction-1"), "eval-42");

    assertTrue(result.isComplete());
    assertFalse(result.isInconclusive());
    assertFalse(result.isAsync());

    assertEquals(1, result.getEvaluations().size());
    Evaluation evaluation = result.getEvaluations().get(0);
    assertTrue(evaluation.passed());
    assertEquals(0.9, evaluation.score().orElseThrow());
    assertEquals("good", evaluation.label().orElseThrow());
    assertEquals("support-agent", evaluation.attributes().get("agent"));
    assertTrue(evaluation.explanation().contains("eval-42"));
  }

  @Test
  public void reportsInconclusive() {
    EvaluatorResult result = testKit.evaluate(agentInteraction("inconclusive"));

    assertTrue(result.isInconclusive());
    assertFalse(result.isComplete());
    assertEquals("cannot evaluate interaction inconclusive", result.getInconclusiveReason());
  }

  @Test
  public void resolvesAsyncEffect() {
    EvaluatorResult result = testKit.evaluate(agentInteraction("async"));

    assertTrue(result.isAsync());
    // async effect resolves to the terminal completion
    assertTrue(result.isComplete());
    assertEquals(1, result.getEvaluations().size());
    assertEquals("async verdict", result.getEvaluations().get(0).explanation());
    assertEquals(0.5, result.getEvaluations().get(0).score().orElseThrow());
  }

  @Test
  public void worksWithFlowInteractionSubject() {
    Subject flow =
        new Subject.Interaction(
            "interaction-1", Optional.of("support-agent"), Optional.of("flow-1"));

    EvaluatorResult result = testKit.evaluate(flow);

    assertTrue(result.isComplete());
    assertEquals("support-agent", result.getEvaluations().get(0).attributes().get("agent"));
  }
}
