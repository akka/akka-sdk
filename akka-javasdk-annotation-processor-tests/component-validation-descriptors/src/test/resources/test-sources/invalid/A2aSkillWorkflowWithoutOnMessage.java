/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;
import akka.javasdk.workflow.Workflow;

@Component(id = "task-workflow-without-on-message")
@A2aSkill(id = "review")
public class A2aSkillWorkflowWithoutOnMessage extends Workflow<String> {

  public Effect<Done> onMessage(String message) {
    return effects().end().thenReply(Done.getInstance());
  }
}
