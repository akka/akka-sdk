package com.example.callanotherservice;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.StrictResponse;

import java.util.concurrent.CompletionStage;

// tag::delegating-action[]
// Allow all other Kalix services deployed in the same project to access the components of this
// Kalix service, but disallow access from the internet.
// Documentation at https://docs.kalix.io/java/access-control.html
@Acl(allow = @Acl.Matcher(service = "*"))
@HttpEndpoint
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
