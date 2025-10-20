package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "another-valid-component", name = "Another Valid Component", description = "A test component")
public class AnotherValidComponent extends EventSourcedEntity<String, AnotherValidComponent.Event> {
  // This component is public and should pass validation

  public sealed interface Event {
    record SampleEvent(String data) implements Event {}
  }

  public Effect sampleCommand(String data) {
    return effects().persist(new Event.SampleEvent(data));
  }

  @Override
  public String applyEvent(Event event) {
    return "";
  }
}
