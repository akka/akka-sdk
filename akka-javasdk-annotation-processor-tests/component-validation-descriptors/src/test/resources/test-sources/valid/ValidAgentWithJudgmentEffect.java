package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Judgment;
import akka.javasdk.agent.Question;
import akka.javasdk.annotations.Component;

@Component(id = "agent-with-judgment")
public class ValidAgentWithJudgmentEffect extends Agent {

  public Effect<Judgment> triage(String ticket) {
    return effects()
        .judgment()
        .state(ticket)
        .question("route", Question.choice("Which team?").option("billing").option("technical"))
        .thenReply();
  }
}
