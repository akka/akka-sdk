/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

public enum TaskStatus {
  PENDING,
  IN_PROGRESS,
  AWAITING_APPROVAL,
  COMPLETED,
  FAILED
}
