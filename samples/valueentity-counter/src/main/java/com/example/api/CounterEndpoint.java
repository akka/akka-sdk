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

import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/counter")
public class CounterEndpoint {

  private final ComponentClient componentClient;

  public CounterEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/{counterId}/increase")
  public CompletionStage<Number> increaseBy(String counterId, Number increaseBy) {
    return componentClient.forKeyValueEntity(counterId)
        .method(CounterEntity::increaseBy)
        .invokeAsync(increaseBy.value())
      .thenApply(Number::new);
  }

  @Put("/{counterId}/increase")
  public CompletionStage<Number> set(String counterId, Number increaseBy) {
    return componentClient.forKeyValueEntity(counterId)
      .method(CounterEntity::set)
      .invokeAsync(increaseBy.value())
      .thenApply(Number::new);
  }

  @Post("/{counterId}/plus-one")
  public CompletionStage<Number> plusOne(String counterId) {
    return componentClient.forKeyValueEntity(counterId)
      .method(CounterEntity::plusOne)
      .invokeAsync()
      .thenApply(Number::new);
  }

  @Get("/{counterId}")
  public CompletionStage<Number> get(String counterId) {
    return componentClient.forKeyValueEntity(counterId)
      .method(CounterEntity::get)
      .invokeAsync()
      .thenApply(Number::new);
  }

  @Delete("/{counterId}")
  public CompletionStage<HttpResponse> delete(String counterId) {
    return componentClient.forKeyValueEntity(counterId)
      .method(CounterEntity::delete)
      .invokeAsync()
      .thenApply(__ -> HttpResponses.ok());
  }
}