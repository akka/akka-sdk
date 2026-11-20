/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * A message between a client and an agent.
 *
 * @param messageId the id of the message, set by its sender
 * @param contextId the conversation the message belongs to
 * @param taskId the task the message belongs to
 * @param role who sent the message
 * @param parts the content of the message
 * @param metadata free form details about the message
 * @param extensions URIs of the protocol extensions that apply to the message
 * @param referenceTaskIds ids of other tasks that this message refers to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aMessage(
    String messageId,
    String contextId,
    String taskId,
    A2aRole role,
    List<A2aPart> parts,
    Map<String, Object> metadata,
    List<String> extensions,
    List<String> referenceTaskIds) {

  public A2aMessage {
    parts = A2aValues.list(parts);
    metadata = A2aValues.optionalMap(metadata);
    extensions = A2aValues.list(extensions);
    referenceTaskIds = A2aValues.list(referenceTaskIds);
  }
}
