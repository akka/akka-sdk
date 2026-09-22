/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Judgment;
import akka.javasdk.agent.JudgmentRequest;
import akka.javasdk.agent.Question;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestJudgmentModelProviderTest {

  private TestJudgmentModelProvider provider;

  private final JudgmentRequest ticket =
      new JudgmentRequest(
          "\"Help! My payouts have been failing for 3 days.\"",
          Map.of("route", Question.choice("Which team?").option("billing").option("technical")));

  private final JudgmentRequest structured =
      new JudgmentRequest(
          "{\"subject\":\"Duplicate charge\"}", Map.of("urgent", Question.yesNo("Urgent?")));

  @BeforeEach
  void setUp() {
    provider = new TestJudgmentModelProvider();
  }

  @Test
  void answersEveryRequestWithFixedAnswers() {
    provider.fixedAnswers(Map.of("route", TestJudgmentModelProvider.choice("billing")));

    Judgment judgment = provider.judge(ticket);

    assertThat(judgment.choice("route").selected()).isEqualTo("billing");
    assertThat(judgment.choice("route").probabilities()).containsEntry("billing", 1.0);
    assertThat(judgment.model()).isEqualTo("test-judgment-model");
    assertThat(judgment.tokenUsage()).isEqualTo(new Agent.TokenUsage(0, 0));
    assertThat(provider.judge(structured).choice("route").selected()).isEqualTo("billing");
  }

  @Test
  void matchesOnStateText() {
    provider
        .whenState("Help! My payouts have been failing for 3 days.")
        .reply(
            Map.of("route", TestJudgmentModelProvider.choice("billing")),
            new Agent.TokenUsage(12, 3));
    provider
        .whenState(state -> state.contains("Duplicate charge"))
        .reply(Map.of("urgent", TestJudgmentModelProvider.yes()));

    Judgment routed = provider.judge(ticket);
    assertThat(routed.choice("route").selected()).isEqualTo("billing");
    assertThat(routed.tokenUsage()).isEqualTo(new Agent.TokenUsage(12, 3));

    assertThat(provider.judge(structured).yesNo("urgent").isYes()).isTrue();
  }

  @Test
  void computesAnswersFromTheRequest() {
    provider.fixedAnswers(
        request -> {
          var choice = (Question.Choice) request.questions().get("route");
          return Map.of(
              "route", TestJudgmentModelProvider.choice(choice.options().getLast().key()));
        });

    assertThat(provider.judge(ticket).choice("route").selected()).isEqualTo("technical");
  }

  @Test
  void latestMatchingRuleWins() {
    provider.fixedAnswers(Map.of("route", TestJudgmentModelProvider.choice("billing")));
    provider
        .whenRequest(request -> true)
        .reply(Map.of("route", TestJudgmentModelProvider.choice("technical")));

    assertThat(provider.judge(ticket).choice("route").selected()).isEqualTo("technical");
  }

  @Test
  void failsMatchingRequests() {
    provider
        .whenState("Help! My payouts have been failing for 3 days.")
        .failWith(new IllegalStateException("overloaded"));

    assertThatThrownBy(() -> provider.judge(ticket))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("overloaded");
  }

  @Test
  void failsWhenNoRuleMatches() {
    provider
        .whenState("something else")
        .reply(Map.of("route", TestJudgmentModelProvider.choice("billing")));

    assertThatThrownBy(() -> provider.judge(ticket))
        .isInstanceOf(TestJudgmentModelProvider.MissingJudgmentResponseException.class)
        .hasMessageContaining("Help! My payouts")
        .hasMessageContaining("route");
  }

  @Test
  void resetRemovesAllRules() {
    provider.fixedAnswers(Map.of("route", TestJudgmentModelProvider.choice("billing")));
    provider.reset();

    assertThatThrownBy(() -> provider.judge(ticket))
        .isInstanceOf(TestJudgmentModelProvider.MissingJudgmentResponseException.class);
  }

  @Test
  void answerHelpers() {
    assertThat(TestJudgmentModelProvider.score(2.5).score()).isEqualTo(2.5);
    assertThat(TestJudgmentModelProvider.yes().isYes()).isTrue();
    assertThat(TestJudgmentModelProvider.no().isYes()).isFalse();
    assertThat(TestJudgmentModelProvider.no().isYes(0.0)).isTrue();
  }
}
