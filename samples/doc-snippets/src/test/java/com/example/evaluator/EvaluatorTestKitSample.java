package com.example.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.evaluation.Subject;
import akka.javasdk.ledger.InteractionMetadata;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;
import akka.javasdk.ledger.ModelConfig;
import akka.javasdk.ledger.ModelResponse;
import akka.javasdk.testkit.EvaluatorResult;
import akka.javasdk.testkit.EvaluatorTestKit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

public class EvaluatorTestKitSample {

  // A stub ledger that returns a canned interaction, so the evaluator can be run in isolation.
  private final LedgerClient stubLedger = new LedgerClient() {
    @Override
    public InteractionRecord getInteraction(String interactionId) {
      return sampleRecord(interactionId);
    }

    @Override
    public CompletionStage<InteractionRecord> getInteractionAsync(String interactionId) {
      return CompletableFuture.completedFuture(sampleRecord(interactionId));
    }
  };

  @Test
  public void evaluatesAnInteraction() {
    // tag::test[]
    var testKit = EvaluatorTestKit.of(() -> new InteractionQualityEvaluator(stubLedger));

    EvaluatorResult result = testKit.evaluate(
      new Subject.AgentInteraction("support-agent", "interaction-1")
    );

    assertThat(result.isComplete()).isTrue();
    assertThat(result.getEvaluations()).hasSize(1);
    assertThat(result.getEvaluations().get(0).passed()).isTrue();
    // end::test[]
  }

  private static InteractionRecord sampleRecord(String interactionId) {
    var metadata = new InteractionMetadata(
      new ModelConfig("openai", "gpt-4o", "", 0.0, 1.0, 0, 0),
      Map.of(),
      Instant.now(),
      Instant.now(),
      InteractionMetadata.FinishReason.STOP
    );
    return new InteractionRecord(
      interactionId,
      "session-1",
      "support-agent",
      Optional.empty(),
      metadata,
      "system",
      List.of(),
      List.of(new ModelResponse("m1", "the agent's reply", 0, 0, "", List.of())),
      List.of(),
      Optional.empty(),
      Optional.empty(),
      Instant.now()
    );
  }
}
