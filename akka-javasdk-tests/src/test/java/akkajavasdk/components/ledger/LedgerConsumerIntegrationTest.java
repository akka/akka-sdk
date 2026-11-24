/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.ledger;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.evaluation.Subject;
import akka.javasdk.ledger.ClassificationRecord;
import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.agent.AgentResponseGuardrailTestAgent;
import akkajavasdk.components.agent.autonomous.GuardedAutonomousAgent;
import akkajavasdk.components.agent.autonomous.TestTasks;
import akkajavasdk.components.evaluation.ClassifyingEvaluator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Full-integration test of a Consumer that consumes the records of the ledger. */
@ExtendWith(Junit5LogCapturing.class)
public class LedgerConsumerIntegrationTest extends TestKitSupport {

  private final TestModelProvider agentModel = new TestModelProvider();
  private final TestModelProvider autonomousAgentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(AgentResponseGuardrailTestAgent.class, agentModel)
        .withModelProvider(GuardedAutonomousAgent.class, autonomousAgentModel);
  }

  private Optional<ClassificationRecord> receivedClassificationOf(String evaluationId) {
    return ClassificationRecordConsumer.receivedRecords.stream()
        .filter(ClassificationRecord.class::isInstance)
        .map(ClassificationRecord.class::cast)
        .filter(record -> record.evaluationId().equals(Optional.of(evaluationId)))
        .findFirst();
  }

  private GuardrailDecisionRecordConsumer.Received awaitGuardrailDecisionOf(String sessionId) {
    return Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                GuardrailDecisionRecordConsumer.receivedRecords.stream()
                    .filter(received -> received.record().sessionId().equals(sessionId))
                    .findFirst(),
            Optional::isPresent)
        .orElseThrow();
  }

  @Test
  public void deliversAClassificationRecordWithItsRecordId() {
    var workflowId = UUID.randomUUID().toString();
    var run = new Subject.Workflow(workflowId, "evaluated-workflow");

    var evaluationId =
        componentClient
            .forEvaluator(ClassifyingEvaluator.class)
            .startEvaluation(run, Map.of("text", "a perfectly fine message"));

    var classification =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .until(() -> receivedClassificationOf(evaluationId), Optional::isPresent)
            .orElseThrow();

    List<EvaluationRecord> evaluations =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .until(
                () -> getLedgerClient().getWorkflowEvaluations("evaluated-workflow", workflowId),
                found -> !found.isEmpty());
    var evaluation = evaluations.getFirst().evaluation().orElseThrow();

    assertThat(classification.recordId())
        .isEqualTo(evaluation.attributes().get("classificationId"));
  }

  @Test
  public void deliversTheGuardrailDecisionOfARequestBasedAgent() {
    agentModel.fixedResponse("anything");
    var sessionId = UUID.randomUUID().toString();

    // the agent writes an interaction and a guardrail decision
    componentClient
        .forAgent()
        .inSession(sessionId)
        .method(AgentResponseGuardrailTestAgent::mapLlmResponse)
        .invoke("hello");

    var received = awaitGuardrailDecisionOf(sessionId);
    var decision = received.record();

    assertThat(decision.agentComponentId()).isEqualTo("model-guardrail-test-agent");
    assertThat(decision.decision()).isEqualTo(GuardrailDecisionRecord.Decision.DENY);
    assertThat(decision.flowId()).isEmpty();

    assertThat(received.eventSubject()).contains(decision.recordId());
    assertThat(received.metadata().get("ce-type")).contains("guardrail-decision");
    assertThat(received.metadata().get("ce-source")).contains("test model guard");
    assertThat(received.metadata().get("flow-id")).isEmpty();
  }

  @Test
  public void deliversTheGuardrailDecisionOfAnAutonomousAgentWithItsFlowId() {
    autonomousAgentModel.fixedResponse(completeTask("done"));
    var agentId = UUID.randomUUID().toString();

    componentClient
        .forAutonomousAgent(GuardedAutonomousAgent.class, agentId)
        .runSingleTask(TestTasks.STRING_TASK.instructions("Say hello"));

    // the session of an autonomous agent interaction is the id of the agent instance
    var received = awaitGuardrailDecisionOf(agentId);
    var decision = received.record();

    assertThat(decision.agentComponentId()).isEqualTo("guarded-autonomous-agent");
    assertThat(decision.decision()).isEqualTo(GuardrailDecisionRecord.Decision.DENY);
    assertThat(decision.flowId()).isPresent();

    assertThat(received.eventSubject()).contains(decision.recordId());
    assertThat(received.metadata().get("ce-type")).contains("guardrail-decision");
    assertThat(received.metadata().get("ce-source")).contains("autonomous model call guard");
    assertThat(received.metadata().get("flow-id")).isEqualTo(decision.flowId());
  }
}
