package com.example.application;

// tag::class[]
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

import java.util.UUID;

@ComponentId("my-agent") // <2>
public class MyAgent extends Agent { // <1>

  Effect<String> query(String question) { // <3>
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
  }

}
// end::class[]

interface More {

  @ComponentId("my-agent")
  public class MyAgentWithModel extends Agent {
    // tag::model[]
    Effect<String> query(String question) {
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

  // tag::prompt[]
  @ComponentId("activity-agent")
  public class ActivityAgent extends Agent {
    private static final String SYSTEM_MESSAGE = // <1>
        """
        You are an activity agent. Your job is to suggest activities in the
        real world. Like for example, a team building activity, sports, an
        indoor or outdoor game, board games, a city trip, etc.
        """.stripIndent();

    Effect<String> query(String message) {
      return effects()
          .systemMessage(SYSTEM_MESSAGE) // <2>
          .userMessage(message)// <3>
          .thenReply();
    }
  }
  // end::prompt[]

  public class Caller {
    private final ComponentClient componentClient;

    public Caller(ComponentClient componentClient) {
      this.componentClient = componentClient;
    }

    public void callIt() {
      // tag::call[]
      var sessionId = UUID.randomUUID().toString();
      String suggestion =
        componentClient
            .forAgent()// <1>
            .inSession(sessionId)// <2>
            .method(ActivityAgent::query)
            .invoke("Business colleagues meeting in London");
      // end::call[]
    }
  }

}
