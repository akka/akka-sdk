package com.example.application;

// tag::class[]
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;

@ComponentId("my-agent") // <2>
public class MyAgent extends Agent { // <1>

  public Effect<String> query(String question) { // <3>
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
  }

}
// end::class[]

interface MyAgentMore {

  @ComponentId("my-agent")
  public class MyAgentWithModel extends Agent {
    // tag::model[]
    public Effect<String> query(String question) {
      return effects()
          .model(ModelProvider // <1>
              .openAi()
              .withApiKey(System.getenv("OPENAI_API_KEY"))
              .withModelName("gpt-4o")
              .withTemperature(0.6)
              .withMaxTokens(10000))
          .systemMessage("You are a helpful...")
          .userMessage(question)
          .thenReply();
    }
    // end::model[]
  }

}
