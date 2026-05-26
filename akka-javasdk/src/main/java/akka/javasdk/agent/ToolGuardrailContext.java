/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Per-call context passed to a {@link ToolGuardrail} during {@link ToolGuardrail#evaluate}.
 *
 * <p>Carries data about the specific tool call being evaluated.
 *
 * <p>For construction-time data that doesn't change per call (the guardrail's configured name and
 * its config section) accept a {@link GuardrailContext} parameter in the constructor.
 */
public interface ToolGuardrailContext {

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

  /** The trace id of the interaction. */
  String traceId();
}
