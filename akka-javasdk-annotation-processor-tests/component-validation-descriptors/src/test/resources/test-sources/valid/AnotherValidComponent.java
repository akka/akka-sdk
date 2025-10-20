package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "another-valid-component", name = "Another Valid Component", description = "A test component")
public class AnotherValidComponent extends EventSourcedEntity<String, Object> {
  // This component is public and should pass validation

  @Override
  public String applyEvent(Object event) {
    return "";
  }
}
