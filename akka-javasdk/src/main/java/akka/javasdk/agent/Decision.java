/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * The outcome of evaluating a {@link ToolGuardrail} or {@link ModelGuardrail}.
 *
 * <p>Three variants:
 *
 * <ul>
 *   <li>{@link Allow} — the call may proceed. Use {@link #allow()} for the common no-reason case,
 *       or {@link #allow(String)} to attach an informational reason (for example, why a borderline
 *       call was let through).
 *   <li>{@link Deny} — the guardrail decided the call must not proceed; carries a human-readable
 *       reason that surfaces to the user as the failure explanation.
 *   <li>{@link Error} — the guardrail could not reach a verdict because the evaluation itself
 *       failed (for example, an upstream classifier was unreachable). The {@code reason} is the
 *       short explanation surfaced to the user; the optional {@code cause} carries the underlying
 *       throwable for logging.
 * </ul>
 */
public sealed interface Decision {

  /** Returns an {@link Allow} decision with no reason. */
  static Allow allow() {
    return Allow.INSTANCE;
  }

  /** Creates an {@link Allow} decision with an informational reason. */
  static Allow allow(String reason) {
    return new Allow(reason);
  }

  /** Creates a {@link Deny} decision with the given reason. */
  static Deny deny(String reason) {
    return new Deny(reason);
  }

  /** Creates an {@link Error} decision with the given reason and no cause. */
  static Error error(String reason) {
    return new Error(reason, null);
  }

  /** Creates an {@link Error} decision with the given reason and cause. */
  static Error error(String reason, Throwable cause) {
    return new Error(reason, cause);
  }

  /**
   * The guardrail saw nothing wrong with the call.
   *
   * @param reason informational explanation; empty string when none was provided
   */
  record Allow(String reason) implements Decision {
    static final Allow INSTANCE = new Allow("");
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
   * @param cause optional underlying throwable for logging; may be {@code null}
   */
  record Error(String reason, Throwable cause) implements Decision {}
}
