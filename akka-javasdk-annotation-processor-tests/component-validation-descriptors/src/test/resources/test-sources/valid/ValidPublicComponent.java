package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "valid-public-component")
public class ValidPublicComponent extends KeyValueEntity<String> {
  // This component is public and should pass validation
  public Effect execute(String command) {
    return effects().reply(command);
  }
}
