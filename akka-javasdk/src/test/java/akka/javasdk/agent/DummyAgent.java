/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("dummy1")
@AgentDescription(name = "Dummy Agent", description = "Not very smart agent")
class DummyAgent1 extends ChatAgent {
  Effect<String> doSomething(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
  }
}

@ComponentId("dummy2")
@AgentDescription(name = "Dummy Agent", description = "Not very smart agent")
class DummyAgent2 extends ChatAgent {
  record Response(String result) {}

  Effect<Response> doSomething(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReplyAs(Response.class);
  }
}

