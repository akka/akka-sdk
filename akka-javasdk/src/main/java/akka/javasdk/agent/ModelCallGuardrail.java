/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.Tracing;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A guardrail that decides whether a model call may proceed. The runtime evaluates it before every
 * model invocation, with the conversation entering the call.
 *
 * <p>An implementation has a public constructor, optionally taking a {@link GuardrailContext}
 * parameter, which gives access to the guardrail's configured name and config section. The per-call
 * data is delivered to {@link #decide} via {@link CallContext}.
 *
 * <p>The runtime shares one instance across concurrent calls from different sessions and agents. An
 * implementation must be thread safe.
 */
public non-sealed interface ModelCallGuardrail extends Guardrail {

  /**
   * Per-call context passed to a {@link ModelCallGuardrail} during {@link
   * ModelCallGuardrail#decide}.
   *
   * <p>For construction-time data that does not change per call, accept a {@link GuardrailContext}
   * parameter in the constructor. That data is the guardrail's configured name and its config
   * section.
   */
  interface CallContext {

    /** The component id of the agent this interaction belongs to. */
    String agentId();

    /** The id of the session this interaction belongs to. */
    String sessionId();

    /** The name of the model about to be called. */
    String modelName();

    /** The system message, or an empty string when the agent has none. */
    String systemMessage();

    /** All messages entering the model call, oldest first. */
    List<Message> messages();

    /**
     * The messages after the last {@link Message.AiMessage}, oldest first. The user message on the
     * first model call; the tool results on each later call.
     */
    List<Message> newMessages();

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
