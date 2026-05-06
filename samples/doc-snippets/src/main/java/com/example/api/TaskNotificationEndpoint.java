package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.agent.task.TaskNotification;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;

// tag::task-notification[]
@HttpEndpoint("/tasks")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TaskNotificationEndpoint {

  private final ComponentClient componentClient;

  public TaskNotificationEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/notifications/{taskId}")
  public HttpResponse notifications(String taskId) {
    var source = componentClient
      .forTask(taskId)
      .notificationStream() // <1>
      .map(Note::from); // <2>
    return HttpResponses.serverSentEvents(source);
  }

  public record Note(String kind, String taskId, String taskName, String detail) { // <3>
    static Note from(TaskNotification n) {
      return switch (n) {
        case TaskNotification.Completed c -> new Note(
          "completed",
          c.taskId(),
          c.taskName(),
          c.result()
        );
        case TaskNotification.ResultRejected r -> new Note(
          "result-rejected",
          r.taskId(),
          r.taskName(),
          r.reason()
        );
        case TaskNotification.Failed f -> new Note(
          "failed",
          f.taskId(),
          f.taskName(),
          f.reason()
        );
        case TaskNotification.Cancelled c -> new Note(
          "cancelled",
          c.taskId(),
          c.taskName(),
          c.reason()
        );
      };
    }
  }
}
// end::task-notification[]
