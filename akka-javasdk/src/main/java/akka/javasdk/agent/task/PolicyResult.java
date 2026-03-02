/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * Result of a {@link TaskPolicy} evaluation. Determines whether a task lifecycle transition
 * (assignment or completion) should proceed.
 */
public sealed interface PolicyResult {

  /** The transition is allowed — proceed normally. */
  record Allow() implements PolicyResult {}

  /** The transition is denied — block the action and feed the reason back to the agent. */
  record Deny(String reason) implements PolicyResult {}

  /**
   * The transition requires external approval — pause the task in {@link
   * TaskStatus#AWAITING_APPROVAL} until approved or rejected.
   */
  record RequireApproval(String reason) implements PolicyResult {}

  /** Allow the transition. */
  static PolicyResult allow() {
    return new Allow();
  }

  /** Deny the transition with a reason. */
  static PolicyResult deny(String reason) {
    return new Deny(reason);
  }

  /** Require external approval before the transition can proceed. */
  static PolicyResult requireApproval(String reason) {
    return new RequireApproval(reason);
  }
}
