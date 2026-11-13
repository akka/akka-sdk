/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracecontext;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "trace-context-es-entity")
public class TraceContextEsEntity extends EventSourcedEntity<String, TraceContextEsEntity.Event> {

  public sealed interface Event {
    record Observed() implements Event {}
  }

  public Effect<SpanIds> observe() {
    return effects().reply(SpanIds.observe(commandContext().tracing()));
  }

  @Override
  public String applyEvent(Event event) {
    return currentState();
  }
}
