/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-workflow-delete-with-params")
@Consume.FromWorkflow(SimpleWorkflow.class)
public class ConsumerWithDeleteHandlerWithParamsWorkflow extends Consumer {

  @DeleteHandler
  public Effect onDelete(String wrongParam) {
    return effects().done();
  }
}
