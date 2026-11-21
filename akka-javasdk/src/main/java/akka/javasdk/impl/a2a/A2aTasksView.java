/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import java.time.Instant;
import java.util.List;

/**
 * INTERNAL API
 *
 * <p>Lists A2A tasks for {@code ListTasks}. A row holds the fields that the request filters and
 * sorts by. The task itself is read from {@link A2aTaskEntity}, because a task holds free form JSON
 * that a view row cannot.
 */
@InternalApi
@Component(id = "akka-a2a-tasks")
public final class A2aTasksView extends View {

  /**
   * @param taskId the id of the task
   * @param contextId the A2A context id of the task
   * @param state the name of the {@code A2aTaskState}
   * @param updatedAt when the status of the task last changed
   * @param owner the JWT subject that created the task, empty without a token
   */
  public record Row(
      String taskId, String contextId, String state, Instant updatedAt, String owner) {}

  /**
   * @param owner only tasks of this owner
   * @param states only tasks in one of these states
   * @param after only tasks whose status changed after this time
   * @param offset the number of matching tasks to skip
   * @param limit the page size
   */
  public record All(String owner, List<String> states, Instant after, int offset, int limit) {}

  /** As {@link All}, restricted to one context. */
  public record InContext(
      String owner, String contextId, List<String> states, Instant after, int offset, int limit) {}

  /**
   * @param tasks the rows of one page
   * @param totalSize the number of rows that match, across all pages
   * @param hasMore true when rows follow this page
   */
  public record Page(List<Row> tasks, int totalSize, boolean hasMore) {}

  @Consume.FromKeyValueEntity(A2aTaskEntity.class)
  public static class Tasks extends TableUpdater<Row> {
    public Effect<Row> onUpdate(A2aTaskEntity.State state) {
      var task = state.task();
      return effects()
          .updateRow(
              new Row(
                  task.id(),
                  task.contextId(),
                  task.status().state().name(),
                  task.status().timestamp(),
                  state.owner() == null ? "" : state.owner()));
    }
  }

  // Token paging cannot be combined with ORDER BY, so pages use a numeric offset that the endpoint
  // returns as the A2A page token.
  @Query(
      """
      SELECT * AS tasks, total_count() AS totalSize, has_more() AS hasMore
      FROM tasks
      WHERE owner = :owner AND state = ANY(:states) AND updatedAt > :after
      ORDER BY updatedAt DESC
      OFFSET :offset
      LIMIT :limit
      """)
  public QueryEffect<Page> all(All request) {
    return queryResult();
  }

  @Query(
      """
      SELECT * AS tasks, total_count() AS totalSize, has_more() AS hasMore
      FROM tasks
      WHERE owner = :owner AND contextId = :contextId AND state = ANY(:states)
        AND updatedAt > :after
      ORDER BY updatedAt DESC
      OFFSET :offset
      LIMIT :limit
      """)
  public QueryEffect<Page> inContext(InContext request) {
    return queryResult();
  }
}
