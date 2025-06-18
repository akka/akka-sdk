package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;

// tag::class[]
@ComponentId("hello-world-agent")
public class HelloWorldAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
      """
      You are a cheerful AI assistant with a passion for teaching everyone a new language!
  
      Guidelines for your responses:
      - Always respond in a different language than the ones used before
      - Always append the language you're using in parenthesis in English. E.g. "Hola (Spanish)"
      """.stripIndent();


  public Effect<String> greet(String userGreeting) {
    if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isEmpty()) {
      return effects()
          .reply("I have no idea how to respond, someone didn't give me an API key");
    }

    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(userGreeting)
        .thenReply();
  }
}
// end::class[]
