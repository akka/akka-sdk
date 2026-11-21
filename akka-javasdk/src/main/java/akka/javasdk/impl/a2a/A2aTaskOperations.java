/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskState;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * INTERNAL API
 *
 * <p>The task methods that do not run an agent: {@code ListTasks}, which reads {@link
 * A2aTasksView}, and {@code CancelTask}.
 */
@InternalApi
final class A2aTaskOperations {

  static final int DEFAULT_PAGE_SIZE = 50;
  static final int MAX_PAGE_SIZE = 100;

  private final A2aRequestHandler handler;

  A2aTaskOperations(A2aRequestHandler handler) {
    this.handler = handler;
  }

  /** The {@code ListTasks} request of the HTTP+JSON query parameters. */
  A2aListTasksRequest listRequestFromQuery() {
    return new A2aListTasksRequest(
        null,
        handler.queryParam("contextId").orElse(null),
        handler.queryParam("status").orElse(null),
        handler.queryParam("pageSize").map(v -> parseInt("pageSize", v)).orElse(null),
        handler.queryParam("pageToken").orElse(null),
        handler.queryParam("historyLength").map(v -> parseInt("historyLength", v)).orElse(null),
        handler
            .queryParam("statusTimestampAfter")
            .map(A2aTaskOperations::parseInstant)
            .orElse(null),
        handler.queryParam("includeArtifacts").map(Boolean::valueOf).orElse(null));
  }

  /**
   * Lists the tasks of the caller. A caller without an authenticated subject is refused, because
   * its tasks cannot be told apart from the tasks of other callers.
   */
  A2aListTasksResponse list(A2aListTasksRequest request) {
    var owner = handler.owner();
    if (owner == null) {
      throw new A2aException(
          A2aErrorCode.UNSUPPORTED_OPERATION,
          "ListTasks needs an authenticated caller: the endpoint must have @JWT and the token a"
              + " subject");
    }
    int pageSize = request.pageSize() == null ? DEFAULT_PAGE_SIZE : request.pageSize();
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw A2aRequestHandler.invalidParams(
          "pageSize must be from 1 to " + MAX_PAGE_SIZE + ", not " + pageSize);
    }
    A2aRequestHandler.checkHistoryLength(request.historyLength());
    int offset = parseOffset(request.pageToken());
    var status = request.status() == null ? null : parseState(request.status());
    var states =
        status == null || status == A2aTaskState.UNSPECIFIED
            ? Arrays.stream(A2aTaskState.values()).map(Enum::name).toList()
            : List.of(status.name());
    var after =
        request.statusTimestampAfter() == null ? Instant.EPOCH : request.statusTimestampAfter();
    var views = handler.componentClient.forView();
    var page =
        A2aRequestHandler.isBlank(request.contextId())
            ? views
                .method(A2aTasksView::all)
                .invoke(new A2aTasksView.All(owner, states, after, offset, pageSize))
            : views
                .method(A2aTasksView::inContext)
                .invoke(
                    new A2aTasksView.InContext(
                        owner, request.contextId(), states, after, offset, pageSize));

    var reads =
        page.tasks().stream()
            .map(
                row ->
                    handler
                        .componentClient
                        .forKeyValueEntity(row.taskId())
                        .method(A2aTaskEntity::get)
                        .invokeAsync()
                        .toCompletableFuture())
            .toList();
    CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).join();
    boolean includeArtifacts = Boolean.TRUE.equals(request.includeArtifacts());
    var tasks =
        reads.stream()
            .map(read -> read.join().state())
            .filter(state -> state != null)
            .map(state -> A2aRequestHandler.trimHistory(state.task(), request.historyLength()))
            .map(
                includeArtifacts
                    ? Function.<A2aTask>identity()
                    : A2aTaskOperations::withoutArtifacts)
            .toList();
    var next = page.hasMore() ? String.valueOf(offset + pageSize) : "";
    return new A2aListTasksResponse(tasks, next, pageSize, page.totalSize());
  }

  A2aTask cancel(A2aCancelTaskRequest request) {
    if (A2aRequestHandler.isBlank(request.id())) {
      throw A2aRequestHandler.invalidParams("The request has no task id");
    }
    var task = handler.lookup(request.id());
    var workflowTask = handler.taskWorkflows().cancel(handler.skillTable(), task);
    if (workflowTask.isPresent()) return workflowTask.get();
    var result =
        handler
            .componentClient
            .forKeyValueEntity(request.id())
            .method(A2aTaskEntity::cancel)
            .invoke();
    if (!result.canceled()) {
      throw new A2aException(
          A2aErrorCode.TASK_NOT_CANCELABLE,
          "Task ["
              + request.id()
              + "] is already "
              + result.state().task().status().state().wireName()
              + " and cannot be canceled");
    }
    return result.state().task();
  }

  private static A2aTask withoutArtifacts(A2aTask task) {
    return new A2aTask(
        task.id(), task.contextId(), task.status(), null, task.history(), task.metadata());
  }

  private static int parseOffset(String pageToken) {
    if (A2aRequestHandler.isBlank(pageToken)) return 0;
    try {
      int offset = Integer.parseInt(pageToken);
      if (offset >= 0) return offset;
    } catch (NumberFormatException e) {
      // reported below
    }
    throw A2aRequestHandler.invalidParams("The pageToken is not valid");
  }

  private static int parseInt(String name, String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw A2aRequestHandler.invalidParams(name + " must be an integer");
    }
  }

  private static Instant parseInstant(String value) {
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      throw A2aRequestHandler.invalidParams("statusTimestampAfter must be an ISO 8601 timestamp");
    }
  }

  private static A2aTaskState parseState(String value) {
    return Arrays.stream(A2aTaskState.values())
        .filter(s -> s.wireName().equals(value) || s.name().equals(value))
        .findFirst()
        .orElseThrow(() -> A2aRequestHandler.invalidParams("Unknown task state [" + value + "]"));
  }
}
