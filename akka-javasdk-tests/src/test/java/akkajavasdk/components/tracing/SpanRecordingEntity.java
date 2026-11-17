/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracing;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "span-recording-entity")
public class SpanRecordingEntity extends EventSourcedEntity<Integer, SpanRecordingEntity.Event> {

  public sealed interface Event {
    @TypeName("span-recorded")
    record Recorded(int count) implements Event {}
  }

  @Override
  public Integer emptyState() {
    return 0;
  }

  public Effect<Done> record() {
    SpanRecorder.record("entity:" + commandContext().entityId());
    return effects().persist(new Event.Recorded(currentState() + 1)).thenReply(__ -> Done.done());
  }

  @Override
  public Integer applyEvent(Event event) {
    return switch (event) {
      case Event.Recorded recorded -> recorded.count();
    };
  }
}
