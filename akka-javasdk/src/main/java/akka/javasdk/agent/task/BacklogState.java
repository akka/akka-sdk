/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** State of a backlog entity — tracks task references and their claim status. */
public record BacklogState(String name, Map<String, Optional<String>> tasks) {

  /** An entry in the backlog: task ID and who has claimed it (if anyone). */
  public record Entry(String taskId, Optional<String> claimedBy) {}

  public static BacklogState empty() {
    return new BacklogState("", Map.of());
  }

  public boolean isCreated() {
    return !name.isEmpty();
  }

  public boolean isEmpty() {
    return tasks.isEmpty();
  }

  public boolean containsTask(String taskId) {
    return tasks.containsKey(taskId);
  }

  public boolean isClaimed(String taskId) {
    return tasks.containsKey(taskId) && tasks.get(taskId).isPresent();
  }

  public Optional<String> claimedBy(String taskId) {
    return tasks.getOrDefault(taskId, Optional.empty());
  }

  public List<Entry> entries() {
    return tasks.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList();
  }

  public List<String> unclaimedTaskIds() {
    return tasks.entrySet().stream()
        .filter(e -> e.getValue().isEmpty())
        .map(Map.Entry::getKey)
        .toList();
  }

  public List<String> claimedTaskIds() {
    return tasks.entrySet().stream()
        .filter(e -> e.getValue().isPresent())
        .map(Map.Entry::getKey)
        .toList();
  }

  public BacklogState withName(String name) {
    return new BacklogState(name, tasks);
  }

  public BacklogState withTaskAdded(String taskId) {
    var updated = new java.util.HashMap<>(tasks);
    updated.put(taskId, Optional.empty());
    return new BacklogState(name, Map.copyOf(updated));
  }

  public BacklogState withTaskClaimed(String taskId, String claimedBy) {
    var updated = new java.util.HashMap<>(tasks);
    updated.put(taskId, Optional.of(claimedBy));
    return new BacklogState(name, Map.copyOf(updated));
  }

  public BacklogState withTaskReleased(String taskId) {
    var updated = new java.util.HashMap<>(tasks);
    updated.put(taskId, Optional.empty());
    return new BacklogState(name, Map.copyOf(updated));
  }

  public BacklogState withUnclaimedRemoved() {
    var updated = new java.util.HashMap<>(tasks);
    updated.entrySet().removeIf(e -> e.getValue().isEmpty());
    return new BacklogState(name, Map.copyOf(updated));
  }
}
