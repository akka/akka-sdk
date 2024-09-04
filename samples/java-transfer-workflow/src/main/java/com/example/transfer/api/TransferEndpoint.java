package com.example.transfer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.http.Endpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.transfer.application.TransferWorkflow;
import com.example.transfer.domain.TransferState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@Endpoint("/transfer")
public class TransferEndpoint {

    private static final Logger log = LoggerFactory.getLogger(TransferEndpoint.class);

    private final ComponentClient componentClient;

    public TransferEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Get("/{id}")
    public CompletionStage<String> get(String id){
        log.info("Get transfer with id [{}].", id);
        return componentClient.forWorkflow(id)
                .method(TransferWorkflow::getTransferState).invokeAsync()
                .thenApply( transferState -> transferState.status().toString());
    }

    @Post("/{id}")
    public CompletionStage<HttpResponse> transfer(String id, TransferState.Transfer transfer){
        log.info("Transfer received [{}].", transfer.toString());
        return componentClient.forWorkflow(id)
                .method(TransferWorkflow::startTransfer).invokeAsync(transfer)
                .thenApply(msg -> HttpResponses.accepted());
    }
}
