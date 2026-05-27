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
 *   <li>{@link Pass} — the call may proceed. Use the singleton {@link #pass()}.
 *   <li>{@link Block} — the guardrail decided the call must not proceed; carries a human-readable
 *       reason that surfaces to the user as the failure explanation.
 *   <li>{@link Error} — the guardrail could not reach a verdict because the evaluation itself
 *       failed (for example, an upstream classifier was unreachable). The {@code reason} is the
 *       short explanation surfaced to the user; the optional {@code cause} carries the underlying
 *       throwable for logging.
 * </ul>
 */
public sealed interface Decision {

  /** Returns the singleton {@link Pass} instance. */
  static Pass pass() {
    return Pass.INSTANCE;
  }

  /** Creates a {@link Block} decision with the given reason. */
  static Block block(String reason) {
    return new Block(reason);
  }

  /** Creates an {@link Error} decision with the given reason and no cause. */
  static Error error(String reason) {
    return new Error(reason, null);
  }

  /** Creates an {@link Error} decision with the given reason and cause. */
  static Error error(String reason, Throwable cause) {
    return new Error(reason, cause);
  }

  /** The guardrail saw nothing wrong with the call. Use the singleton {@link Decision#pass()}. */
  record Pass() implements Decision {
    static final Pass INSTANCE = new Pass();
  }

  /**
   * The guardrail decided the call must not proceed.
   *
   * @param reason human-readable explanation surfaced to the user as the failure message
   */
  record Block(String reason) implements Decision {}

  /**
   * The guardrail could not reach a verdict because evaluation itself failed.
   *
   * @param reason human-readable explanation surfaced to the user
   * @param cause optional underlying throwable for logging; may be {@code null}
   */
  record Error(String reason, Throwable cause) implements Decision {}
}
