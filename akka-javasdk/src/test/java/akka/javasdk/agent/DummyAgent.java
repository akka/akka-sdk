/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.Component;
import java.util.List;

@Component(id = "dummy1", name = "Dummy Agent", description = "Not very smart agent")
class DummyAgent1 extends Agent {
  Effect<String> doSomething(String question) {
    return effects().systemMessage("You are a helpful...").userMessage(question).thenReply();
  }
}

@Component(id = "dummy2")
@AgentDescription(name = "Dummy Agent", description = "Not very smart agent")
class DummyAgent2 extends Agent {
  record Response(String result) {}

  Effect<Response> doSomething(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .memory(MemoryProvider.limitedWindow().readOnly())
        .userMessage(question)
        .responseAs(Response.class)
        .thenReply();
  }
}

@Component(id = "dummy3")
@AgentDescription(name = "Dummy Agent", description = "Not very smart agent")
class DummyAgent3 extends Agent {
  Effect<String> doSomethingElse(String question) {
    // customer memory
    var memory =
        new SessionMemory() {

          @Override
          public void addInteraction(
              String sessionId,
              SessionMessage.UserMessage userMessage,
              List<SessionMessage> messages) {}

          @Override
          public SessionHistory getHistory(String sessionId) {
            return null;
          }
        };

    return effects()
        .systemMessage("You are a helpful...")
        .memory(MemoryProvider.custom(memory))
        .userMessage(question)
        .thenReply();
  }
}

@Component(id = "dummy4")
@AgentDescription(name = "Dummy Agent", description = "Not very smart nor memorable agent")
class DummyAgent4 extends Agent {
  Effect<String> doSomething(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .memory(MemoryProvider.none())
        .userMessage(question)
        .thenReply();
  }
}
