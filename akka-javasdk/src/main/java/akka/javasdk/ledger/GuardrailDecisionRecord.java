/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The record of one guardrail decision, as fetched from the ledger.
 *
 * @param recordId the globally unique id of the record
 * @param agentComponentId the component id of the agent the guardrail ran for
 * @param interactionId the id of the interaction the guardrail ran in
 * @param sessionId the session id of the interaction
 * @param flowId the flow the interaction belongs to, or empty when it belongs to none
 * @param name the name of the guardrail
 * @param controlId the control id the service declares for the guardrail, or empty when it declares
 *     none
 * @param category the category of the guardrail
 * @param boundary where in the interaction the guardrail ran
 * @param decision what the guardrail decided
 * @param explanation the explanation the guardrail gave for its decision, or the failure message
 *     when the decision is {@link Decision#FAIL}
 * @param reportOnly true when the guardrail only reports and the interaction continues regardless
 *     of the decision
 * @param failMode what the runtime does when the guardrail fails to decide, without effect when
 *     {@code reportOnly} is true
 * @param duration how long the guardrail took to decide
 * @param toolCall the tool call the guardrail decided on, or empty when it decided on none
 * @param timestamp when the decision was recorded
 */
public record GuardrailDecisionRecord(
    String recordId,
    String agentComponentId,
    String interactionId,
    String sessionId,
    Optional<String> flowId,
    String name,
    Optional<String> controlId,
    String category,
    Boundary boundary,
    Decision decision,
    String explanation,
    boolean reportOnly,
    FailMode failMode,
    Duration duration,
    Optional<ToolCall> toolCall,
    Instant timestamp) {

  /** Where in an interaction a guardrail ran. A later SDK version can add values. */
  public enum Boundary {
    /** The runtime did not report the boundary, or this version of the SDK does not know it. */
    UNSPECIFIED,
    /** Before the request is sent to the model. */
    BEFORE_MODEL_CALL,
    /** Before the agent response is returned. */
    BEFORE_AGENT_RESPONSE,
    /** Before a tool is called. */
    BEFORE_TOOL_CALL
  }

  /** What a guardrail decided. A later SDK version can add values. */
  public enum Decision {
    /** The runtime did not report the decision, or this version of the SDK does not know it. */
    UNSPECIFIED,
    /** The guardrail allowed the content. */
    ALLOW,
    /** The guardrail denied the content. */
    DENY,
    /** The guardrail threw instead of deciding. */
    FAIL
  }

  /** What the runtime does when a guardrail fails to decide. A later SDK version can add values. */
  public enum FailMode {
    /** This version of the SDK does not know the fail mode. */
    UNSPECIFIED,
    /** A failing guardrail stops the interaction. */
    FAIL_CLOSED,
    /** A failing guardrail lets the interaction continue. */
    FAIL_OPEN
  }

  /** Where the tool of a guardrail decision runs. A later SDK version can add values. */
  public enum ToolOrigin {
    /** The runtime did not report the origin, or this version of the SDK does not know it. */
    UNSPECIFIED,
    /** A function tool of the service. */
    FUNCTION_TOOL,
    /** A tool on a remote MCP server. */
    REMOTE_MCP
  }

  /**
   * The tool call a guardrail decided on. Set for a before-tool-call guardrail, and for a
   * before-agent-response guardrail on the response a control tool carries.
   *
   * @param toolCallId the id the model gave the call, or empty when it gave none
   * @param toolName the name of the called tool
   * @param origin where the tool runs
   * @param mcpEndpoint the endpoint of the remote MCP server the tool runs on, or empty when the
   *     tool is not a remote MCP tool
   */
  public record ToolCall(
      Optional<String> toolCallId,
      String toolName,
      ToolOrigin origin,
      Optional<String> mcpEndpoint) {}
}
