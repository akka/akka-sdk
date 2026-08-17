/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.evaluation.Subject;
import akka.javasdk.ledger.InteractionMetadata;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.ModelConfig;
import akka.javasdk.ledger.ModelResponse;
import akka.javasdk.testkit.EvaluatorResult;
import akka.javasdk.testkit.EvaluatorTestKit;
import akka.javasdk.testkit.TestLedgerClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class InteractionAccessorEvaluatorTest {

  private final EvaluatorTestKit<InteractionAccessorEvaluator> testKit =
      EvaluatorTestKit.of(InteractionAccessorEvaluator::new);

  private static InteractionRecord answeredInteraction(String interactionId) {
    return new InteractionRecord(
        interactionId,
        "session-1",
        "support-agent",
        Optional.empty(),
        new InteractionMetadata(
            new ModelConfig("openai", "gpt-4", "", 0.7, 1.0, 0, 1024),
            Map.of(),
            Instant.EPOCH,
            Instant.EPOCH,
            InteractionMetadata.FinishReason.STOP),
        "You are a calculator.",
        List.of(),
        List.of(new ModelResponse("m1", "4", 5, 1, "", List.of())),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        Instant.EPOCH);
  }

  @Test
  public void resolvesTheInteractionThroughTheGivenLedgerClient() {
    var ledger = TestLedgerClient.create().seed(answeredInteraction("interaction-1"));
    var subject =
        new Subject.Interaction("interaction-1", Optional.of("support-agent"), Optional.empty());

    EvaluatorResult result =
        testKit.evaluate(subject, EvaluatorTestKit.DEFAULT_EVALUATION_ID, ledger);

    assertThat(result.isComplete()).isTrue();
    assertThat(result.getEvaluations().get(0).attributes().get("finalText")).isEqualTo("4");
  }

  @Test
  public void reportsInconclusiveForANonInteractionSubject() {
    var subject = new Subject.Flow("flow-1");

    EvaluatorResult result = testKit.evaluate(subject);

    assertThat(result.isInconclusive()).isTrue();
  }
}
