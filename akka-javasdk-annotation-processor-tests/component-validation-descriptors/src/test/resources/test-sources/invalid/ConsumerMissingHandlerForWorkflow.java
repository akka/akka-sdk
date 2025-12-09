package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-missing-workflow-handler")
@Consume.FromWorkflow(SimpleWorkflow.class)
public class ConsumerMissingHandlerForWorkflow extends Consumer {

  // Wrong handler - should accept String (the state type), not Integer
  public Effect wrongHandler(Integer msg) {
    return effects().done();
  }
}