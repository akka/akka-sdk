/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "kv-entity-with-function-tool-on-invalid-method")
public class KeyValueEntityWithFunctionToolOnInvalidMethod extends KeyValueEntity<String> {

  public Effect<String> update(String value) {
    return effects().updateState(value).thenReply(__ -> "updated");
  }

  // @FunctionTool is not allowed on methods that don't return Effect or ReadOnlyEffect
  @FunctionTool(description = "This should not be allowed on non-Effect methods")
  public String invalidMethod() {
    return "invalid";
  }
}
