/*
 * Copyright (C) 2024 Lightbend Inc. <https://www.lightbend.com>
 */
package akka.javasdk.agent;

class DummyAgent1 extends ChatAgent {
  Effect<String> doSomething(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
  }
}

class DummyAgent2 extends ChatAgent {
  record Response(String result) {}

  Effect<Response> doSomething(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question, Response.class)
        .thenReply();
  }
}

class DummyAgent3 extends ChatAgent {
  record Response(String result) {}

  Effect<Response> doSomething(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply(Response::new);
  }
}
