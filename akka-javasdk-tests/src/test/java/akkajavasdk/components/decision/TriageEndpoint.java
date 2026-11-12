/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.decision;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.decision.Answer;
import akka.javasdk.decision.DecisionModelClient;
import akka.javasdk.decision.DecisionModelProvider;
import akka.javasdk.decision.Question;
import java.util.List;
import java.util.Map;

@HttpEndpoint("/triage")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TriageEndpoint {

  public record Ticket(String subject, String body) {}

  public record TriageResult(
      String team, double severity, double urgentProbability, String model) {}

  public static final Question<Answer.Choice> ROUTE =
      Question.choice("Which team should handle this ticket?")
          .option("billing", "Payments, invoicing, refunds")
          .option("technical", "Bugs, outages, integrations")
          .build("route");

  public static final Question<Answer.Score> SEVERITY =
      Question.score("How severe is the reported issue?")
          .levels(
              "Cosmetic, no impact on function",
              "Broken, but a workaround exists",
              "Blocking, no workaround")
          .build("severity");

  public static final Question<Answer.Predicate> URGENT =
      Question.predicate("Does the customer need a reply today?")
          .whenTrue("The customer states a deadline of today or earlier")
          .whenFalse("There is no deadline requirement")
          .build("urgent");

  private final DecisionModelClient decisionModelClient;

  public TriageEndpoint(DecisionModelClient decisionModelClient) {
    this.decisionModelClient = decisionModelClient;
  }

  @Post
  public TriageResult triage(Ticket ticket) {
    var decision = decisionModelClient.input(ticket).questions(ROUTE, SEVERITY, URGENT).invoke();
    return new TriageResult(
        decision.answer(ROUTE).selected(),
        decision.answer(SEVERITY).value(),
        decision.answer(URGENT).probability(),
        decision.model());
  }

  @Post("/failure-class")
  public String failureClass(Ticket ticket) {
    try {
      decisionModelClient.input(ticket).questions(ROUTE, SEVERITY, URGENT).invoke();
      return "no-failure";
    } catch (RuntimeException e) {
      return e.getClass().getSimpleName();
    }
  }

  // routes for calling a SystemOne or OpenAI stand-in server through the runtime,
  // the provider path segment is system-one or openai

  public record StandInCall(String baseUrl, String input) {}

  public record ChoiceResult(
      String selected, double confidence, Map<String, Double> probabilities, String model) {}

  public record ScoreResult(double value, double confidence, List<Double> probabilities) {}

  public record PredicateResult(double probability) {}

  private static DecisionModelProvider standIn(String provider, String baseUrl) {
    return switch (provider) {
      case "system-one" ->
          DecisionModelProvider.systemOne()
              .withApiKey("test-key")
              .withModelName("jev-latest")
              .withBaseUrl(baseUrl)
              .withMaxRetries(0);
      case "openai" ->
          DecisionModelProvider.openAi()
              .withApiKey("test-key")
              .withModelName("gpt-6-luna")
              .withBaseUrl(baseUrl)
              .withMaxRetries(0);
      default -> throw new IllegalArgumentException("Unknown stand-in provider [" + provider + "]");
    };
  }

  @Post("/{provider}/route")
  public ChoiceResult routeWithStandIn(String provider, StandInCall call) {
    var decision =
        decisionModelClient
            .model(standIn(provider, call.baseUrl()))
            .input(call.input())
            .questions(ROUTE)
            .invoke();
    var answer = decision.answer(ROUTE);
    return new ChoiceResult(
        answer.selected(), answer.confidence(), answer.probabilities(), decision.model());
  }

  @Post("/{provider}/severity")
  public ScoreResult severityWithStandIn(String provider, StandInCall call) {
    var decision =
        decisionModelClient
            .model(standIn(provider, call.baseUrl()))
            .input(call.input())
            .questions(SEVERITY)
            .invoke();
    var answer = decision.answer(SEVERITY);
    return new ScoreResult(answer.value(), answer.confidence(), answer.probabilities());
  }

  @Post("/{provider}/urgent")
  public PredicateResult urgentWithStandIn(String provider, StandInCall call) {
    var decision =
        decisionModelClient
            .model(standIn(provider, call.baseUrl()))
            .input(call.input())
            .questions(URGENT)
            .invoke();
    return new PredicateResult(decision.answer(URGENT).probability());
  }

  @Post("/{provider}/failure-class")
  public String standInFailureClass(String provider, StandInCall call) {
    try {
      decisionModelClient
          .model(standIn(provider, call.baseUrl()))
          .input(call.input())
          .questions(URGENT)
          .invoke();
      return "no-failure";
    } catch (RuntimeException e) {
      return e.getClass().getSimpleName();
    }
  }

  public record FailureResult(String exceptionClass, String message) {}

  // asks two questions, so that a test can fail the call on one of them
  @Post("/{provider}/route-and-urgent-failure")
  public FailureResult routeAndUrgentFailure(String provider, StandInCall call) {
    try {
      decisionModelClient
          .model(standIn(provider, call.baseUrl()))
          .input(call.input())
          .questions(ROUTE, URGENT)
          .invoke();
      return new FailureResult("no-failure", "");
    } catch (RuntimeException e) {
      return new FailureResult(e.getClass().getSimpleName(), e.getMessage());
    }
  }
}
