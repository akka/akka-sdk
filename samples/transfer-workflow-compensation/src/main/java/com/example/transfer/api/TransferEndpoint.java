package com.example.transfer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.transfer.application.TransferWorkflow;
import com.example.transfer.domain.TransferState.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/transfer")
public class TransferEndpoint {

  private static final Logger log = LoggerFactory.getLogger(TransferEndpoint.class);

  private final ComponentClient componentClient;

  public TransferEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/{id}")
  public CompletionStage<String> get(String id) {
    log.info("Get transfer with id [{}].", id);
    return componentClient.forWorkflow(id)
      .method(TransferWorkflow::getTransferState).invokeAsync()
      .thenApply(transferState -> transferState.status().toString());
  }

  @Post("/{id}")
  public CompletionStage<HttpResponse> start(String id, Transfer transfer) {
    log.info("Starting transfer [{}].", transfer.toString());
    return componentClient.forWorkflow(id)
      .method(TransferWorkflow::startTransfer).invokeAsync(transfer)
      .thenApply(msg -> HttpResponses.accepted());
  }

  @Post("/{id}/accept")
  public CompletionStage<HttpResponse> accept(String id) {
    log.info("Accepting transfer [{}].", id);
    return componentClient.forWorkflow(id)
      .method(TransferWorkflow::accept).invokeAsync()
      .thenApply(msg -> HttpResponses.accepted());
  }
}
