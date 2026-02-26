/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

public enum TaskStatus {
  PENDING,
  IN_PROGRESS,
  WAITING_FOR_INPUT,
  COMPLETED,
  FAILED
}
