/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * INTERNAL API
 *
 * <p>One workflow command that follows one remote A2A task. The id is derived from the workflow,
 * the command, the remote agent and the task, so that following the same task again for the same
 * workflow finds the same subscription.
 *
 * <p>It holds the hash of the push notification token, never the token, and the keys of the updates
 * already delivered, so that a repeated push or poll delivers an update once.
 */
@InternalApi
@Component(id = "akka-a2a-remote-task-subscription")
public final class A2aRemoteTaskSubscriptionEntity
    extends KeyValueEntity<A2aRemoteTaskSubscriptionEntity.State> {

  /** How many delivered update keys are kept. Older keys are dropped first. */
  static final int MAX_DELIVERED_KEYS = 256;

  /**
   * The workflow command that receives the updates.
   *
   * @param componentId the component id of the workflow
   * @param methodName the name of the command, as the runtime calls it
   * @param workflowId the id of the workflow
   */
  public record Target(String componentId, String methodName, String workflowId) {}

  /**
   * @param target the command that receives the updates, or null before the subscription starts
   * @param taskId the id of the remote task
   * @param tokenHash the SHA-256 hash of the push notification token, base64url, or null when no
   *     push notification configuration was registered
   * @param deliveredKeys the keys of the delivered updates, oldest first
   * @param ended true once an update that ends the task was delivered
   */
  public record State(
      Target target, String taskId, String tokenHash, List<String> deliveredKeys, boolean ended) {
    public State {
      deliveredKeys = deliveredKeys == null ? List.of() : List.copyOf(deliveredKeys);
    }
  }

  /**
   * @param target the command that receives the updates
   * @param taskId the id of the remote task
   * @param tokenHash the hash of a new push notification token, or null when push is not used
   * @param deliveredKeys the keys of updates that the caller already has, such as the state in the
   *     reply to the message
   */
  public record Subscribe(
      Target target, String taskId, String tokenHash, List<String> deliveredKeys) {}

  /**
   * @param key the key of the delivered update
   * @param ends true when the update ends the task
   */
  public record Delivered(String key, boolean ends) {}

  @Override
  public State emptyState() {
    return new State(null, null, null, List.of(), false);
  }

  /**
   * Starts the subscription, or adds to the one that exists.
   *
   * @return true when the token hash of the command was stored, so the caller registers the push
   *     notification configuration with its token. False when a token was stored before or the
   *     command has none.
   */
  public Effect<Boolean> subscribe(Subscribe cmd) {
    var state = currentState();
    if (state.target() != null && !state.taskId().equals(cmd.taskId())) {
      return effects().error("The subscription follows another task");
    }
    var storeToken = state.tokenHash() == null && cmd.tokenHash() != null;
    var keys = new ArrayList<>(state.deliveredKeys());
    for (var key : cmd.deliveredKeys()) {
      if (!keys.contains(key)) keys.add(key);
    }
    var updated =
        new State(
            cmd.target(),
            cmd.taskId(),
            storeToken ? cmd.tokenHash() : state.tokenHash(),
            trimmed(keys),
            state.ended());
    return effects().updateState(updated).thenReply(storeToken);
  }

  public Effect<Done> delivered(Delivered cmd) {
    var state = currentState();
    if (state.target() == null) return effects().error("No subscription");
    var keys = new ArrayList<>(state.deliveredKeys());
    if (!keys.contains(cmd.key())) keys.add(cmd.key());
    return effects()
        .updateState(
            new State(
                state.target(),
                state.taskId(),
                state.tokenHash(),
                trimmed(keys),
                state.ended() || cmd.ends()))
        .thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<State> get() {
    return effects().reply(currentState());
  }

  private static List<String> trimmed(List<String> keys) {
    return keys.size() <= MAX_DELIVERED_KEYS
        ? keys
        : keys.subList(keys.size() - MAX_DELIVERED_KEYS, keys.size());
  }
}
