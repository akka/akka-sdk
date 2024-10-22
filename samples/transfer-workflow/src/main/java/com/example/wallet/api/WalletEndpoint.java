package com.example.wallet.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.wallet.application.WalletEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/wallet")
public class WalletEndpoint {

  private static final Logger log = LoggerFactory.getLogger(WalletEndpoint.class);

  private final ComponentClient componentClient;

  public WalletEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/{id}")
  public CompletionStage<String> get(String id) {
    log.info("Get wallet with id [{}].", id);
    return componentClient.forEventSourcedEntity(id)
      .method(WalletEntity::get).invokeAsync()
      .thenApply(balance -> "The balance is [" + balance + "].");
  }

  @Post("/{id}/create/{initialAmount}")
  public CompletionStage<HttpResponse> create(String id, int initialAmount) {
    log.info("creating wallet [{}] with balance [{}].", id, initialAmount);
    return componentClient.forEventSourcedEntity(id)
      .method(WalletEntity::create).invokeAsync(initialAmount)
      .thenApply(done -> HttpResponses.ok());
  }

  @Post("/{id}/deposit/{amount}")
  public CompletionStage<HttpResponse> deposit(String id, int amount) {
    return componentClient.forEventSourcedEntity(id)
      .method(WalletEntity::deposit).invokeAsync(amount)
      .thenApply(done -> HttpResponses.ok());
  }

  @Post("/{id}/withdraw/{amount}")
  public CompletionStage<HttpResponse> withdraw(String id, int amount) {
    return componentClient.forEventSourcedEntity(id)
      .method(WalletEntity::withdraw).invokeAsync(amount)
      .thenApply(done -> HttpResponses.ok());
  }
}
