/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import java.util.ArrayList;
import java.util.List;

/** State of a shared task list. */
public record TaskListState(String listId, List<TaskListItem> tasks) {

  public record TaskListItem(
      String taskId, String description, String claimedBy, TaskListItemStatus status) {}

  public enum TaskListItemStatus {
    AVAILABLE,
    CLAIMED,
    COMPLETED,
    FAILED,
    CANCELLED
  }

  public static TaskListState empty() {
    return new TaskListState("", List.of());
  }

  public TaskListState withTask(String taskId, String description) {
    var updated = new ArrayList<>(tasks);
    updated.add(new TaskListItem(taskId, description, null, TaskListItemStatus.AVAILABLE));
    return new TaskListState(listId, updated);
  }

  public TaskListState withClaimed(String taskId, String claimedBy) {
    var updated =
        tasks.stream()
            .map(
                t ->
                    t.taskId().equals(taskId)
                        ? new TaskListItem(
                            t.taskId(), t.description(), claimedBy, TaskListItemStatus.CLAIMED)
                        : t)
            .toList();
    return new TaskListState(listId, updated);
  }

  public TaskListState withUnclaimed(String taskId) {
    var updated =
        tasks.stream()
            .map(
                t ->
                    t.taskId().equals(taskId)
                        ? new TaskListItem(
                            t.taskId(), t.description(), null, TaskListItemStatus.AVAILABLE)
                        : t)
            .toList();
    return new TaskListState(listId, updated);
  }

  public TaskListState withCompleted(String taskId) {
    var updated =
        tasks.stream()
            .map(
                t ->
                    t.taskId().equals(taskId)
                        ? new TaskListItem(
                            t.taskId(),
                            t.description(),
                            t.claimedBy(),
                            TaskListItemStatus.COMPLETED)
                        : t)
            .toList();
    return new TaskListState(listId, updated);
  }

  public TaskListState withFailed(String taskId) {
    var updated =
        tasks.stream()
            .map(
                t ->
                    t.taskId().equals(taskId)
                        ? new TaskListItem(
                            t.taskId(), t.description(), t.claimedBy(), TaskListItemStatus.FAILED)
                        : t)
            .toList();
    return new TaskListState(listId, updated);
  }

  public TaskListState withCancelled(String taskId) {
    var updated =
        tasks.stream()
            .map(
                t ->
                    t.taskId().equals(taskId)
                        ? new TaskListItem(
                            t.taskId(),
                            t.description(),
                            t.claimedBy(),
                            TaskListItemStatus.CANCELLED)
                        : t)
            .toList();
    return new TaskListState(listId, updated);
  }
}
