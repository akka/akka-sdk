package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "package-private-component")
class PackagePrivateComponent extends EventSourcedEntity<String, Object> {
  // This component has package-private access and should fail validation

  @Override
  public String applyEvent(Object event) {
    return "";
  }
}
