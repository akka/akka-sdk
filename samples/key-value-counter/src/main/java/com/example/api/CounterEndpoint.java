package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.application.CounterEntity;
import com.example.domain.Counter;

import java.util.concurrent.CompletionStage;

// tag::endpoint[]

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/counter")
public class CounterEndpoint {

  private final ComponentClient componentClient;

  public CounterEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/{counterId}/plus-one")
  public CompletionStage<Integer> plusOne(String counterId) {
    return componentClient.forKeyValueEntity(counterId)
        .method(CounterEntity::plusOne)
        .invokeAsync()
        .thenApply(Counter::value);
  }

  @Put("/{counterId}/set")
  public CompletionStage<Integer> set(String counterId, Counter increaseBy) {
    return componentClient.forKeyValueEntity(counterId)
        .method(CounterEntity::set)
        .invokeAsync(increaseBy.value())
        .thenApply(Counter::value);
  }

  // end::endpoint[]

  @Post("/{counterId}/increase?test={test}")
  public CompletionStage<Integer> increaseBy(String counterId, int increaseBy) {
    return componentClient.forKeyValueEntity(counterId)
        .method(CounterEntity::increaseBy)
        .invokeAsync(increaseBy)
        .thenApply(Counter::value);
  }

  @Get("/{counterId}")
  public CompletionStage<Integer> get(String counterId) {
    return componentClient.forKeyValueEntity(counterId)
      .method(CounterEntity::get)
      .invokeAsync()
      .thenApply(Counter::value);
  }

  @Delete("/{counterId}")
  public CompletionStage<HttpResponse> delete(String counterId) {
    return componentClient.forKeyValueEntity(counterId)
      .method(CounterEntity::delete)
      .invokeAsync()
      .thenApply(__ -> HttpResponses.ok());
  }

  // tag::endpoint[]
}
// end::endpoint[]
