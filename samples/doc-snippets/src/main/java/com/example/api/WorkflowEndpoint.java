package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.application.TransferWorkflowWithNotifications;

// tag::workflow-notification[]
@HttpEndpoint("/transfer")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class WorkflowEndpoint {

  public record TransferUpdate(String message) {} // <1>

  // end::workflow-notification[]
  private final ComponentClient componentClient;

  public WorkflowEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::workflow-notification[]
  @Get("/updates/{transferId}")
  public HttpResponse updates(String transferId) {
    var source = componentClient
      .forWorkflow(transferId)
      .notificationStream(TransferWorkflowWithNotifications::updates)
      .source()
      .map(msg -> new TransferUpdate(msg)); // <2>
    return HttpResponses.serverSentEvents(source);
  }
}
// end::workflow-notification[]
