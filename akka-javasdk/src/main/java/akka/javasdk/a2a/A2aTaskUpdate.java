/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

/**
 * An event about a task that an agent sends while the task runs, in a stream or as a push
 * notification: a change of status or a new or extended artifact.
 */
public sealed interface A2aTaskUpdate permits A2aTaskStatusUpdate, A2aTaskArtifactUpdate {

  /** The id of the task that the event is about. */
  String taskId();

  /** The id of the conversation that the task belongs to. */
  String contextId();
}
