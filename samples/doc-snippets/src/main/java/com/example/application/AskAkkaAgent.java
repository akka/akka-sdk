package com.example.application;

// tag::class[]
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("ask-akka-agent") // <2>
@AgentDescription(name = "Ask Akka", description = "Expert in Akka")
public class AskAkkaAgent extends Agent { // <1>

  private static final String SYSTEM_MESSAGE =
      """
      You are a very enthusiastic Akka representative who loves to help people!
      Given the following sections from the Akka SDK documentation, answer the question
      using only that information, outputted in markdown format.
      If you are unsure and the text is not explicitly written in the documentation, say:
      Sorry, I don't know how to help with that.
      """.stripIndent(); // <4>

  public StreamEffect ask(String question) { // <3>
    return streamEffects()
        .systemMessage(SYSTEM_MESSAGE) // <4>
        .userMessage(question) // <5>
        .thenReply();
  }

}
// end::class[]
