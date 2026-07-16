/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.Tracing;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A guardrail that decides whether a tool call may be dispatched (the before-tool-call boundary).
 *
 * <p>An implementation has a public constructor, optionally taking a {@link GuardrailContext}
 * parameter, which gives access to the guardrail's configured name and config section. The per-call
 * data is delivered to {@link #decide} via {@link CallContext}. Guardrails are enabled and bound to
 * boundaries via configuration; see the agent documentation.
 */
public non-sealed interface ToolGuardrail extends Guardrail {

  /**
   * Per-call context passed to a {@link ToolGuardrail} during {@link ToolGuardrail#decide}.
   *
   * <p>Carries data about the specific tool call being checked.
   *
   * <p>For construction-time data that doesn't change per call (the guardrail's configured name and
   * its config section) accept a {@link GuardrailContext} parameter in the constructor.
   */
  public interface CallContext {

    /** The id of the agent performing the tool call. */
    String agentId();

    /** The name of the tool about to be called. */
    String toolName();

    /** The id of the tool call, correlating it with the model's tool-call request. */
    String toolCallId();

    /** The raw JSON arguments the model produced for the tool call. */
    String arguments();

    /** The session id of the interaction. */
    String sessionId();

    /**
     * Provides access to tracing for custom application-specific tracing.
     *
     * <p>Spans started through this are parented to the tool call being checked, so work the
     * guardrail performs (e.g. calling external or internal components) shows up under the
     * interaction's trace.
     *
     * @return tracing interface for custom tracing
     */
    Tracing tracing();
  }

  /**
   * Decides whether the tool call described by {@code ctx} may be dispatched.
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
