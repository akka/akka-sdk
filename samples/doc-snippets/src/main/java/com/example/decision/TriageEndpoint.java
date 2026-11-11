package com.example.decision;

// tag::imports[]
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.decision.Answer;
import akka.javasdk.decision.Decision;
import akka.javasdk.decision.DecisionModelClient;
import akka.javasdk.decision.DecisionModelProvider;
import akka.javasdk.decision.Question;

// end::imports[]

// tag::injection[]
@HttpEndpoint("/triage")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TriageEndpoint {

  public record Ticket(String subject, String body) {}

  // end::injection[]
  public record Triage(String team, double severity, boolean urgent, String model) {}

  // tag::questions[]
  public static final Question<Answer.Choice> ROUTE = Question.choice(
    "Which team should handle this ticket?"
  )
    .option("billing", "Payments, invoicing, refunds")
    .option("technical", "Bugs, outages, integrations")
    .build("route");

  public static final Question<Answer.Score> SEVERITY = Question.score(
    "How severe is the reported issue?"
  )
    .levels(
      "Cosmetic, no impact on function",
      "Broken, but a workaround exists",
      "Blocking, no workaround"
    )
    .build("severity");

  public static final Question<Answer.YesNo> URGENT = Question.yesNo(
    "Does the customer need a reply today?"
  )
    .whenYes("The customer states a deadline of today or earlier")
    .whenNo("There is no deadline requirement")
    .build("urgent");

  // end::questions[]

  // tag::injection[]
  private final DecisionModelClient decisionModelClient;

  public TriageEndpoint(DecisionModelClient decisionModelClient) {
    this.decisionModelClient = decisionModelClient;
  }

  // end::injection[]

  @Post
  public Triage triage(Ticket ticket) {
    // tag::invoke[]
    Decision decision = decisionModelClient
      .state(ticket)
      .questions(ROUTE, SEVERITY, URGENT)
      .invoke();
    // end::invoke[]

    // tag::read[]
    String team = decision.answer(ROUTE).selected();
    double severity = decision.answer(SEVERITY).value();
    boolean urgent = decision.answer(URGENT).probability() > 0.8;
    // end::read[]

    return new Triage(team, severity, urgent, decision.model());
  }

  @Post("/escalate")
  public boolean escalate(Ticket ticket) {
    // tag::model[]
    Decision decision = decisionModelClient
      .model(DecisionModelProvider.fromConfig("escalation-model"))
      .state(ticket)
      .questions(URGENT)
      .invoke();
    // end::model[]

    return decision.answer(URGENT).probability() > 0.8;
  }
  // tag::injection[]
}
// end::injection[]
