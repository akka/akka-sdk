package com.example.wallet.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.http.Endpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.wallet.application.WalletEntity;
import com.example.wallet.domain.WalletCmd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@Endpoint("/wallet")
public class WalletEndpoint {

    private static final Logger log = LoggerFactory.getLogger(WalletEndpoint.class);

    private final ComponentClient componentClient;

    public WalletEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Get("/{id}")
    public CompletionStage<String> get(String id){
        log.info("Get wallet with id [{}].", id);
        return componentClient.forEventSourcedEntity(id)
                .method(WalletEntity::get).invokeAsync()
                .thenApply(balance -> "The balance is ["+ balance +"].");
    }

    @Post("/{id}/create")
    public CompletionStage<HttpResponse> create(String id, WalletCmd.CreateCmd cmd){
        log.info("creating wallet [{}] with balance [{}].", id, cmd.initialAmount());
        return componentClient.forEventSourcedEntity(id)
                .method(WalletEntity::create).invokeAsync(cmd)
                .thenApply(done -> HttpResponses.ok());
    }

    @Post("/{id}/deposit}")
    public CompletionStage<HttpResponse> deposit(String id, WalletCmd.DepositCmd cmd){
        log.info("adding [{}] to wallet [{}].", cmd.amount(), id);
        return componentClient.forEventSourcedEntity(id)
                .method(WalletEntity::deposit).invokeAsync(cmd)
                .thenApply(done -> HttpResponses.ok());
    }

    @Post("/{id}/withdraw")
    public CompletionStage<HttpResponse> withdraw(String id, WalletCmd.WithdrawCmd cmd){
        log.info("withdrawing [{}] from wallet [{}].", cmd.amount(), id);
        return componentClient.forEventSourcedEntity(id)
                .method(WalletEntity::withdraw).invokeAsync(cmd)
                .thenApply(done -> HttpResponses.ok());
    }
}
