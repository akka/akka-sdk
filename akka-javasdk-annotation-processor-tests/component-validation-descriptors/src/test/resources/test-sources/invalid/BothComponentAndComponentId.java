package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "component-id")
@ComponentId("deprecated-id")
public class BothComponentAndComponentId extends KeyValueEntity<String> {
  // This component has both @Component and @ComponentId and should fail validation
}
