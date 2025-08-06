/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.counter;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import java.util.concurrent.CompletionStage;

@ComponentId("increase-action")
@Consume.FromEventSourcedEntity(value = CounterEntity.class)
public class IncreaseConsumer extends Consumer {

  private ComponentClient componentClient;

  public IncreaseConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect printMultiply(CounterEvent.ValueMultiplied event) {
    return effects().done();
  }

  public Effect printSet(CounterEvent.ValueSet event) {
    return effects().done();
  }

  public Effect printIncrease(CounterEvent.ValueIncreased event) {
    String entityId = this.messageContext().metadata().asCloudEvent().subject().get();
    if (event.value() == 42) {
      CompletionStage<Done> res =
          componentClient
              .forEventSourcedEntity(entityId)
              .method(CounterEntity::increase)
              .invokeAsync(1)
              .thenApply(__ -> Done.getInstance());
      return effects().asyncDone(res);
    }
    return effects().done();
  }
}
