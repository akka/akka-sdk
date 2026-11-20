/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;

@Component(id = "skill-agent")
@A2aSkill(id = "check claim!")
@A2aSkill(id = "explain")
@A2aSkill(id = "explain")
public class A2aSkillWithInvalidId extends Agent {
  public Effect<String> ask(String question) {
    return effects().userMessage(question).thenReply();
  }
}
