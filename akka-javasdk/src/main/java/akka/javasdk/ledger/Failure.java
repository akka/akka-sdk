/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

/**
 * A failure that terminated an interaction.
 *
 * <p>The {@code reason} is the stored category; any finer detail (such as the specific tool that
 * failed) is carried in the free-text {@code description}.
 *
 * @param reason the category of failure
 * @param description a human-readable description of the failure
 */
public record Failure(FailureReason reason, String description) {

  /** Why an interaction failed. */
  public enum FailureReason {
    /** The failure reason was not reported. */
    UNSPECIFIED,
    /** The model call itself failed. */
    MODEL,
    /** The model provider rate-limited the call. */
    RATE_LIMIT,
    /** The model call timed out. */
    TIMEOUT,
    /** The interaction used a feature the model does not support. */
    UNSUPPORTED_FEATURE,
    /** An internal error occurred. */
    INTERNAL,
    /** The model output could not be parsed into the expected result type. */
    OUTPUT_PARSING,
    /** A tool call failed. */
    TOOL_CALL,
    /** An MCP tool call failed. */
    MCP_TOOL_CALL,
    /** A guardrail rejected the interaction. */
    GUARDRAIL,
    /** Referenced content (for example an image or PDF) could not be loaded. */
    CONTENT_LOADING
  }
}
