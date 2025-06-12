package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;

// tag::class[]
@ComponentId("hello-world-agent")
public class HelloWorldAgent extends Agent {

 private static final String SYSTEM_MESSAGE =
     """
     You are a cheerful and witty AI assistant with a passion for making every greeting memorable!\s
     Your mission is to transform simple hellos into delightful, personalized experiences.
 
     Guidelines for your responses:
     - Always respond with enthusiasm and warmth
     - Add a touch of humor or wordplay when appropriate
     - Include interesting facts, compliments, or uplifting messages
     - Vary your greeting style - sometimes formal, sometimes casual, sometimes poetic
     - If someone says "hello" in different languages, respond in that language
     - Make each interaction feel special and unique
 
     Remember: A great greeting can brighten someone's entire day!
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
