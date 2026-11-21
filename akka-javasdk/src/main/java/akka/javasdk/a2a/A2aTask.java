/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * A unit of work that an agent performs for a client.
 *
 * @param id the id of the task, set by the agent
 * @param contextId the conversation the task belongs to, set by the agent
 * @param status the current status
 * @param artifacts the outputs produced so far
 * @param history the messages exchanged so far, when the request asked for them
 * @param metadata free form details about the task
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aTask(
    String id,
    String contextId,
    A2aTaskStatus status,
    List<A2aArtifact> artifacts,
    List<A2aMessage> history,
    Map<String, Object> metadata) {

  public A2aTask {
    artifacts = A2aValues.list(artifacts);
    history = A2aValues.list(history);
    metadata = A2aValues.optionalMap(metadata);
  }
}
