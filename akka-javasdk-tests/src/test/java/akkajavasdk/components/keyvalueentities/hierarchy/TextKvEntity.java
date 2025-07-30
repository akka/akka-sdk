/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.hierarchy;

import akka.javasdk.annotations.ComponentId;
import java.util.Optional;

@ComponentId("hierarchy-kv-entity")
public class TextKvEntity extends AbstractTextKvEntity {

  public Effect<String> setText(String text) {
    return effects().updateState(new State(text)).thenReply(text);
  }

  public Effect<Optional<String>> getText() {
    return effects().reply(Optional.ofNullable(currentState()).map(State::value));
  }
}
