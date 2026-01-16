package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Flow;
import com.example.application.StreamingHelloWorldAgent;

// tag::class[]

/**
 * This is a simple Akka Endpoint that uses an agent and LLM to generate
 * greetings in different languages. An HTTP client connects a websocket with a username
 * in the path and then sends individual requests over the socket to get the response
 * streamed from the agent.
 */
// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered,
// and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class HelloWorldWebSocketEndpoint extends AbstractHttpEndpoint {

  private final ComponentClient componentClient;

  public HelloWorldWebSocketEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // end::class[]
  // Note: using websocket in a deployed service requires additional steps, see the documentation
  // tag::class[]
  @Get("/hello-websocket/{user}")
  public HttpResponse hello(String user) {
    return HttpResponses.textWebsocket(
      requestContext(), // <1>
      Flow.of(String.class).flatMapConcat(requestText -> // <2>
        componentClient
          .forAgent()
          .inSession(user)
          .tokenStream(StreamingHelloWorldAgent::greet)
          .source(requestText))
    );
  }
}
// end::class[]
