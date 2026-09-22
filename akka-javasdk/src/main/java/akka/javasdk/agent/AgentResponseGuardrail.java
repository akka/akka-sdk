/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.Tracing;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A guardrail that decides whether the agent's final reply may be returned. The runtime evaluates
 * it once per agent interaction.
 *
 * <p>An implementation has a public constructor, optionally taking a {@link GuardrailContext}
 * parameter, which gives access to the guardrail's configured name and config section. The per-call
 * data is delivered to {@link #decide} via {@link CallContext}.
 *
 * <p>The runtime shares one instance across concurrent calls from different sessions and agents. An
 * implementation must be thread safe.
 */
public non-sealed interface AgentResponseGuardrail extends Guardrail {

  /**
   * Per-call context passed to an {@link AgentResponseGuardrail} during {@link
   * AgentResponseGuardrail#decide}.
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

    /** The name of the model that produced the reply. */
    String modelName();

    /** The final model reply. It carries no tool requests. */
    Message.AiMessage reply();

    /**
     * Provides access to tracing for custom application-specific tracing.
     *
     * <p>Spans started through this are parented to the agent response being checked, so work the
     * guardrail performs (e.g. calling external or internal components) shows up under the
     * interaction's trace.
     *
     * @return tracing interface for custom tracing
     */
    Tracing tracing();
  }

  /**
   * Decides whether the reply described by {@code ctx} may be returned.
   *
   * <p>Use {@link Decision.Deny} to refuse the reply. Returning {@link Decision.Fail} and throwing
   * are equivalent: both mean the guardrail reached no verdict, which is distinct from refusing the
   * reply.
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
   * the reply.
   *
   * @return a CompletionStage with the decision
   */
  default CompletionStage<Decision> decideAsync(CallContext ctx) {
    return CompletableFuture.completedFuture(decide(ctx));
  }
}
