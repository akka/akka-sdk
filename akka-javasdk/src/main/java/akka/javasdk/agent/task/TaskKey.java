/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/** A reference to a task, holding its ID and name. */
public record TaskKey(String id, String name) {
  @Override
  public String toString() {
    return name + "|" + id;
  }
}
