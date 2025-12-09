package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "my-component")
public class MyComponent extends KeyValueEntity<String> {

  public Effect<String> getState() {
    return effects().reply(currentState());
  }
}
