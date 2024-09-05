package ${package}.hello;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

import java.util.concurrent.CompletionStage;
import static java.util.concurrent.CompletableFuture.completedStage;

/**
 * This is a simple Akka Endpoint that returns "Hello World!".
 * Locally, you can access it by running `curl http://localhost:9000/hello`.
 */
@HttpEndpoint("/hello")
public class HelloWorldEndpoint {

  @Get("/")
  public CompletionStage<String> helloWorld() {
    return completedStage("Hello World!");
  }
}
