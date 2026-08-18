/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.MessageContent.TextMessageContent;
import akka.javasdk.ledger.Failure;
import akka.javasdk.ledger.InteractionMetadata;
import akka.javasdk.ledger.InteractionMetadata.FinishReason;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.ModelConfig;
import akka.javasdk.ledger.ModelResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class InteractionRecordAdapterTest {

  @Test
  public void carriesEveryFieldFromTheLedgerRecord() {
    var modelConfig = new ModelConfig("openai", "gpt-4", "", 0.7, 1.0, 0, 1024);
    var metadata =
        new InteractionMetadata(
            modelConfig, Map.of(), Instant.EPOCH, Instant.EPOCH, FinishReason.STOP);
    var record =
        new InteractionRecord(
            "interaction-1",
            "session-1",
            "support-agent",
            Optional.of("flow-1"),
            metadata,
            "You are a helpful assistant.",
            List.of(TextMessageContent.from("What is 2+2?")),
            List.of(new ModelResponse("m1", "The answer is 4.", 8, 6, "", List.of())),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Instant.EPOCH);

    var interaction = new InteractionRecordAdapter(record);

    assertThat(interaction.interactionId()).isEqualTo("interaction-1");
    assertThat(interaction.systemMessage()).isEqualTo("You are a helpful assistant.");
    assertThat(interaction.agentComponentId()).contains("support-agent");
    assertThat(interaction.sessionId()).contains("session-1");
    assertThat(interaction.flowId()).contains("flow-1");
    assertThat(interaction.taskContext()).isEmpty();
    assertThat(interaction.failure()).isEmpty();
    assertThat(interaction.callStartedAt()).contains(Instant.EPOCH);
    assertThat(interaction.callFinishedAt()).contains(Instant.EPOCH);
    assertThat(interaction.finishReason()).contains(FinishReason.STOP);
    assertThat(interaction.modelConfig()).contains(modelConfig);
    assertThat(interaction.totalInputTokens()).hasValue(8);
    assertThat(interaction.totalOutputTokens()).hasValue(6);

    // derived accessors are inherited defaults, computed the same way as on InteractionRecord
    assertThat(interaction.inputText()).isEqualTo("What is 2+2?");
    assertThat(interaction.finalResponseText()).isEqualTo("The answer is 4.");
    assertThat(interaction.transcript()).contains("Response: The answer is 4.");
  }

  @Test
  public void reportsFailureWhenTheInteractionFailed() {
    var metadata =
        new InteractionMetadata(
            new ModelConfig("openai", "gpt-4", "", 0.7, 1.0, 0, 1024),
            Map.of(),
            Instant.EPOCH,
            Instant.EPOCH,
            FinishReason.UNSPECIFIED);
    var failure = new Failure(Failure.FailureReason.TIMEOUT, "model call timed out");
    var record =
        new InteractionRecord(
            "interaction-2",
            "session-1",
            "support-agent",
            Optional.empty(),
            metadata,
            "",
            List.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            Optional.of(failure),
            Instant.EPOCH);

    var interaction = new InteractionRecordAdapter(record);

    assertThat(interaction.failure()).contains(failure);
    assertThat(interaction.flowId()).isEmpty();
    assertThat(interaction.finishReason()).contains(FinishReason.UNSPECIFIED);
  }
}
