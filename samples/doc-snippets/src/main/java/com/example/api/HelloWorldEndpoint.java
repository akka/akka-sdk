package com.example.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.application.HelloWorldAgent;

import java.util.UUID;

// tag::helloWorld[]
// tag::helloRequest[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/")
public class HelloWorldEndpoint {
  // end::helloWorld[]
  public record Request(String greeting, String sessionId) {}
  // tag::helloWorld[]

  private final ComponentClient componentClient;

  public HelloWorldEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // end::helloRequest[]
  @Get("/hello")
  public String helloWorld() {
    return componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(HelloWorldAgent::greet)
        .invoke("Hello World!");
  }
  // tag::helloRequest[]

  // end::helloWorld[]
  @Post("/hello")
  public String hello(Request request) {
    return componentClient
        .forAgent()
        .inSession(request.sessionId)
        .method(HelloWorldAgent::greet)
        .invoke(request.greeting);
  }
  // tag::helloWorld[]
}
// end::helloRequest[]
// end::helloWorld[]
