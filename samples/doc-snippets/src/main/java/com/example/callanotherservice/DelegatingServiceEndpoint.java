package com.example.callanotherservice;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.StrictResponse;

import java.util.concurrent.CompletionStage;

// Allow all other Akka services deployed in the same project to access the components of this
// Akka service, but disallow access from the internet.
// Documentation at https://doc.akka.io/java/access-control.html
// tag::delegating-endpoint[]
@Acl(allow = @Acl.Matcher(service = "*"))
@HttpEndpoint
public class DelegatingServiceEndpoint {

  private final HttpClient httpClient;

  public DelegatingServiceEndpoint(HttpClientProvider componentClient) { // <1>
    this.httpClient = componentClient.httpClientFor("counter"); // <2>
  }

  // model for the JSON we accept
  record IncreaseRequest(int increaseBy) {}

  // model for the JSON the upstream service responds with
  record Counter(int value) {}

  @Post("/delegate/counter/{counter_id}/increase")
  public CompletionStage<String> addAndReturn(String counterId, IncreaseRequest request) {
    CompletionStage<String> result =
        httpClient.POST("/counter/" + counterId + "/increase") // <3>
            .withRequestBody(request)
            .responseBodyAs(Counter.class)
            .invokeAsync() // <4>
            .thenApply(response -> { // <5>
              if (response.status().isSuccess()) {
                return "New counter vaue: " + response.body().value;
              } else {
                throw new RuntimeException("Counter returned unexpected status: " + response.status());
              }
            });

    return result;
  }
}
// end::delegating-endpoint[]
