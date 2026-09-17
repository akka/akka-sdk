/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.Tracing;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A guardrail that decides whether a model-side interaction may proceed.
 *
 * <p>Bound via configuration to one or more model-side boundaries, expressed as {@code use-for}
 * values:
 *
 * <ul>
 *   <li>{@code before-model-call} — fired before every model invocation, with the conversation
 *       entering the call
 *   <li>{@code before-agent-response} — fired once per agent interaction, on the final agent reply
 * </ul>
 *
 * <p>An implementation has a public constructor, optionally taking a {@link GuardrailContext}
 * parameter, which gives access to the guardrail's configured name and config section. The per-call
 * data is delivered to {@link #decide} via {@link CallContext}.
 */
public non-sealed interface ModelGuardrail extends Guardrail {

  /**
   * Per-call context passed to a {@link ModelGuardrail} during {@link ModelGuardrail#decide}.
   *
   * <p>Carries data about the specific model call being checked.
   *
   * <p>For construction-time data that doesn't change per call (the guardrail's configured name and
   * its config section) accept a {@link GuardrailContext} parameter in the constructor.
   */
  public interface CallContext {

    /** The boundary a {@link ModelGuardrail} is being evaluated at. */
    enum Boundary {
      BEFORE_MODEL_CALL,
      BEFORE_AGENT_RESPONSE
    }

    /**
     * A message in the conversation a guardrail inspects, carrying its origin: what the user said,
     * what the model replied (and which tools it requested), and what a tool returned.
     */
    sealed interface ConversationMessage {

      /** A tool call the model requested: its id, tool name, and raw arguments. */
      record ToolCallRequest(String id, String name, String arguments) {}

      /** A user-authored message. */
      record UserMessage(List<MessageContent> contents) implements ConversationMessage {}

      /** A model reply: its text and the tool calls it requested. */
      record AiMessage(String text, List<ToolCallRequest> toolRequests)
          implements ConversationMessage {}

      /** The result a tool returned for a requested tool call. */
      record ToolCallResult(String id, String toolName, List<MessageContent> contents)
          implements ConversationMessage {}
    }

    /**
     * The conversation entering a model call: the system message and the messages so far, oldest
     * first.
     */
    record ConversationContext(String systemMessage, List<ConversationMessage> messages) {}

    /** The boundary this call is being checked at. */
    Boundary boundary();

    /** True when this call is checked at {@code before-model-call}. */
    default boolean isBeforeModelCall() {
      return boundary() == Boundary.BEFORE_MODEL_CALL;
    }

    /** True when this call is checked at {@code before-agent-response}. */
    default boolean isBeforeAgentResponse() {
      return boundary() == Boundary.BEFORE_AGENT_RESPONSE;
    }

    /** The component id of the agent this interaction belongs to. */
    String agentId();

    /** The id of the session this interaction belongs to. */
    String sessionId();

    /** The name of the model involved in the interaction being checked. */
    String modelName();

    /**
     * The conversation entering the model call, present only at {@code before-model-call}. Empty at
     * boundaries that carry no conversation, such as {@code before-agent-response}.
     */
    Optional<ConversationContext> conversation();

    /**
     * The text being checked. Non-empty only when {@link #textOnly()} is true; empty for multimodal
     * content, whose parts must be inspected via {@link #contents()}.
     */
    String text();

    /**
     * True when the content being checked is a single text part, i.e. {@link #contents()} holds
     * exactly one {@link MessageContent.TextMessageContent} and {@link #text()} returns it. False
     * for multimodal content, where {@link #contents()} must be inspected and {@link #text()} is
     * empty.
     */
    boolean textOnly();

    /**
     * The full content being checked, in order: text parts plus any image/PDF parts. Image and PDF
     * parts are either loaded bytes ({@link MessageContent.DataMessageContent}) or URI references
     * ({@link MessageContent.LoadableMessageContent}), so handle both. When {@link #textOnly()} is
     * true this is a single {@link MessageContent.TextMessageContent} that matches {@link #text()}.
     */
    List<MessageContent> contents();

    /**
     * Provides access to tracing for custom application-specific tracing.
     *
     * <p>Spans started through this are parented to the model call being checked, so work the
     * guardrail performs (e.g. calling external or internal components) shows up under the
     * interaction's trace.
     *
     * @return tracing interface for custom tracing
     */
    Tracing tracing();
  }

  /**
   * Decides whether the interaction described by {@code ctx} may proceed.
   *
   * <p>Use {@link Decision.Deny} to refuse the call. Returning {@link Decision.Fail} and throwing
   * are equivalent: both mean the guardrail reached no verdict, which is distinct from refusing the
   * call.
   *
   * <p>This is the method to implement. It may block: guardrails are always evaluated on virtual
   * threads.
   *
   * @return the decision
   */
  Decision decide(CallContext ctx);

  /**
   * Async variant of {@link #decide}, for implementations that prefer composing futures.
   *
   * <p>The default implementation delegates to {@link #decide}. When this method is overridden,
   * {@link #decide} is no longer used, and it is then safe to have it throw {@link
   * UnsupportedOperationException} or return {@code null}.
   *
   * <p>Completing with {@link Decision.Fail}, failing the returned stage, and throwing are
   * equivalent: all three mean the guardrail reached no verdict, which is distinct from refusing
   * the call.
   *
   * @return a CompletionStage with the decision
   */
  default CompletionStage<Decision> decideAsync(CallContext ctx) {
    return CompletableFuture.completedFuture(decide(ctx));
  }
}
