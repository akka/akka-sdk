/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.keyvalueentity;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testmodels.Done;

public class ValueEntitiesTestModels {

  @Component(id = "user")
  public static class InvalidValueEntityWithOverloadedCommandHandler extends KeyValueEntity<User> {
    public KeyValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }

    public KeyValueEntity.Effect<Done> createEntity(String user) {
      return effects().reply(Done.instance);
    }
  }

  public static class ValidKeyValueEntityWithNoArgCommandHandler extends KeyValueEntity<String> {
    public KeyValueEntity.Effect<String> execute() {
      return effects().reply("ok");
    }
  }

  public static class ValidKeyValueEntityWithOneArgCommandHandler extends KeyValueEntity<String> {
    public KeyValueEntity.Effect<String> execute(String command) {
      return effects().reply(command);
    }
  }

  public static class InvalidKeyValueEntityWithTwoArgCommandHandler extends KeyValueEntity<String> {
    public KeyValueEntity.Effect<String> execute(String cmd, int i) {
      return effects().reply(cmd);
    }
  }

  public static class InvalidKeyValueEntityWithDuplicateHandlers extends KeyValueEntity<String> {
    public KeyValueEntity.Effect<String> execute(String cmd) {
      return effects().reply(cmd);
    }

    public KeyValueEntity.Effect<String> execute(Integer cmd) {
      return effects().reply(cmd.toString());
    }
  }

  public static class KeyValueEntityWithNoEffectMethod extends KeyValueEntity<String> {
    public String execute() {
      return "ok";
    }
  }

  // Test models for @FunctionTool validation

  @Component(id = "entity_with_function_tool_on_non_effect")
  public static class KeyValueEntityWithFunctionToolOnNonEffectMethod
      extends KeyValueEntity<String> {

    public KeyValueEntity.Effect<String> command(String cmd) {
      return effects().reply(cmd);
    }

    @FunctionTool(description = "Helper method")
    public String helperMethod() {
      return "helper";
    }
  }

  @Component(id = "entity_with_valid_function_tool")
  public static class KeyValueEntityWithValidFunctionTool extends KeyValueEntity<String> {

    @FunctionTool(description = "Execute command")
    public KeyValueEntity.Effect<String> command(String cmd) {
      return effects().reply(cmd);
    }
  }

  @Component(id = "kve_with_function_tool_on_readonly_effect")
  public static class KeyValueEntityWithFunctionToolOnReadOnlyEffect
      extends KeyValueEntity<String> {

    public KeyValueEntity.Effect<String> command(String cmd) {
      return effects().reply(cmd);
    }

    @FunctionTool(description = "Get state")
    public KeyValueEntity.ReadOnlyEffect<String> getState() {
      return effects().reply(currentState());
    }
  }
}
