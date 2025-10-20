package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.workflow.Workflow;

@Component(id = "consumer-valid-workflow")
@Consume.FromWorkflow(SimpleWorkflow.class)
public class ValidConsumerWithWorkflowSubscription extends Consumer {

  public Effect onUpdate(String state) {
    return effects().done();
  }

  public static class MyWorkflow extends Workflow<String> {}
}
