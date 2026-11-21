/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(
    id = "a2a-assistant",
    name = "Assistant",
    description = "Answers questions and remembers the conversation")
public class A2aAssistantAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
        .systemMessage("You are a helpful assistant.")
        .userMessage(question)
        .thenReply();
  }
}
