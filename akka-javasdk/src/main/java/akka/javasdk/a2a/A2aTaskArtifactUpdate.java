/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * An event that delivers an artifact of a task, or a chunk of one.
 *
 * @param taskId the id of the task
 * @param contextId the id of the conversation that the task belongs to
 * @param artifact the artifact, or the chunk to add to it
 * @param append true to append the parts of {@code artifact} to the artifact with the same id,
 *     false to replace it
 * @param lastChunk true when this is the last chunk of the artifact
 * @param metadata free form details about the event
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aTaskArtifactUpdate(
    String taskId,
    String contextId,
    A2aArtifact artifact,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean append,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean lastChunk,
    Map<String, Object> metadata)
    implements A2aTaskUpdate {

  public A2aTaskArtifactUpdate {
    metadata = A2aValues.optionalMap(metadata);
  }
}
