/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatus;
import akka.javasdk.client.ComponentClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * INTERNAL API
 *
 * <p>Reads and changes A2A tasks in {@link A2aTaskEntity}, and sends each change to the push
 * notification configurations of the task. A change reads the task, applies a function and stores
 * the result, so it is meant for one writer at a time: the task workflow while it runs, or the
 * endpoint while the workflow waits for input.
 */
@InternalApi
public final class A2aTaskStore {

  private final ComponentClient componentClient;

  public A2aTaskStore(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  /** The stored task, or empty when it does not exist. */
  public Optional<A2aTaskEntity.State> get(String taskId) {
    return Optional.ofNullable(
        componentClient.forKeyValueEntity(taskId).method(A2aTaskEntity::get).invoke().state());
  }

  /**
   * Applies a change to a task that has not ended, stores it and sends it to the push notification
   * configurations. A task that has ended is left unchanged.
   *
   * @return the stored state, or empty when the task has ended
   * @throws IllegalStateException when the task does not exist
   */
  public Optional<A2aTaskEntity.State> modify(String taskId, UnaryOperator<A2aTask> change) {
    var current =
        get(taskId)
            .orElseThrow(() -> new IllegalStateException("A2A task [" + taskId + "] not found"));
    if (current.task().status().state().terminal()) return Optional.empty();
    var stored =
        componentClient
            .forKeyValueEntity(taskId)
            .method(A2aTaskEntity::update)
            .invoke(change.apply(current.task()));
    A2aPushNotifier.taskChanged(componentClient, stored);
    return Optional.of(stored);
  }

  // ---------------------------------------------------------------------------------------------
  // Changes of a task

  static A2aTask withStatus(A2aTask task, A2aTaskState state, A2aMessage message) {
    return new A2aTask(
        task.id(),
        task.contextId(),
        new A2aTaskStatus(state, message, Instant.now()),
        task.artifacts(),
        task.history(),
        task.metadata());
  }

  /**
   * Adds a message to the history. A message with the same id, or an agent message with the same
   * parts as the latest message, is not added again.
   */
  static A2aTask withHistory(A2aTask task, A2aMessage message) {
    var history = new ArrayList<>(task.history());
    boolean known = history.stream().anyMatch(m -> m.messageId().equals(message.messageId()));
    boolean repeated =
        message.role() == A2aRole.AGENT
            && !history.isEmpty()
            && history.getLast().role() == A2aRole.AGENT
            && history.getLast().parts().equals(message.parts());
    if (known || repeated) return task;
    history.add(message);
    return new A2aTask(
        task.id(), task.contextId(), task.status(), task.artifacts(), history, task.metadata());
  }

  /** Adds an artifact, or replaces the one with the same id. */
  static A2aTask withArtifact(A2aTask task, A2aArtifact artifact) {
    var artifacts = new ArrayList<>(task.artifacts());
    int index = -1;
    for (int i = 0; i < artifacts.size(); i++) {
      if (artifacts.get(i).artifactId().equals(artifact.artifactId())) index = i;
    }
    if (index >= 0) artifacts.set(index, artifact);
    else artifacts.add(artifact);
    return new A2aTask(
        task.id(), task.contextId(), task.status(), artifacts, task.history(), task.metadata());
  }

  /** A message from the agent in the task and context of the task. */
  static A2aMessage agentMessage(String taskId, String contextId, List<A2aPart> parts) {
    return new A2aMessage(
        UUID.randomUUID().toString(), contextId, taskId, A2aRole.AGENT, parts, null, null, null);
  }
}
