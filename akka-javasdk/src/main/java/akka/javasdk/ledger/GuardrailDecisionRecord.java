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
 * @param reportOnly true when the guardrail only reports and the interaction continues regardless
 *     of the decision
 * @param explanation the explanation the guardrail gave for its decision
 * @param duration how long the guardrail took to decide
 * @param toolCallId the id of the tool call the guardrail inspected, or empty when it inspected
 *     none
 * @param toolName the name of the tool the guardrail inspected, or empty when it inspected none
 * @param toolOrigin where the inspected tool runs
 * @param mcpEndpoint the endpoint of the remote MCP server the inspected tool runs on, or empty
 *     when the tool is not a remote MCP tool
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
    boolean reportOnly,
    String explanation,
    Duration duration,
    Optional<String> toolCallId,
    Optional<String> toolName,
    ToolOrigin toolOrigin,
    Optional<String> mcpEndpoint,
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

  /** Where the tool inspected by a guardrail runs. A later SDK version can add values. */
  public enum ToolOrigin {
    /** The guardrail inspected no tool, or this version of the SDK does not know the origin. */
    UNSPECIFIED,
    /** A function tool of the service. */
    FUNCTION_TOOL,
    /** A tool on a remote MCP server. */
    REMOTE_MCP
  }
}
