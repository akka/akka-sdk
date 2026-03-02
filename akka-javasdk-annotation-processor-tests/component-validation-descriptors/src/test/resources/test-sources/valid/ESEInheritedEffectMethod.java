/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

class MyEventSourcedEntity extends EventSourcedEntity<String, MyEventSourcedEntity.Event> {

  public sealed interface Event {
    record Created(String name) implements Event {}
  }

  public Effect<String> sampleCommand(String data) {
    return effects().reply("Ok");
  }

  public String applyEvent(com.example.MyEventSourcedEntity.Event event) {
    return "";
  }
}

@Component(id = "no-effect-method-entity")
public class ESEInheritedEffectMethod extends MyEventSourcedEntity {

}