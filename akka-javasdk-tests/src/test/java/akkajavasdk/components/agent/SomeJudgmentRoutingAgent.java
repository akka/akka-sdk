/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelException;
import akka.javasdk.agent.Question;
import akka.javasdk.annotations.Component;

@Component(
    id = "judgment-routing-agent",
    name = "Judgment Routing Agent",
    description = "Routes tickets")
public class SomeJudgmentRoutingAgent extends Agent {

  public record Routing(String team, boolean urgent, double confidence) {}

  public Effect<Routing> route(String ticket) {
    return effects()
        .judgment()
        .state(ticket)
        .question(
            "route",
            Question.choice("Which team should handle this?").option("billing").option("technical"))
        .question("urgent", Question.yesNo("Does this need a reply today?"))
        .map(
            judgment ->
                new Routing(
                    judgment.choice("route").choice(),
                    judgment.yesNo("urgent").isYes(),
                    judgment.choice("route").confidence()))
        .onFailure(
            throwable -> {
              if (throwable instanceof ModelException) return new Routing("unknown", false, 0.0);
              throw new RuntimeException(throwable);
            })
        .thenReply();
  }
}
