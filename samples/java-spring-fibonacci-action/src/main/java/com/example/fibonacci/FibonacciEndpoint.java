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
            return numberResult.thenApply(HttpResponses::ok)
                    // FIXME right now any error code from the component error effect becomes a runtime exception
                    //       before handing it back to us, we should see that here more easily.
                    //       (and be able to choose if we want to propagate that maybe, but whose responsibility is it
                    //       to validate input, and is it really safe and makes sense to propagate it right back,
                    //       the invalid input could be crafted here and caller has no idea)
                    //       For now just turn any error message to bad request.
                    .exceptionally(ex -> HttpResponses.badRequest(ex.getMessage()));
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
