/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.MessageContent.TextMessageContent;
import akka.javasdk.ledger.Failure;
import akka.javasdk.ledger.InteractionMetadata;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.ModelConfig;
import akka.javasdk.ledger.ModelResponse;
import akka.javasdk.ledger.ToolCall;
import akka.javasdk.ledger.ToolCallResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class InteractionRecordTest {

  private static InteractionMetadata metadata() {
    return new InteractionMetadata(
        new ModelConfig("openai", "gpt-4", "", 0.7, 1.0, 0, 1024),
        Map.of(),
        Instant.EPOCH,
        Instant.EPOCH,
        InteractionMetadata.FinishReason.STOP);
  }

  private static InteractionRecord record(Optional<Failure> failure) {
    var thinkingWithToolCall =
        new ModelResponse(
            "m1",
            "",
            10,
            5,
            "let me think",
            List.of(new ToolCall("t1", "calc", "{\"expr\":\"2+2\"}", "4")));
    var finalAnswer = new ModelResponse("m2", "The answer is 4.", 8, 6, "", List.of());
    return new InteractionRecord(
        "interaction-1",
        "session-1",
        "math-agent",
        Optional.empty(),
        metadata(),
        "You are a calculator.",
        List.of(TextMessageContent.from("What is 2+2?")),
        List.of(thinkingWithToolCall, finalAnswer),
        List.of(
            new ToolCallResponse(
                "t1", "calc", List.<MessageContent>of(TextMessageContent.from("4")))),
        Optional.empty(),
        failure,
        Instant.EPOCH);
  }

  @Test
  public void accessorsFlattenTheRecord() {
    var record = record(Optional.empty());

    assertThat(record.inputText()).isEqualTo("What is 2+2?");
    assertThat(record.finalResponseText()).isEqualTo("The answer is 4.");
    assertThat(record.toolCalls()).hasSize(1);
    assertThat(record.toolCalls().get(0).name()).isEqualTo("calc");
    assertThat(record.totalInputTokens()).isEqualTo(18);
    assertThat(record.totalOutputTokens()).isEqualTo(11);
    assertThat(record.isFlowInteraction()).isFalse();
    assertThat(record.failed()).isFalse();
    assertThat(record.failureSummary()).isEmpty();
  }

  @Test
  public void finalResponseTextSkipsResponsesWithoutContent() {
    // the last response has content; a trailing tool-only response should not blank it out
    var record = record(Optional.empty());
    assertThat(record.finalResponseText()).isEqualTo("The answer is 4.");
  }

  @Test
  public void failureSummaryRendersReasonAndDescription() {
    var record = record(Optional.of(new Failure(Failure.FailureReason.TOOL_CALL, "calc exploded")));

    assertThat(record.failed()).isTrue();
    assertThat(record.failureSummary()).contains("TOOL_CALL: calc exploded");
  }

  @Test
  public void transcriptFlattensInOrder() {
    var transcript = record(Optional.empty()).transcript();

    // exact rendering: system, then input, then each model response (thinking, content, tool calls
    // in order), then the tool call responses the model saw as input
    var expected =
        """
        System: You are a calculator.
        Input: What is 2+2?
        Thinking: let me think
        Tool call calc({"expr":"2+2"}) -> 4
        Response: The answer is 4.
        Tool response calc: 4
        """;
    assertThat(transcript).isEqualTo(expected);
  }

  @Test
  public void transcriptOmitsEmptySystemMessageAndRendersNonTextContent() {
    var record =
        new InteractionRecord(
            "interaction-2",
            "session-1",
            "vision-agent",
            Optional.empty(),
            metadata(),
            "", // no system message
            List.of(
                TextMessageContent.from("describe these"),
                MessageContent.ImageMessageContent.fromUri("https://example.com/cat.png"),
                MessageContent.PdfMessageContent.fromUri("https://example.com/doc.pdf")),
            List.of(new ModelResponse("m1", "a cat and a document", 5, 4, "", List.of())),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Instant.EPOCH);

    // non-text content is dropped from inputText but rendered as placeholders in the transcript
    assertThat(record.inputText()).isEqualTo("describe these");

    var expected =
        """
        Input: describe these
        [image https://example.com/cat.png]
        [pdf https://example.com/doc.pdf]
        Response: a cat and a document
        """;
    assertThat(record.transcript()).isEqualTo(expected);
  }
}
