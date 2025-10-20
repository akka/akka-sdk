/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-multiple-workflow-updates")
@Consume.FromWorkflow(SimpleWorkflow.class)
public class ConsumerWithMultipleUpdateMethodsWorkflow extends Consumer {

  public Effect onUpdate1(String state) {
    return effects().done();
  }

  public Effect onUpdate2(String state) {
    return effects().done();
  }
}
