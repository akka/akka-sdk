/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.List;

/**
 * Guardrails can protect against harmful inputs and outputs to/from model and tool calls.
 *
 * <p>A Guardrail needs to implement exactly one of {@link ToolCallGuardrail}, {@link
 * ModelCallGuardrail} or {@link AgentResponseGuardrail}, which extend this interface, and have a
 * public constructor optionally taking a {@link GuardrailContext} parameter (the guardrail's
 * configured name and config section).
 *
 * <p>Guardrails are enabled for agents with configuration, see agent documentation.
 */
@SuppressWarnings("removal")
public sealed interface Guardrail
    permits TextGuardrail, ToolCallGuardrail, ModelCallGuardrail, AgentResponseGuardrail {

  /**
   * The result of the guardrail evaluation.
   *
   * @param passed true if the text passed the guardrail evaluation
   * @param explanation reason for the decision, especially when it didn't pass
   * @deprecated Use {@link Decision} from {@link ToolCallGuardrail}, {@link ModelCallGuardrail} or
   *     {@link AgentResponseGuardrail}.
   */
  @Deprecated(since = "3.6.0", forRemoval = true)
  record Result(boolean passed, String explanation) {
    public static final Result OK = new Result(true, "");
  }

  /**
   * Thrown when the text didn't pass the evaluation criteria, and {@code report-only} is true. Can
   * be handled in {@code onFailure}.
   */
  final class GuardrailException extends RuntimeException {
    public GuardrailException(String message) {
      super(message);
    }
  }

  /**
   * A message in the conversation a guardrail inspects, carrying its origin: what the user said,
   * what the model replied (and which tools it requested), and what a tool returned.
   */
  sealed interface Message {

    /** A user-authored message. */
    record UserMessage(List<MessageContent> contents) implements Message {}

    /** A model reply: its text and the tool calls it requested. */
    record AiMessage(String text, List<ToolCallRequest> toolCallRequests) implements Message {}

    /** A tool call the model requested: its id, tool name, and raw arguments. */
    record ToolCallRequest(String id, String name, String arguments) {}

    /** The result a tool returned for a requested tool call. */
    record ToolCallResponse(String id, String name, List<MessageContent> contents)
        implements Message {}
  }
}
