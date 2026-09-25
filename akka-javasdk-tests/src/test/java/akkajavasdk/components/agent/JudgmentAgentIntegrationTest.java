/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Judgment;
import akka.javasdk.agent.JudgmentRequest;
import akka.javasdk.agent.Question;
import akka.javasdk.testkit.TestJudgmentModelProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.Junit5LogCapturing;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class JudgmentAgentIntegrationTest extends TestKitSupport {

  private final TestJudgmentModelProvider judgmentProvider = new TestJudgmentModelProvider();

  private static final String TICKET = "Help! My payouts have been failing for 3 days.";

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withJudgmentModelProvider(SomeJudgmentAgent.class, judgmentProvider)
        .withJudgmentModelProvider(SomeJudgmentRoutingAgent.class, judgmentProvider)
        .withJudgmentModelProvider(SomeJudgmentStructuredStateAgent.class, judgmentProvider);
  }

  @AfterEach
  public void afterEach() {
    judgmentProvider.reset();
  }

  private String newSessionId() {
    return UUID.randomUUID().toString();
  }

  @Test
  public void shouldReplyWithTheJudgmentAndTokenUsage() {
    judgmentProvider
        .whenState(TICKET)
        .reply(
            Map.of(
                "route",
                new Judgment.ChoiceAnswer("billing", Map.of("billing", 0.9, "technical", 0.1), 0.8),
                "severity",
                new Judgment.ScoreAnswer(
                    2.4,
                    List.of("Low", "Medium", "High", "Critical"),
                    List.of(0.0, 0.1, 0.4, 0.5),
                    0.6),
                "urgent",
                new Judgment.YesNoAnswer(0.87)),
            new Agent.TokenUsage(120, 9));

    Agent.AgentReply<Judgment> reply =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SomeJudgmentAgent::triage)
            .withDetailedReply()
            .invoke(TICKET);

    Judgment judgment = reply.value();
    assertThat(judgment.answers().keySet()).containsExactly("route", "severity", "urgent");
    assertThat(judgment.choice("route").selected()).isEqualTo("billing");
    assertThat(judgment.choice("route").probabilities()).containsEntry("technical", 0.1);
    assertThat(judgment.choice("route").confidence()).isEqualTo(0.8);
    assertThat(judgment.score("severity").value()).isEqualTo(2.4);
    assertThat(judgment.score("severity").legend())
        .containsExactly("Low", "Medium", "High", "Critical");
    assertThat(judgment.yesNo("urgent").isYes()).isTrue();
    assertThat(judgment.model()).isEqualTo("test-judgment-model");
    assertThat(judgment.tokenUsage()).isEqualTo(new Agent.TokenUsage(120, 9));
    assertThat(reply.tokenUsage()).isEqualTo(new Agent.TokenUsage(120, 9));
  }

  @Test
  public void shouldMapTheJudgmentToAnotherType() {
    judgmentProvider.fixedAnswers(
        Map.of(
            "route", TestJudgmentModelProvider.choice("technical"),
            "urgent", TestJudgmentModelProvider.no()));

    SomeJudgmentRoutingAgent.Routing routing =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SomeJudgmentRoutingAgent::route)
            .invoke(TICKET);

    assertThat(routing).isEqualTo(new SomeJudgmentRoutingAgent.Routing("technical", false, 1.0));
  }

  @Test
  public void shouldRecoverFromAFailedJudgmentWithOnFailure() {
    judgmentProvider.whenState(TICKET).failWith(new IllegalStateException("overloaded"));

    SomeJudgmentRoutingAgent.Routing routing =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SomeJudgmentRoutingAgent::route)
            .invoke(TICKET);

    assertThat(routing).isEqualTo(new SomeJudgmentRoutingAgent.Routing("unknown", false, 0.0));
  }

  @Test
  public void shouldFailWithoutOnFailure() {
    // no rule configured: the test provider fails every request

    assertThatThrownBy(
            () ->
                componentClient
                    .forAgent()
                    .inSession(newSessionId())
                    .method(SomeJudgmentAgent::triage)
                    .invoke(TICKET))
        .hasMessageContaining("No judgment configured");
  }

  @Test
  public void shouldSendAnObjectStateAsJson() {
    AtomicReference<JudgmentRequest> received = new AtomicReference<>();
    judgmentProvider.fixedAnswers(
        request -> {
          received.set(request);
          return Map.of("route", TestJudgmentModelProvider.choice("billing"));
        });

    String team =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SomeJudgmentStructuredStateAgent::route)
            .invoke(
                new SomeJudgmentStructuredStateAgent.Ticket(
                    "Duplicate charge", "I was charged twice"));

    assertThat(team).isEqualTo("billing");
    JudgmentRequest request = received.get();
    assertThat(request.stateJson())
        .isEqualTo("{\"subject\":\"Duplicate charge\",\"body\":\"I was charged twice\"}");
    assertThat(request.stateAsText()).isEqualTo(request.stateJson());
    assertThat(request.questions()).containsOnlyKeys("route");
    Question.Choice route = (Question.Choice) request.questions().get("route");
    assertThat(route.options().stream().map(Question.Choice.Option::key))
        .containsExactly("billing", "technical");
  }

  @Test
  public void shouldGiveTheStateAsTextToTheProvider() {
    AtomicReference<JudgmentRequest> received = new AtomicReference<>();
    judgmentProvider.fixedAnswers(
        request -> {
          received.set(request);
          return Map.of(
              "route", TestJudgmentModelProvider.choice("billing"),
              "urgent", TestJudgmentModelProvider.yes());
        });

    componentClient
        .forAgent()
        .inSession(newSessionId())
        .method(SomeJudgmentRoutingAgent::route)
        .invoke(TICKET);

    assertThat(received.get().stateJson()).isEqualTo("\"" + TICKET + "\"");
    assertThat(received.get().stateAsText()).isEqualTo(TICKET);
  }
}
