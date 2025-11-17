/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-with-function-tool-on-private-method")
@Consume.FromTopic("my-topic")
public class ConsumerWithFunctionToolOnPrivateMethod extends Consumer {

  public Effect onEvent(String event) {
    return effects().done();
  }

  // @FunctionTool is not allowed on private methods
  @FunctionTool(description = "This should not be allowed on private methods")
  private Effect privateMethod() {
    return effects().done();
  }
}
