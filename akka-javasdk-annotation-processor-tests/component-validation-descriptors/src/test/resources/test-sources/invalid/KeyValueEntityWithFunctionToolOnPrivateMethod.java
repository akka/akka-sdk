/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "kv-entity-with-function-tool-on-private-method")
public class KeyValueEntityWithFunctionToolOnPrivateMethod extends KeyValueEntity<String> {

  public Effect update(String value) {
    return effects().updateState(value).reply("updated");
  }

  // @FunctionTool is not allowed on private methods
  @FunctionTool(description = "This should not be allowed on private methods")
  private Effect privateMethod() {
    return effects().reply("private");
  }
}
