/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.counter;

import akka.Done;
import akka.javasdk.Metadata;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import java.util.concurrent.CompletionStage;

@Component(id = "increase-action")
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
    String entityId = messageContext().eventSubject().get();
    if (event.value() == 42) {
      Metadata metadata;
      if (messageContext().metadata().has(CounterEntity.META_KEY)) {
        metadata =
            Metadata.EMPTY.add(
                CounterEntity.META_KEY,
                "magic 42 with metadata from IncreaseConsumer: "
                    + messageContext().metadata().getLast(CounterEntity.META_KEY).get());
      } else {
        metadata = Metadata.EMPTY;
      }

      CompletionStage<Done> res =
          componentClient
              .forEventSourcedEntity(entityId)
              .method(CounterEntity::increase)
              .withMetadata(metadata)
              .invokeAsync(1)
              .thenApply(__ -> Done.getInstance());
      return effects().asyncDone(res);
    }
    return effects().done();
  }
}
