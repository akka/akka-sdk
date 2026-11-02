/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * The verdict of a {@link ToolCallGuardrail}, {@link ModelCallGuardrail} or {@link
 * AgentResponseGuardrail}: {@link Allow}, {@link Deny}, or {@link Fail}.
 */
public sealed interface Decision {

  /**
   * The guardrail saw nothing wrong with the call.
   *
   * @param reason informational explanation; empty string when none was provided
   */
  record Allow(String reason) implements Decision {

    /** Creates an {@link Allow} decision with no reason. */
    public Allow() {
      this("");
    }
  }

  /**
   * The guardrail decided the call must not proceed.
   *
   * @param reason human-readable explanation. The runtime uses it as the failure message when
   *     {@code report-only} is false.
   */
  record Deny(String reason) implements Decision {}

  /**
   * The guardrail could not reach a verdict because the check itself failed.
   *
   * @param reason human-readable explanation. The runtime uses it as the failure message when
   *     {@code report-only} is false and {@code fail-mode} is {@code fail-closed}.
   * @param cause the underlying throwable, or {@code null} if none
   */
  record Fail(String reason, Throwable cause) implements Decision {

    /** Creates a {@link Fail} decision with the given reason and no cause. */
    public Fail(String reason) {
      this(reason, null);
    }
  }
}
