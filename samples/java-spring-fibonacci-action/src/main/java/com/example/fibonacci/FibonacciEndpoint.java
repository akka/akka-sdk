package com.example.fibonacci;

import kalix.javasdk.annotations.http.Endpoint;
import kalix.javasdk.annotations.http.Get;
import kalix.javasdk.annotations.http.Post;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@Endpoint("/limitedfibonacci")
public class FibonacciEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(FibonacciEndpoint.class);
    // tag::injecting-component-client[]
    private ComponentClient componentClient; // <1>

    public FibonacciEndpoint(ComponentClient componentClient) { // <2>
        this.componentClient = componentClient; // <3>
    }
    // end::injecting-component-client[]

    @Get("/{number}/next")
    public CompletionStage<Number> nextNumberPath(Long number) {
        if (number < 0 || number > 10000) {
            throw new IllegalArgumentException("Only numbers between 0 and 10k are allowed");
        } else {
            logger.info("Executing GET call to real /fibonacci = " + number);
            // tag::component-client[]
            CompletionStage<Number> numberResult = componentClient.forAction() // <1>
              .method(FibonacciAction::getNumber) // <2>
            // FIXME no longer forward as documented
              .invokeAsync(number); // <3>
            return numberResult;
            // end::component-client[]
        }
    }

    @Post("/next")
    public CompletionStage<Number> nextNumber(Number number) {
        if (number.value() < 0 || number.value() > 10000) {
            throw new IllegalArgumentException("Only numbers between 0 and 10k are allowed");
        } else {
            logger.info("Executing POST call to real /fibonacci = " + number.value());
            var nextNumberReply =
              componentClient.forAction()
                .method(FibonacciAction::nextNumber)
                .invokeAsync(number);

            return nextNumberReply;
        }
    }
}
