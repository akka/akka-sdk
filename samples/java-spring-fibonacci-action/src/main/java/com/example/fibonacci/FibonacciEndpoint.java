package com.example.fibonacci;

import akka.http.javadsl.model.HttpResponse;
import kalix.javasdk.annotations.http.Endpoint;
import kalix.javasdk.annotations.http.Get;
import kalix.javasdk.annotations.http.Post;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Endpoint("/limitedfibonacci")
public class FibonacciEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(FibonacciEndpoint.class);
    private ComponentClient componentClient;

    public FibonacciEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }
    // end::injecting-component-client[]

    @Get("/{number}/next")
    public CompletionStage<HttpResponse> nextNumberPath(Long number) {
        if (number < 0 || number > 10000) {
            return CompletableFuture.completedStage(
              HttpResponses.badRequest("Only numbers between 0 and 10k are allowed"));
        } else {
            logger.info("Executing GET call to real /fibonacci = " + number);
            CompletionStage<Number> numberResult = componentClient.forAction()
              .method(FibonacciAction::getNumber)
            // FIXME no longer forward as documented
              .invokeAsync(number);
            return numberResult.thenApply(HttpResponses::ok);
        }
    }

    @Post("/next")
    public CompletionStage<HttpResponse> nextNumber(Number number) {

        if (number.value() < 0 || number.value() > 10000) {
            return CompletableFuture.completedStage(
              HttpResponses.badRequest("Only numbers between 0 and 10k are allowed"));
        } else {
            logger.info("Executing POST call to real /fibonacci = " + number.value());

            var nextNumberReply =
              componentClient.forAction()
                .method(FibonacciAction::nextNumber)
                .invokeAsync(number);

            return nextNumberReply.thenApply(HttpResponses::ok);
        }
    }
}
