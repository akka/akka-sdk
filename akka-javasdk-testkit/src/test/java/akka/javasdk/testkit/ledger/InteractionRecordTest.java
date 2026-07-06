/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    assertEquals("What is 2+2?", record.userText());
    assertEquals("The answer is 4.", record.finalAssistantText());
    assertEquals(1, record.toolCalls().size());
    assertEquals("calc", record.toolCalls().get(0).name());
    assertEquals(18, record.totalInputTokens());
    assertEquals(11, record.totalOutputTokens());
    assertFalse(record.isFlowInteraction());
    assertFalse(record.failed());
    assertTrue(record.failureSummary().isEmpty());
  }

  @Test
  public void finalAssistantTextSkipsResponsesWithoutContent() {
    // the last response has content; a trailing tool-only response should not blank it out
    var record = record(Optional.empty());
    assertEquals("The answer is 4.", record.finalAssistantText());
  }

  @Test
  public void failureSummaryRendersReasonAndDescription() {
    var record = record(Optional.of(new Failure(Failure.FailureReason.TOOL_CALL, "calc exploded")));

    assertTrue(record.failed());
    assertEquals("TOOL_CALL: calc exploded", record.failureSummary().orElseThrow());
  }

  @Test
  public void transcriptFlattensInOrder() {
    var transcript = record(Optional.empty()).transcript();

    // exact rendering: system, then user, then each model response (thinking, content, tool calls
    // in order), then the tool call responses the model saw as input
    var expected =
        """
        System: You are a calculator.
        User: What is 2+2?
        Assistant (thinking): let me think
        Tool call calc({"expr":"2+2"}) -> 4
        Assistant: The answer is 4.
        Tool response calc: 4
        """;
    assertEquals(expected, transcript);
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

    // non-text content is dropped from userText but rendered as placeholders in the transcript
    assertEquals("describe these", record.userText());

    var expected =
        """
        User: describe these
        [image https://example.com/cat.png]
        [pdf https://example.com/doc.pdf]
        Assistant: a cat and a document
        """;
    assertEquals(expected, record.transcript());
  }
}
