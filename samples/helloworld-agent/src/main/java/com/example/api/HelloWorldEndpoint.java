package com.example.api;

import akka.javasdk.annotations.http.Post;
import com.example.application.HelloWorldAgent;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.client.ComponentClient;

import java.util.UUID;

// tag::class[]
/**
 * This is a simple Akka Endpoint that uses an agent and LLM to generate
 * greetings in different languages.
 */
// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint()
public class HelloWorldEndpoint {
  public record Request(String user, String text) {}

  private final ComponentClient componentClient;

  public HelloWorldEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/hello")
  public String hello(Request request) {
    return componentClient
        .forAgent()
        .inSession(request.user)
        .method(HelloWorldAgent::greet)
        .invoke(request.text);
  }
}
// end::class[]
