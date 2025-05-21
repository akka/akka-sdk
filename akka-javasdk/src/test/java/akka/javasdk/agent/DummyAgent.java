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
  record Response(String result) {}
  record Response2(String result) {}

  Effect<Response2> doSomething(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .responseAs(Response.class)
//        .reply();
        .map(response -> new Response2(response.result))
//      .reply()
        .onFailure(throwable -> {throw new IllegalStateException("das");});
//        .onFailure(ex -> ...)
  }
}

