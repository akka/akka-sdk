/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.client.ComponentClient;
import java.util.concurrent.CompletionStage;

/**
 * INTERNAL API
 *
 * <p>The calls of {@link A2aRemoteTasks} to the components that follow remote tasks. They are
 * written in Java, because the component client resolves Java method references.
 */
@InternalApi
public final class A2aRemoteTaskCalls {

  private A2aRemoteTaskCalls() {}

  public static CompletionStage<Boolean> subscribe(
      ComponentClient client,
      String subscriptionId,
      A2aRemoteTaskSubscriptionEntity.Subscribe cmd) {
    return client
        .forKeyValueEntity(subscriptionId)
        .method(A2aRemoteTaskSubscriptionEntity::subscribe)
        .invokeAsync(cmd);
  }

  public static CompletionStage<A2aRemoteTaskSubscriptionEntity.State> subscription(
      ComponentClient client, String subscriptionId) {
    return client
        .forKeyValueEntity(subscriptionId)
        .method(A2aRemoteTaskSubscriptionEntity::get)
        .invokeAsync();
  }

  public static CompletionStage<Done> delivered(
      ComponentClient client,
      String subscriptionId,
      A2aRemoteTaskSubscriptionEntity.Delivered delivered) {
    return client
        .forKeyValueEntity(subscriptionId)
        .method(A2aRemoteTaskSubscriptionEntity::delivered)
        .invokeAsync(delivered);
  }

  public static CompletionStage<Done> startPoll(
      ComponentClient client, String pollId, A2aRemoteTaskPollWorkflow.State poll) {
    return client.forWorkflow(pollId).method(A2aRemoteTaskPollWorkflow::start).invokeAsync(poll);
  }

  public static CompletionStage<Done> startHandOff(
      ComponentClient client, String workflowId, RemoteA2aTaskWorkflow.State handOff) {
    return client.forWorkflow(workflowId).method(RemoteA2aTaskWorkflow::start).invokeAsync(handOff);
  }
}
