package ${package}.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

import java.util.concurrent.CompletionStage;
import static java.util.concurrent.CompletableFuture.completedStage;

/**
 * This is a simple Akka Endpoint that returns "Hello World!".
 * Locally, you can access it by running `curl http://localhost:9000/hello`.
 */
// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/hello")
public class HelloWorldEndpoint {

  @Get("/")
  public CompletionStage<String> helloWorld() {
    return completedStage("Hello World!");
  }
}
