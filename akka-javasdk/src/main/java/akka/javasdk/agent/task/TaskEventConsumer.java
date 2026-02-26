/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;

/**
 * Consumer that bridges {@link TaskEntity} events to the {@link TaskNotificationWorkflow} for
 * real-time notification streaming.
 *
 * <p>This is a prototype implementation. In the actual runtime, Event Sourced Entities will support
 * notification streams directly (like Workflows do today), and this Consumer bridge will no longer
 * be needed. The entity's event journal will be the notification source, with the runtime handling
 * the subscription plumbing internally.
 *
 * <p>For this prototype, the Consumer subscribes to all TaskEntity events and routes each event to
 * a per-task-ID {@link TaskNotificationWorkflow}, which holds the {@link
 * akka.javasdk.NotificationPublisher}. This gives us task-centric notifications that follow the
 * task across agent handoffs — subscribers see the full lifecycle regardless of which agent is
 * processing.
 */
@Component(id = "akka-task-event-consumer")
@Consume.FromEventSourcedEntity(TaskEntity.class)
public class TaskEventConsumer extends Consumer {

  private final ComponentClient componentClient;

  public TaskEventConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(TaskEvent event) {
    var taskId = messageContext().eventSubject().orElseThrow();
    var notification = toNotification(event);
    componentClient
        .forWorkflow(taskId)
        .method(TaskNotificationWorkflow::publish)
        .invoke(notification);
    return effects().done();
  }

  private TaskNotification toNotification(TaskEvent event) {
    return switch (event) {
      case TaskEvent.TaskCreated e -> new TaskNotification.TaskCreated(e.taskId(), e.description());
      case TaskEvent.TaskAssigned e -> new TaskNotification.TaskAssigned(e.taskId(), e.assignee());
      case TaskEvent.TaskStarted e -> new TaskNotification.TaskStarted(e.taskId());
      case TaskEvent.TaskCompleted e -> new TaskNotification.TaskCompleted(e.taskId(), e.result());
      case TaskEvent.TaskFailed e -> new TaskNotification.TaskFailed(e.taskId(), e.reason());
      case TaskEvent.TaskHandedOff e ->
          new TaskNotification.TaskHandedOff(e.taskId(), e.newAssignee());
      case TaskEvent.DecisionRequested e ->
          new TaskNotification.DecisionRequested(
              e.taskId(), e.decisionId(), e.question(), e.decisionType());
      case TaskEvent.InputProvided e ->
          new TaskNotification.InputProvided(e.taskId(), e.decisionId(), e.response());
    };
  }
}
