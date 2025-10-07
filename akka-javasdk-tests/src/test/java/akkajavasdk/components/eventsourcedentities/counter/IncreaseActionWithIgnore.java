/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.counter;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import java.util.concurrent.CompletionStage;

@Component(id = "increase-action-with-ignore")
@Consume.FromEventSourcedEntity(value = CounterEntity.class, ignoreUnknown = true)
public class IncreaseActionWithIgnore extends Consumer {

  private ComponentClient componentClient;

  public IncreaseActionWithIgnore(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect oneShallPass(CounterEvent.ValueIncreased event) {
    String entityId = this.messageContext().metadata().asCloudEvent().subject().get();
    if (event.value() == 1234) {
      CompletionStage<Done> res =
          componentClient
              .forEventSourcedEntity(entityId)
              .method(CounterEntity::increase)
              .invokeAsync(1)
              .thenApply(__ -> Done.done());
      return effects().asyncDone(res);
    }
    return effects().done();
  }
}
