/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("dummy1")
@AgentDescription(name = "Dummy Agent", description = "Not very smart agent")
class DummyAgent1 extends Agent {
  Effect<String> doSomething(String question) {
    return effects()
      .systemMessage("You are a helpful...")
      .userMessage(question)
      .thenReply();
  }
}

@ComponentId("dummy2")
@AgentDescription(name = "Dummy Agent", description = "Not very smart agent")
class DummyAgent2 extends Agent {
  record Response(String result) {
  }

  Effect<Response> doSomething(String question) {
    return effects()
      .systemMessage("You are a helpful...")
      .memory(MemoryProvider.limitedWindow().readOnly())
      .userMessage(question)
      .responseAs(Response.class)
      .thenReply();
  }

  Effect<Response> doSomethingElse(String question) {
    // customer memory
    var memory = new CoreMemory() {

      @Override
      public void addInteraction(String sessionId, String componentId, String userMessage, String aiMessage) {

      }

      @Override
      public ConversationHistory getHistory(String sessionId) {
        return null;
      }
    };

    return effects()
        .systemMessage("You are a helpful...")
        .memory(MemoryProvider.custom(memory))
        .userMessage(question)
        .responseAs(Response.class)
        .thenReply();
  }
}

