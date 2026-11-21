/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.client.ComponentClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * INTERNAL API
 *
 * <p>Reads and changes A2A tasks in {@link A2aTaskEntity}, and sends each change to the push
 * notification configurations of the task.
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
   * Applies a change to a task that has not ended, and sends it to the push notification
   * configurations. A task that has ended is left unchanged. It blocks, so call it only where
   * blocking is allowed, such as an endpoint or a workflow step.
   *
   * @return the stored state, or empty when the task has ended
   */
  public Optional<A2aTaskEntity.State> change(String taskId, A2aTaskEntity.Change change) {
    var result =
        componentClient.forKeyValueEntity(taskId).method(A2aTaskEntity::change).invoke(change);
    if (!result.applied()) return Optional.empty();
    A2aPushNotifier.taskChanged(componentClient, result.state()).toCompletableFuture().join();
    return Optional.of(result.state());
  }

  /** As {@link #change}, without blocking. */
  public CompletionStage<Optional<A2aTaskEntity.State>> changeAsync(
      String taskId, A2aTaskEntity.Change change) {
    return componentClient
        .forKeyValueEntity(taskId)
        .method(A2aTaskEntity::change)
        .invokeAsync(change)
        .thenCompose(this::changed);
  }

  private CompletionStage<Optional<A2aTaskEntity.State>> changed(A2aTaskEntity.Changed result) {
    if (!result.applied()) return CompletableFuture.completedFuture(Optional.empty());
    return A2aPushNotifier.taskChanged(componentClient, result.state())
        .thenApply(done -> Optional.of(result.state()));
  }

  /** A message from the agent in the task and context of the task. */
  static A2aMessage agentMessage(String taskId, String contextId, List<A2aPart> parts) {
    return new A2aMessage(
        UUID.randomUUID().toString(), contextId, taskId, A2aRole.AGENT, parts, null, null, null);
  }
}
