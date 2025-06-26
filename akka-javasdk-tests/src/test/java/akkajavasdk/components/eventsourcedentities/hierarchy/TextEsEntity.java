/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.hierarchy;

import akka.javasdk.annotations.ComponentId;

import java.util.Optional;

@ComponentId("hierarchy-es-entity")
public class TextEsEntity extends AbstractTextEsEntity<TextEsEntity.Event> {
  sealed interface Event {}
  record TextSet(String value) implements Event {}

  public Effect<String> setText(String text) {
    return effects().persist(new TextSet(text)).thenReply(__ -> text);
  }

  public ReadOnlyEffect<Optional<String>> getText() {
    return effects().reply(Optional.ofNullable(currentState()).map(State::value));
  }

  @Override
  public State applyEvent(Event event) {
    return switch (event) {
      case TextSet set -> new State(set.value);
    };
  }
}
