/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.http;

import akka.javasdk.Retries;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.pattern.RetrySettings;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

@HttpEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class RetryEndpoint {

  private final Retries retries;
  private final ComponentClient componentClient;

  public RetryEndpoint(Retries retries, ComponentClient componentClient) {
    this.retries = retries;
    this.componentClient = componentClient;
  }

  @Post("/retry/{counterId}")
  public CompletionStage<Integer> useRetry(String counterId) {
    return componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::failedIncrease)
      .withRetry(RetrySettings.create(3).withFixedDelay(Duration.ofMillis(100)))
      .invokeAsync(111);
  }

  @Post("/async-utils/{counterId}")
  public CompletionStage<Integer> useAsyncUtilsRetry(String counterId) {
    return retries.retryAsync(() -> componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::failedIncrease)
      .invokeAsync(111), RetrySettings.create(3));
  }

  @Post("/failing/{counterId}")
  public CompletionStage<Integer> failingIncrease(String counterId) {
    return componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::failedIncrease)
      .invokeAsync(111);
  }
}
