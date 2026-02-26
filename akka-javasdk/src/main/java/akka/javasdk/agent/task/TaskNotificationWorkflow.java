/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.Done;
import akka.javasdk.NotificationPublisher;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

/**
 * Minimal workflow that holds a {@link NotificationPublisher} for a single task's notification
 * stream. Keyed by task ID — one instance per task.
 *
 * <p>This is a prototype implementation. In the actual runtime, notification streams will be
 * supported directly on Event Sourced Entities, eliminating the need for this bridge workflow. The
 * Consumer + Workflow pattern here gives us the right interface and behavior (task-centric
 * notifications that survive handoffs) while the runtime support is being built.
 *
 * @see TaskEventConsumer
 */
@Component(
    id = "akka-task-notifications",
    name = "TaskNotificationWorkflow",
    description = "Notification stream for task lifecycle events")
public final class TaskNotificationWorkflow extends Workflow<String> {

  private final NotificationPublisher<TaskNotification> notificationPublisher;

  public TaskNotificationWorkflow(NotificationPublisher<TaskNotification> notificationPublisher) {
    this.notificationPublisher = notificationPublisher;
  }

  @Override
  public String emptyState() {
    return null;
  }

  /** Publish a task notification to all subscribers. Called by {@link TaskEventConsumer}. */
  public Effect<Done> publish(TaskNotification notification) {
    if (currentState() == null) {
      // First event — initialize the workflow so it stays alive for publishing
      notificationPublisher.publish(notification);
      return effects().updateState("active").pause().thenReply(Done.done());
    }
    notificationPublisher.publish(notification);
    return effects().reply(Done.done());
  }

  /**
   * Returns the notification stream handle for client subscription.
   *
   * <p>Usage: {@code componentClient.forWorkflow(taskId)
   * .notificationStream(TaskNotificationWorkflow::updates).source()}
   */
  public NotificationPublisher.NotificationStream<TaskNotification> updates() {
    return notificationPublisher.stream();
  }
}
