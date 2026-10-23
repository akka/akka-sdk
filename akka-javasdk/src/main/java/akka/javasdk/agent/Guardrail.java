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
  @Deprecated(since = "3.7.0", forRemoval = true)
  record Result(boolean passed, String explanation) {
    public static final Result OK = new Result(true, "");
  }

  /**
   * Thrown when {@code report-only} is false and a guardrail denies the call, or fails with {@code
   * fail-mode = fail-closed}.
   */
  final class GuardrailException extends RuntimeException {
    public GuardrailException(String message) {
      super(message);
    }

    /** The cause is the error of a guardrail that failed, or {@code null} for a denial. */
    public GuardrailException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * A message in the conversation a guardrail inspects, carrying its origin: what the user said,
   * what the model replied (and which tools it requested), and what a tool returned. An inline
   * image or PDF, or one that the runtime loads, arrives as a {@link
   * MessageContent.DataMessageContent}. An image or PDF that the model fetches itself arrives as
   * its URI. Session memory stores an inline image or PDF as the text {@code [image]} or {@code
   * [pdf]}, and a message read from session memory carries that text.
   */
  sealed interface Message {

    /** A user-authored message. */
    record UserMessage(List<MessageContent> contents) implements Message {}

    /**
     * A model reply, with only its text and the tool calls it requested. It does not include the
     * model's thinking or provider attributes.
     */
    record AiMessage(String text, List<ToolCallRequest> toolCallRequests) implements Message {
      /**
       * A tool call the model requested: its id, tool name, and raw arguments. The id is never
       * null, but it is empty when the model provider does not assign one.
       */
      public static final record ToolCallRequest(String id, String name, String arguments) {}
    }

    /**
     * The result a tool returned for a requested tool call. The id is never null, but it is empty
     * when the model provider does not assign one.
     *
     * @param isError true when the MCP server reported the result as an error, or when the runtime
     *     turned a failed tool call into an error result for the model.
     */
    record ToolCallResponse(String id, String name, List<MessageContent> contents, boolean isError)
        implements Message {

      public ToolCallResponse(String id, String name, List<MessageContent> contents) {
        this(id, name, contents, false);
      }
    }
  }
}
