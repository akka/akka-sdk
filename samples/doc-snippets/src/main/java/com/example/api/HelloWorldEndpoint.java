package com.example.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.application.HelloWorldAgent;

import java.util.UUID;

// tag::helloWorld[]
// tag::helloUser[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/")
public class HelloWorldEndpoint {

  private final ComponentClient componentClient;

  public HelloWorldEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // end::helloUser[]
  @Get("/hello")
  public String helloWorld() {
    return componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(HelloWorldAgent::greet)
        .invoke("Hello World!");
  }
  // tag::helloUser[]
  // end::helloWorld[]
  public record Request(String user, String text) {}

  @Post("/hello")
  public String helloUser(Request req) {
    return componentClient
        .forAgent()
        .inSession(req.user)
        .method(HelloWorldAgent::greet)
        .invoke(req.text);
  }
  // tag::helloWorld[]
}
// end::helloUser[]
// end::helloWorld[]
