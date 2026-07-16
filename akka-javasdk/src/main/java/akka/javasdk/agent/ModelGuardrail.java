/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.Tracing;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * A guardrail that decides whether a model-side call (for example, a model request or an agent
 * response) may proceed.
 *
 * <p>An implementation has a public constructor, optionally taking a {@link GuardrailContext}
 * parameter, which gives access to the guardrail's configured name and config section. The per-call
 * data is delivered to {@link #decide} via {@link CallContext}. Guardrails are enabled and bound to
 * boundaries via configuration; see the agent documentation.
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

    /**
     * The text being checked: the user input for a model request, the model output for an agent
     * response. Non-empty only when {@link #textOnly()} is true; empty for multimodal content,
     * whose parts must be inspected via {@link #contents()}.
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
   * Decides whether the model call described by {@code ctx} may proceed.
   *
   * <p>Use {@link Decision.Deny} to refuse the call. Completing with {@link Decision.Fail}, failing
   * the returned stage, and throwing are equivalent: all three mean the guardrail reached no
   * verdict, which is distinct from refusing the call.
   *
   * @return a CompletionStage with the decision
   */
  CompletionStage<Decision> decide(CallContext ctx);
}
