package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

public class TripleDuplicateComponentIds {

  @Component(id = "triple-id")
  public static class A extends KeyValueEntity<String> {
    public Effect<String> execute(String command) {
      return effects().reply(command);
    }
  }

  @Component(id = "triple-id")
  public static class B extends KeyValueEntity<String> {
    public Effect<String> execute(String command) {
      return effects().reply(command);
    }
  }

  @Component(id = "triple-id")
  public static class C extends KeyValueEntity<String> {
    public Effect<String> execute(String command) {
      return effects().reply(command);
    }
  }
}
