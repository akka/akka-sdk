/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-multiple-workflow-deletes")
@Consume.FromWorkflow(SimpleWorkflow.class)
public class ConsumerWithMultipleDeleteHandlersWorkflow extends Consumer {

  @DeleteHandler
  public Effect onDelete1() {
    return effects().done();
  }

  @DeleteHandler
  public Effect onDelete2() {
    return effects().done();
  }
}
