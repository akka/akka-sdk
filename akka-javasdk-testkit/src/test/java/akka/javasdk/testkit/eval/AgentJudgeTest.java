/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The material sent to the model, checked without a provider. */
class AgentJudgeTest {

  @Test
  void theMaterialLabelsTheCriterionAndTheEvidence() {
    var material =
        AgentJudge.material(
            new Judge.Question(
                "the reply states the customer's tier",
                "Who is cust_1?",
                "Ada Lovelace is a gold customer.",
                List.of(
                    new ToolCall(
                        "getCustomer",
                        Map.of("customerId", "cust_1"),
                        Optional.of("{\"tier\":\"gold\"}"),
                        Optional.empty()))));

    assertThat(material)
        .contains("Criterion:\nthe reply states the customer's tier")
        .contains("The user asked:\nWho is cust_1?")
        .contains("The agent replied:\nAda Lovelace is a gold customer.")
        .contains(
            "Tools called, in order:\n- getCustomer {customerId=cust_1} -> {\"tier\":\"gold\"}");
  }

  @Test
  void aFailedToolCallIsShownAsSuch() {
    var material =
        AgentJudge.material(
            new Judge.Question(
                "the reply admits the lookup failed",
                "Who is cust_404?",
                "I could not find that customer.",
                List.of(
                    new ToolCall(
                        "getCustomer",
                        Map.of("customerId", "cust_404"),
                        Optional.empty(),
                        Optional.of("no customer cust_404")))));

    assertThat(material)
        .contains("- getCustomer {customerId=cust_404} -> failed: no customer cust_404");
  }

  @Test
  void aCaseThatCalledNothingIsJudgedOnTheReplyAlone() {
    var material =
        AgentJudge.material(new Judge.Question("the reply is polite", "hi", "Hello!", List.of()));

    assertThat(material).doesNotContain("Tools called");
  }

  @Test
  void theShippedPromptStatesTheContractTheVerdictIsReadUnder() {
    assertThat(AgentJudge.defaultPrompt())
        .contains("between 0")
        .contains("\"score\"")
        .contains("\"reason\"");
  }
}
