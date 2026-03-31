/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.annotation.DoNotInherit;

/**
 * Lifecycle notifications published by the runtime for an autonomous agent instance.
 *
 * <p>These events are emitted automatically by the runtime as the agent progresses through its
 * execution loop. They are not published by user code.
 *
 * <p>Subscribe to notifications via {@link
 * akka.javasdk.client.AutonomousAgentClient#notifications()}.
 */
@DoNotInherit
public sealed interface Notification {

  /** Agent activated — transitioned from idle to processing. */
  final class Activated implements Notification {
    public Activated() {}
  }

  /** Agent deactivated — no more work, back to idle. */
  final class Deactivated implements Notification {
    public Deactivated() {}
  }

  /** Agent iteration started — LLM call beginning. */
  final class IterationStarted implements Notification {
    public IterationStarted() {}
  }

  /** Agent iteration completed successfully. */
  final class IterationCompleted implements Notification {
    private final int inputTokens;
    private final int outputTokens;

    public IterationCompleted(int inputTokens, int outputTokens) {
      this.inputTokens = inputTokens;
      this.outputTokens = outputTokens;
    }

    /** Number of input tokens consumed by this iteration. */
    public int inputTokens() {
      return inputTokens;
    }

    /** Number of output tokens produced by this iteration. */
    public int outputTokens() {
      return outputTokens;
    }
  }

  /** Agent iteration failed. */
  final class IterationFailed implements Notification {
    private final String reason;

    public IterationFailed(String reason) {
      this.reason = reason;
    }

    /** The failure reason. */
    public String reason() {
      return reason;
    }
  }

  /** Agent stopped. */
  final class Stopped implements Notification {
    public Stopped() {}
  }
}
