/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-with-function-tool")
@Consume.FromTopic("my-topic")
public class ConsumerWithFunctionTool extends Consumer {

  @FunctionTool(description = "This should not be allowed")
  public Effect onEvent(String event) {
    return effects().done();
  }

}
