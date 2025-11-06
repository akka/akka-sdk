/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "valid-kv-entity-with-function-tool")
public class ValidKeyValueEntityWithFunctionTool extends KeyValueEntity<String> {

  @FunctionTool(description = "This is allowed on Effect")
  public Effect update(String value) {
    return effects().updateState(value).reply("updated");
  }

  @FunctionTool(description = "This is allowed on ReadOnlyEffect")
  public ReadOnlyEffect query() {
    return effects().reply("query result");
  }
}
