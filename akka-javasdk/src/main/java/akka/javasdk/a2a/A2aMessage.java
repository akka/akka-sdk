/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A message between a client and an agent.
 *
 * <p>Create one with {@link #user(String)} or {@link #agent(String)}, and set the other fields with
 * the {@code with} methods:
 *
 * <pre>{@code
 * A2aMessage.user("What is the status of claim 42?").withContextId(contextId);
 * }</pre>
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

  /** A message from the client with one text part and a new message id. */
  public static A2aMessage user(String text) {
    return user(A2aPart.text(text));
  }

  /** A message from the client with these parts and a new message id. */
  public static A2aMessage user(A2aPart part, A2aPart... moreParts) {
    return of(A2aRole.USER, part, moreParts);
  }

  /** A message from the agent with one text part and a new message id. */
  public static A2aMessage agent(String text) {
    return agent(A2aPart.text(text));
  }

  /** A message from the agent with these parts and a new message id. */
  public static A2aMessage agent(A2aPart part, A2aPart... moreParts) {
    return of(A2aRole.AGENT, part, moreParts);
  }

  private static A2aMessage of(A2aRole role, A2aPart part, A2aPart... moreParts) {
    var parts = new ArrayList<A2aPart>(1 + moreParts.length);
    parts.add(part);
    parts.addAll(Arrays.asList(moreParts));
    return new A2aMessage(UUID.randomUUID().toString(), null, null, role, parts, null, null, null);
  }

  public A2aMessage withMessageId(String messageId) {
    return new A2aMessage(
        messageId, contextId, taskId, role, parts, metadata, extensions, referenceTaskIds);
  }

  public A2aMessage withContextId(String contextId) {
    return new A2aMessage(
        messageId, contextId, taskId, role, parts, metadata, extensions, referenceTaskIds);
  }

  public A2aMessage withTaskId(String taskId) {
    return new A2aMessage(
        messageId, contextId, taskId, role, parts, metadata, extensions, referenceTaskIds);
  }

  public A2aMessage withRole(A2aRole role) {
    return new A2aMessage(
        messageId, contextId, taskId, role, parts, metadata, extensions, referenceTaskIds);
  }

  public A2aMessage withParts(List<A2aPart> parts) {
    return new A2aMessage(
        messageId, contextId, taskId, role, parts, metadata, extensions, referenceTaskIds);
  }

  public A2aMessage withMetadata(Map<String, Object> metadata) {
    return new A2aMessage(
        messageId, contextId, taskId, role, parts, metadata, extensions, referenceTaskIds);
  }

  public A2aMessage withExtensions(List<String> extensions) {
    return new A2aMessage(
        messageId, contextId, taskId, role, parts, metadata, extensions, referenceTaskIds);
  }

  public A2aMessage withReferenceTaskIds(List<String> referenceTaskIds) {
    return new A2aMessage(
        messageId, contextId, taskId, role, parts, metadata, extensions, referenceTaskIds);
  }
}
