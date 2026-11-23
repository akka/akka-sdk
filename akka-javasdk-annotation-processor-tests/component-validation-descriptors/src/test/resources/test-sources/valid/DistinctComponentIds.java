package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

public class DistinctComponentIds {

  @Component(id = "first-id")
  public static class First extends KeyValueEntity<String> {
    public Effect<String> execute(String command) {
      return effects().reply(command);
    }
  }

  @Component(id = "second-id")
  public static class Second extends KeyValueEntity<String> {
    public Effect<String> execute(String command) {
      return effects().reply(command);
    }
  }
}
