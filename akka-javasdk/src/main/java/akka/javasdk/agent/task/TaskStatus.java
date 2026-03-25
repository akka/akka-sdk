/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

public enum TaskStatus {
  PENDING,
  ASSIGNED,
  IN_PROGRESS,
  COMPLETED,
  FAILED,
  CANCELLED
}
