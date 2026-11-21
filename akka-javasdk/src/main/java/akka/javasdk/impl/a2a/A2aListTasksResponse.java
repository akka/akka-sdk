/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.javasdk.a2a.A2aTask;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * INTERNAL API
 *
 * <p>The result of {@code ListTasks}. All members are always written, as the protocol requires.
 *
 * @param tasks the tasks of this page
 * @param nextPageToken the token for the next page, empty on the last page
 * @param pageSize the page size that the agent used
 * @param totalSize the number of tasks that match the request, across all pages
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record A2aListTasksResponse(
    List<A2aTask> tasks, String nextPageToken, int pageSize, int totalSize) {

  public A2aListTasksResponse {
    tasks = A2aValues.list(tasks);
    nextPageToken = nextPageToken == null ? "" : nextPageToken;
  }
}
