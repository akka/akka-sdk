/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aTaskPushNotificationConfig;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * INTERNAL API
 *
 * <p>What an A2A task holds besides the task itself, keyed by the task id: the push notification
 * configurations of the task, and the message that answered its first message when a task workflow
 * answered with a message instead of a task.
 */
@InternalApi
@Component(id = "akka-a2a-task-extras")
public final class A2aTaskExtrasEntity extends KeyValueEntity<A2aTaskExtrasEntity.State> {

  /**
   * @param pushConfigs the push notification configurations, in the order they were created
   * @param directReply the message that answered the first message, or null
   */
  public record State(List<A2aTaskPushNotificationConfig> pushConfigs, A2aMessage directReply) {
    public State {
      pushConfigs = pushConfigs == null ? List.of() : List.copyOf(pushConfigs);
    }
  }

  @Override
  public State emptyState() {
    return new State(List.of(), null);
  }

  /** Adds a configuration, or replaces the one with the same id. */
  public Effect<A2aTaskPushNotificationConfig> putPushConfig(A2aTaskPushNotificationConfig config) {
    var configs = new ArrayList<>(currentState().pushConfigs());
    configs.removeIf(c -> c.id().equals(config.id()));
    configs.add(config);
    return effects()
        .updateState(new State(configs, currentState().directReply()))
        .thenReply(config);
  }

  /** Removes a configuration. Removing one that does not exist succeeds. */
  public Effect<Done> deletePushConfig(String configId) {
    var configs = new ArrayList<>(currentState().pushConfigs());
    if (!configs.removeIf(c -> c.id().equals(configId))) {
      return effects().reply(Done.getInstance());
    }
    return effects()
        .updateState(new State(configs, currentState().directReply()))
        .thenReply(Done.getInstance());
  }

  public Effect<Done> setDirectReply(A2aMessage message) {
    return effects()
        .updateState(new State(currentState().pushConfigs(), message))
        .thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<State> get() {
    return effects().reply(currentState());
  }
}
