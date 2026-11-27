/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.MessageContent.TextMessageContent;
import akka.javasdk.evaluation.Evaluation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class RecordIdTest {

  private static InteractionRecord interaction() {
    return new InteractionRecord(
        "interaction-1",
        "session-1",
        "coordinator-agent",
        Optional.of("flow-1"),
        new InteractionMetadata(
            new ModelConfig("openai", "gpt-4", "", 0.7, 1.0, 0, 1024),
            Map.of(),
            Instant.EPOCH,
            Instant.EPOCH,
            InteractionMetadata.FinishReason.STOP),
        "You coordinate research.",
        List.of(TextMessageContent.from("Research quantum computing")),
        List.of(),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        Instant.EPOCH);
  }

  private static EvaluationRecord evaluation() {
    return new EvaluationRecord(
        "evaluation-1",
        "quality-evaluator",
        Optional.empty(),
        EvaluationRecord.Trigger.ON_INTERACTION,
        new EvaluationRecord.Subject.Interaction("interaction-1", "coordinator-agent"),
        Map.of(),
        new EvaluationRecord.Outcome.Verdict(Evaluation.of(true, "good")),
        Instant.EPOCH,
        Optional.empty());
  }

  private static ClassificationRecord classification() {
    return new ClassificationRecord(
        "classification-1",
        "toxicity",
        Optional.of(0.1),
        Optional.of("safe"),
        Optional.of(0.9),
        Map.of(),
        Optional.of("flow-1"),
        Optional.of("evaluation-1"),
        Instant.EPOCH);
  }

  @Test
  public void returnsTheIdOfEachRecord() {
    var interaction = interaction();
    var evaluation = evaluation();
    var classification = classification();

    assertThat(interaction.recordId()).isEqualTo("interaction-1");
    assertThat(evaluation.recordId()).isEqualTo("evaluation-1");
    assertThat(classification.recordId()).isEqualTo("classification-1");
    assertThat(((FlowRecord) interaction).recordId()).isEqualTo("interaction-1");
  }
}
