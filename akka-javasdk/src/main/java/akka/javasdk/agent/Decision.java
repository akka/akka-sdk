/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * The outcome of evaluating a {@link ToolGuardrail} or {@link ModelGuardrail}: {@link Allow},
 * {@link Deny}, or {@link Fail}.
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
   * @param reason human-readable explanation surfaced to the user as the failure message
   */
  record Deny(String reason) implements Decision {}

  /**
   * The guardrail could not reach a verdict because evaluation itself failed.
   *
   * @param reason human-readable explanation surfaced to the user
   * @param cause the underlying throwable, or {@code null} if none
   */
  record Fail(String reason, Throwable cause) implements Decision {

    /** Creates a {@link Fail} decision with the given reason and no cause. */
    public Fail(String reason) {
      this(reason, null);
    }
  }
}
