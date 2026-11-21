/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * An event about a task that an agent sends while the task runs, in a stream or as a push
 * notification: a change of status or a new or extended artifact.
 *
 * <p>A workflow command can take it as its parameter, as {@link
 * RemoteA2aAgentClient.SendRequest#notifyWorkflow} requires. The JSON form has no type field: the
 * variant is told apart by its {@code status} or {@code artifact} member.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
  @JsonSubTypes.Type(A2aTaskStatusUpdate.class),
  @JsonSubTypes.Type(A2aTaskArtifactUpdate.class)
})
public sealed interface A2aTaskUpdate permits A2aTaskStatusUpdate, A2aTaskArtifactUpdate {

  /** The id of the task that the event is about. */
  String taskId();

  /** The id of the conversation that the task belongs to. */
  String contextId();
}
