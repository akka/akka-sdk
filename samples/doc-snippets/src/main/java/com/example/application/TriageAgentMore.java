package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Judgment;
import akka.javasdk.agent.JudgmentModelProvider;
import akka.javasdk.agent.ModelException;
import akka.javasdk.agent.Question;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;

public interface TriageAgentMore {
  // tag::judgment-answers[]
  public class TicketRouter {

    private final ComponentClient componentClient;

    public TicketRouter(ComponentClient componentClient) {
      this.componentClient = componentClient;
    }

    public String route(String sessionId, String ticket) {
      Judgment judgment = componentClient
        .forAgent()
        .inSession(sessionId)
        .method(TriageAgent::triage)
        .invoke(ticket);

      Judgment.ChoiceAnswer route = judgment.choice("route"); // <1>
      if (route.confidence() < 0.5) { // <2>
        return "human-review";
      }
      Judgment.ScoreAnswer severity = judgment.score("severity"); // <3>
      if (judgment.yesNo("urgent").isYes() || severity.value() >= 2) { // <4>
        return route.selected() + "-urgent"; // <5>
      }
      return route.selected();
    }
  }

  // end::judgment-answers[]

  // tag::judgment-map[]
  @Component(id = "routing-agent")
  public class RoutingAgent extends Agent {

    public record Routing(String team, boolean urgent) {}

    public Effect<Routing> route(String ticket) {
      return effects()
        .judgment()
        .state(ticket)
        .question(
          "route",
          Question.choice("Which team should handle this?")
            .option("billing", "Payments, invoicing, refunds")
            .option("technical", "Bugs, outages, integrations")
        )
        .question("urgent", Question.yesNo("Does this need a reply today?"))
        .map(judgment -> // <1>
          new Routing(judgment.choice("route").selected(), judgment.yesNo("urgent").isYes()))
        .onFailure(throwable -> { // <2>
          if (throwable instanceof ModelException) {
            return new Routing("human-review", false);
          } else {
            throw new RuntimeException(throwable);
          }
        })
        .thenReply();
    }
  }

  // end::judgment-map[]

  // tag::judgment-model[]
  @Component(id = "triage-agent-with-model")
  public class TriageAgentWithModel extends Agent {

    public Effect<Judgment> triage(String ticket) {
      return effects()
        .judgment()
        .model(
          JudgmentModelProvider.systemOne() // <1>
            .withApiKey(System.getenv("SYSTEM_ONE_API_KEY"))
            .withModelName("jev-latest")
        )
        .state(ticket)
        .question(
          "route",
          Question.choice("Which team should handle this?")
            .option("billing")
            .option("technical")
        )
        .thenReply();
    }
  }

  @Component(id = "triage-agent-from-config")
  public class TriageAgentFromConfig extends Agent {

    public Effect<Judgment> triage(String ticket) {
      return effects()
        .judgment()
        .model(JudgmentModelProvider.fromConfig("fast-judge")) // <2>
        .state(ticket)
        .question(
          "route",
          Question.choice("Which team should handle this?")
            .option("billing")
            .option("technical")
        )
        .thenReply();
    }
  }
  // end::judgment-model[]
}
