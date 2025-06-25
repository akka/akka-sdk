package com.example.transfer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.transfer.application.TransfersView;
import com.example.transfer.application.TransfersView.TransferEntries;
import com.example.transfer.application.TransferWorkflow;
import com.example.transfer.domain.TransferState.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class TransferEndpoint {

  private static final Logger log = LoggerFactory.getLogger(TransferEndpoint.class);

  private final ComponentClient componentClient;

  public TransferEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/transfer/{id}")
  public String get(String id) {
    log.info("Get transfer with id [{}].", id);
    var transferState = componentClient.forWorkflow(id)
      .method(TransferWorkflow::getTransferState).invoke();
   return transferState.status().toString();
  }

  @Get("/transfers/completed")
  public TransferEntries getCompleted() {
    return componentClient.forView()
      .method(TransfersView::getAllCompleted).invoke();
  }

  @Post("/transfer/{id}")
  public HttpResponse start(String id, Transfer transfer) {
    log.info("Starting transfer [{}].", transfer.toString());
    componentClient.forWorkflow(id)
      .method(TransferWorkflow::startTransfer).invoke(transfer);
    return HttpResponses.accepted();
  }
}
