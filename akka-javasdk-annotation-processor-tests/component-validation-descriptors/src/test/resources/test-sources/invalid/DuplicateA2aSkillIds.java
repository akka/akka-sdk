/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;

public class DuplicateA2aSkillIds {

  @Component(id = "first-agent")
  @A2aSkill(id = "answer")
  public static class First extends Agent {
    public Effect<String> ask(String question) {
      return effects().userMessage(question).thenReply();
    }
  }

  @Component(id = "second-agent")
  @A2aSkill(id = "answer")
  public static class Second extends Agent {
    public Effect<String> ask(String question) {
      return effects().userMessage(question).thenReply();
    }
  }
}
