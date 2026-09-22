package com.example.application;

// tag::judgment[]
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Judgment;
import akka.javasdk.agent.Question;
import akka.javasdk.annotations.Component;

@Component(id = "triage-agent")
public class TriageAgent extends Agent {

  public Effect<Judgment> triage(String ticket) {
    return effects()
      .judgment() // <1>
      .state(ticket) // <2>
      .question(
        "route",
        Question.choice("Which team should handle this?") // <3>
          .option("billing", "Payments, invoicing, refunds")
          .option("technical", "Bugs, outages, integrations")
      )
      .question(
        "severity",
        Question.score("How severe is this?", "Low", "Medium", "High", "Critical") // <4>
      )
      .question(
        "urgent",
        Question.yesNo("Does this need a reply today?", "Time-sensitive", "Can wait") // <5>
      )
      .thenReply(); // <6>
  }
}
// end::judgment[]
