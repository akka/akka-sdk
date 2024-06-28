package com.example.callanotherservice;

import kalix.javasdk.annotations.http.Endpoint;
import kalix.javasdk.annotations.http.Post;
import kalix.javasdk.client.ComponentClient;

import java.util.concurrent.CompletionStage;

// tag::delegating-action[]
@Endpoint
public class DelegatingServiceAction {

  private final ComponentClient componentClient;

  public DelegatingServiceAction(ComponentClient componentClient) { // <1>
    this.componentClient = componentClient;
  }

  @Post("/delegate/counter/{counter_id}/increase")
  public CompletionStage<Number> addAndReturn(String counterId, Number increaseBy) {
    throw new UnsupportedOperationException("FIXME");
    // FIXME Not sure what component this was intended to call
    /*
    var result =
        componentClient. ???
            .post().uri("/counter/" + counterId + "/increase") // <3>
            .bodyValue(increaseBy)
            .retrieve()
            .bodyToMono(Number.class).toFuture();

    return result;  // <4>
     */
  }
}
// end::delegating-action[]
