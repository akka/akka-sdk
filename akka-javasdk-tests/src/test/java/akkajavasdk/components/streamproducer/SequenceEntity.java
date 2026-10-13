/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.streamproducer;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "stream-producer-sequence-entity")
public class SequenceEntity extends EventSourcedEntity<Integer, SequenceEntity.Event> {

  public sealed interface Event {}

  public record NumberAdded(int number) implements Event {}

  public Effect<Done> add(int number) {
    return effects().persist(new NumberAdded(number)).thenReply(__ -> Done.getInstance());
  }

  @Override
  public Integer applyEvent(Event event) {
    return switch (event) {
      case NumberAdded added -> added.number();
    };
  }
}
