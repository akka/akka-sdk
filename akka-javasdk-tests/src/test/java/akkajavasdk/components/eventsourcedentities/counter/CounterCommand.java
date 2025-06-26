/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.counter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CounterCommand.Increase.class, name = "I"),
  @JsonSubTypes.Type(value = CounterCommand.Set.class, name = "S")})
public sealed interface CounterCommand {

  record Increase(int value) implements CounterCommand {
  }

  record Set(int value) implements CounterCommand {
  }
}
