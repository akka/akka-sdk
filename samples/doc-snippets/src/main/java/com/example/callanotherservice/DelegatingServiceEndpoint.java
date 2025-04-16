package com.example.callanotherservice;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;

import java.util.concurrent.CompletionStage;

// Allow all other Akka services deployed in the same project to access the components of this
// Akka service, but disallow access from the internet.
// Documentation at https://doc.akka.io/java/access-control.html
// tag::delegating-endpoint[]
@Acl(allow = @Acl.Matcher(service = "*"))
@HttpEndpoint
public class DelegatingServiceEndpoint {

  private final HttpClient httpClient;

  public DelegatingServiceEndpoint(HttpClientProvider httpClient) { // <1>
    this.httpClient = httpClient.httpClientFor("counter"); // <2>
  }

  // model for the JSON we accept
  record IncreaseRequest(int increaseBy) {}

  // model for the JSON the upstream service responds with
  record Counter(int value) {}

  @Post("/delegate/counter/{counterId}/increase")
  public String addAndReturn(String counterId, IncreaseRequest request) {
    var response = httpClient.POST("/counter/" + counterId + "/increase") // <3>
    .withRequestBody(request)
    .responseBodyAs(Counter.class)
    .invoke(); // <4>

    if (response.status().isSuccess()) { // <5>
      return "New counter vaue: " + response.body().value;
    } else {
      throw new RuntimeException("Counter returned unexpected status: " + response.status());
    }
  }
}
// end::delegating-endpoint[]
