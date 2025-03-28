/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.http;

import akka.javasdk.AsyncUtils;
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

  private final AsyncUtils asyncUtils;
  private final ComponentClient componentClient;

  public RetryEndpoint(AsyncUtils asyncUtils, ComponentClient componentClient) {
    this.asyncUtils = asyncUtils;
    this.componentClient = componentClient;
  }

  @Post("/retry/{counterId}")
  public CompletionStage<Integer> useRetry(String counterId) {
    return componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::failedIncrease)
      .withRetry(RetrySettings.attempts(3).withFixedDelay(Duration.ofMillis(100)))
      .invokeAsync(111);
  }

  @Post("/async-utils/{counterId}")
  public CompletionStage<Integer> useAsyncUtilsRetry(String counterId) {
    return asyncUtils.retry(() -> componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::failedIncrease)
      .invokeAsync(111), RetrySettings.attempts(3).withBackoff());
  }

  @Post("/failing/{counterId}")
  public CompletionStage<Integer> failingIncrease(String counterId) {
    return componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::failedIncrease)
      .invokeAsync(111);
  }
}
