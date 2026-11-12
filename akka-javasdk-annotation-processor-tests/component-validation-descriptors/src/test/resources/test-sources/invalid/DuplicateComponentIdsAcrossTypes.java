package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.timedaction.TimedAction;

public class DuplicateComponentIdsAcrossTypes {

  @Component(id = "shared-id")
  public static class Entity extends KeyValueEntity<String> {
    public Effect<String> execute(String command) {
      return effects().reply(command);
    }
  }

  @Component(id = "shared-id")
  public static class Action extends TimedAction {
    public Effect execute() {
      return effects().done();
    }
  }
}
