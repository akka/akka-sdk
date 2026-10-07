/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * An evaluator bound to agents by their role, not by their component id, runs for each interaction
 * of an agent with that role.
 */
@ExtendWith(Junit5LogCapturing.class)
public class EvaluatorRoleBindingIntegrationTest extends TestKitSupport {

  private final TestModelProvider agentModel = new TestModelProvider();
  private final TestModelProvider judgeModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(RoleEvaluatedAgent.class, agentModel)
        .withModelProvider(QualityJudge.class, judgeModel)
        .withAdditionalConfig(
            """
            akka.javasdk.evaluation.evaluators.response-quality-evaluator {
              agent-roles {
                evaluated-by-role { trigger = interaction }
              }
            }
            """);
  }

  @Test
  public void runsEvaluationForAnAgentBoundByItsRole() {
    agentModel.fixedResponse("You can reset your password under account settings.");
    judgeModel.fixedResponse(
        """
        { "passed": true, "score": 0.9, "reason": "clear and helpful" }
        """
            .stripIndent());

    var reply =
        componentClient
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(RoleEvaluatedAgent::ask)
            .withDetailedReply()
            .invoke("How do I reset my password?");
    String interactionId = reply.interactionId().orElseThrow();

    List<EvaluationRecord> records =
        Awaitility.await()
            .atMost(30, TimeUnit.SECONDS)
            .until(
                () -> getLedgerClient().getEvaluations(interactionId), found -> !found.isEmpty());

    assertThat(records).hasSize(1);
    var record = records.getFirst();
    assertThat(record.evaluatorComponentId()).isEqualTo("response-quality-evaluator");
    assertThat(record.agentComponentId()).isEqualTo("role-evaluated-agent");
    assertThat(record.outcome()).isInstanceOf(EvaluationRecord.Outcome.Verdict.class);
  }
}
