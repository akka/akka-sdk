/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.Tracing;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A guardrail that decides whether a tool call may be dispatched.
 *
 * <p>An implementation has a public constructor, optionally taking a {@link GuardrailContext}
 * parameter, which gives access to the guardrail's configured name and config section. The per-call
 * data is delivered to {@link #decide} via {@link CallContext}. Guardrails are enabled via
 * configuration; see the agent documentation.
 *
 * <p>The runtime shares one instance across concurrent calls from different sessions and agents. An
 * implementation must be thread safe.
 */
public non-sealed interface ToolCallGuardrail extends Guardrail {

  /** Where the called tool runs: {@link FunctionTool} or {@link RemoteMcp}. */
  sealed interface ToolOrigin {

    /** A function tool of the agent. */
    record FunctionTool() implements ToolOrigin {}

    /**
     * A tool on a remote MCP server.
     *
     * @param endpoint the URI of the MCP server, as set by {@link RemoteMcpTools#fromServer} or
     *     {@link RemoteMcpTools#fromService}
     */
    record RemoteMcp(String endpoint) implements ToolOrigin {}
  }

  /**
   * Per-call context passed to a {@link ToolCallGuardrail} during {@link ToolCallGuardrail#decide}.
   *
   * <p>Carries data about the specific tool call being checked.
   *
   * <p>For construction-time data that doesn't change per call (the guardrail's configured name and
   * its config section) accept a {@link GuardrailContext} parameter in the constructor.
   */
  public interface CallContext {

    /** The id of the agent performing the tool call. */
    String agentId();

    /** The name of the tool about to be called. For an MCP tool, the name on the MCP server. */
    String toolName();

    /**
     * The id of the tool call, correlating it with the model's tool-call request. Never null, but
     * empty when the model provider does not assign one.
     */
    String toolCallId();

    /**
     * The raw JSON arguments the model produced for the tool call. For an MCP tool, the arguments
     * after {@link RemoteMcpTools.ToolInterceptor#interceptRequest}.
     */
    String arguments();

    /** The session id of the interaction. */
    String sessionId();

    /** Where the called tool runs. */
    ToolOrigin origin();

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
