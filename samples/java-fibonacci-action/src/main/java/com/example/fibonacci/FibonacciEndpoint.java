package com.example.fibonacci;

import akka.http.javadsl.model.HttpResponse;
import akka.platform.javasdk.annotations.http.Endpoint;
import akka.platform.javasdk.annotations.http.Get;
import akka.platform.javasdk.annotations.http.Post;
import akka.platform.javasdk.client.ComponentClient;
import akka.platform.javasdk.http.HttpResponses;
import com.example.MyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedStage;

@Endpoint("/fibonacci")
public class FibonacciEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(FibonacciEndpoint.class);
    private ComponentClient componentClient;
    private MyContext myContext;

    public FibonacciEndpoint(ComponentClient componentClient, MyContext myContext) {
        this.componentClient = componentClient;
        this.myContext = myContext;
    }
    // end::injecting-component-client[]

    @Get("/{number}/next")
    public CompletionStage<HttpResponse> nextNumberPath(Long number) {
        if (!myContext.requestValidator().isValid(number)) {
            return completedStage(
              HttpResponses.badRequest("Only numbers between 0 and 10k are allowed"));
        } else {
            logger.info("Executing GET call to real /fibonacci = " + number);
            return completedStage(HttpResponses.ok(Fibonacci.nextFib(number)))
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
            return completedStage(
              HttpResponses.badRequest("Only numbers between 0 and 10k are allowed"));
        } else {
            logger.info("Executing POST call to real /fibonacci = " + number.value());
            return completedStage(HttpResponses.ok(Fibonacci.nextFib(number.value())));
        }
    }
}
