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
  public Integer plusOne(String counterId) {
    var result = componentClient.forKeyValueEntity(counterId)
        .method(CounterEntity::plusOne)
        .invoke();
    return result.value();
  }

  @Put("/{counterId}/set")
  public Integer set(String counterId, Counter increaseBy) {
    var result = componentClient.forKeyValueEntity(counterId)
        .method(CounterEntity::set)
        .invoke(increaseBy.value());
    return result.value();
  }

  // end::endpoint[]

  @Post("/{counterId}/increase")
  public Integer increaseBy(String counterId, int increaseBy) {
    var result = componentClient.forKeyValueEntity(counterId)
        .method(CounterEntity::increaseBy)
        .invoke(increaseBy);
    return result.value();
  }

  @Get("/{counterId}")
  public Integer get(String counterId) {
    var result = componentClient.forKeyValueEntity(counterId)
      .method(CounterEntity::get)
      .invoke();
    return result.value();
  }

  @Delete("/{counterId}")
  public HttpResponse delete(String counterId) {
    componentClient.forKeyValueEntity(counterId)
      .method(CounterEntity::delete)
      .invoke();
    return HttpResponses.ok();
  }

  // tag::endpoint[]
}
// end::endpoint[]
