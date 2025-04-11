package com.example.wallet.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.wallet.application.WalletEntity;
import com.example.wallet.application.WalletEntity.WalletResult.Failure;
import com.example.wallet.application.WalletEntity.WalletResult.Success;
import com.example.wallet.domain.WalletCommand.Deposit;
import com.example.wallet.domain.WalletCommand.Withdraw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

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
  public String get(String id) {
    log.info("Get wallet with id [{}].", id);
    var balance = componentClient.forEventSourcedEntity(id)
      .method(WalletEntity::get).invoke();
    return "The balance is [" + balance + "].";
  }

  @Post("/{id}/create/{initialAmount}")
  public HttpResponse create(String id, int initialAmount) {
    log.info("creating wallet [{}] with balance [{}].", id, initialAmount);
    componentClient.forEventSourcedEntity(id)
      .method(WalletEntity::create)
      .invoke(initialAmount);
    return HttpResponses.ok();
  }

  @Post("/{id}/deposit/{amount}")
  public HttpResponse deposit(String id, int amount) {
    var walletResult = componentClient.forEventSourcedEntity(id)
      .method(WalletEntity::deposit)
      .invoke(new Deposit(UUID.randomUUID().toString(), amount));

    return switch (walletResult) {
      case Success __ -> HttpResponses.ok();
      case Failure failure -> {
        log.info("Failed to deposit [{}]", failure.errorMsg());
        yield HttpResponses.badRequest();
      }
    };
  }

  @Post("/{id}/withdraw/{amount}")
  public HttpResponse withdraw(String id, int amount) {
    var walletResult = componentClient.forEventSourcedEntity(id)
      .method(WalletEntity::withdraw)
      .invoke(new Withdraw(UUID.randomUUID().toString(), amount));

    return switch (walletResult) {
      case Success __ -> HttpResponses.ok();
      case Failure failure -> {
        log.info("Failed to withdraw [{}]", failure.errorMsg());
        yield HttpResponses.badRequest();
      }
    };
  }
}
