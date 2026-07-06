/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.agent.MessageContent.TextMessageContent;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

public class LedgerEvaluatorTest {

  private static InteractionRecord answeredInteraction(String interactionId) {
    return new InteractionRecord(
        interactionId,
        "session-1",
        "math-agent",
        Optional.empty(),
        new InteractionMetadata(
            new ModelConfig("openai", "gpt-4", "", 0.7, 1.0, 0, 1024),
            Map.of(),
            Instant.EPOCH,
            Instant.EPOCH,
            InteractionMetadata.FinishReason.STOP),
        "You are a calculator.",
        List.of(TextMessageContent.from("What is 2+2?")),
        List.of(new ModelResponse("m1", "The answer is 4.", 8, 6, "", List.of())),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        Instant.EPOCH);
  }

  @Test
  public void evaluatorFetchesAndUsesTheSeededRecord() {
    var ledger = TestLedgerClient.create().seed(answeredInteraction("interaction-1"));
    var testKit = EvaluatorTestKit.of(() -> new LedgerEvaluator(ledger));

    EvaluatorResult result =
        testKit.evaluate(new Subject.AgentInteraction("math-agent", "interaction-1"));

    assertTrue(result.isComplete());
    var evaluation = result.getEvaluations().get(0);
    assertTrue(evaluation.passed());
    assertEquals("The answer is 4.", evaluation.attributes().get("finalText"));
    assertTrue(evaluation.attributes().get("transcript").contains("Assistant: The answer is 4."));
  }

  @Test
  public void missingInteractionFailsWithNoSuchElement() {
    var ledger = TestLedgerClient.create();

    assertThrows(NoSuchElementException.class, () -> ledger.getInteraction("missing"));

    var async = ledger.getInteractionAsync("missing").toCompletableFuture();
    // join wraps the failure in a CompletionException; the cause is the no-such-element error
    var thrown = assertThrows(CompletionException.class, async::join);
    assertTrue(thrown.getCause() instanceof NoSuchElementException);
  }
}
