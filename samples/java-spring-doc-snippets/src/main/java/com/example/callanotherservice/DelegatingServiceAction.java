package com.example.callanotherservice;

import kalix.javasdk.annotations.http.Endpoint;
import kalix.javasdk.annotations.http.Post;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.http.HttpClient;
import kalix.javasdk.http.HttpClientProvider;
import kalix.javasdk.http.StrictResponse;

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
