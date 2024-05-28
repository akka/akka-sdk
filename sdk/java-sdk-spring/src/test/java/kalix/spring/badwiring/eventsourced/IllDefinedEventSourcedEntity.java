/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.spring.badwiring.eventsourced;

import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.annotations.TypeId;
import org.springframework.stereotype.Component;

@TypeId("test")
@Component
public class IllDefinedEventSourcedEntity extends EventSourcedEntity<String, IllDefinedEventSourcedEntity.Event> {

    public sealed interface Event {
        public record Created()implements Event{};
    }

    @Override
    public String applyEvent(Event event) {
        return currentState();
    }
}
