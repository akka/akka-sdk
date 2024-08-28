package com.example.callanotherservice;

import akka.javasdk.annotations.http.Endpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.StrictResponse;

import java.util.concurrent.CompletionStage;

// tag::delegating-action[]
@Endpoint
public class DelegatingServiceAction {

  private final HttpClient httpClient;

  public DelegatingServiceAction(HttpClientProvider componentClient) { // <1>
    this.httpClient = componentClient.httpClientFor("counter");
  }

  @Post("/delegate/counter/{counter_id}/increase")
  public CompletionStage<Number> addAndReturn(String counterId, Number increaseBy) {
    var result =
        httpClient.POST("/counter/" + counterId + "/increase") // <3>
            .withRequestBody(increaseBy)
            .responseBodyAs(Number.class)
                .invokeAsync()
                .thenApply(StrictResponse::body);

    return result;  // <4>
  }
}
// end::delegating-action[]
