/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.ledger.ActivityRecord;
import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.FlowPage;
import akka.javasdk.ledger.FlowRecord;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.ledger.InteractionMetadata;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.ModelConfig;
import akka.javasdk.testkit.TestLedgerClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

public class TestLedgerClientTest {

  private static InteractionRecord interaction(
      String id, String agentId, String sessionId, Instant timestamp) {
    return interaction(id, agentId, sessionId, Optional.empty(), timestamp);
  }

  private static InteractionRecord interaction(
      String id, String agentId, String sessionId, Optional<String> flowId, Instant timestamp) {
    var metadata =
        new InteractionMetadata(
            new ModelConfig("test", "test", "", -1.0, -1.0, -1, -1),
            Map.of(),
            timestamp,
            timestamp,
            InteractionMetadata.FinishReason.STOP);
    return new InteractionRecord(
        id,
        sessionId,
        agentId,
        flowId,
        metadata,
        "",
        List.of(),
        List.of(),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        timestamp);
  }

  private static EvaluationRecord workflowEvaluation(
      String id, String workflowId, Instant timestamp) {
    return new EvaluationRecord(
        id,
        "order-evaluator",
        Optional.empty(),
        EvaluationRecord.Trigger.MANUAL,
        new EvaluationRecord.Subject.Workflow(workflowId, "order-workflow"),
        Map.of("interactionIds", "i1,i2"),
        new EvaluationRecord.Outcome.Verdict(Evaluation.passed("ok")),
        timestamp);
  }

  private static EvaluationRecord interactionEvaluation(
      String id, String interactionId, Instant timestamp) {
    return new EvaluationRecord(
        id,
        "quality-evaluator",
        Optional.empty(),
        EvaluationRecord.Trigger.ON_INTERACTION,
        new EvaluationRecord.Subject.Interaction(interactionId, "support-agent"),
        Map.of(),
        new EvaluationRecord.Outcome.Verdict(Evaluation.passed("ok")),
        timestamp);
  }

  private static GuardrailDecisionRecord decision(
      String id, String interactionId, String sessionId, Instant timestamp) {
    return decision(id, interactionId, sessionId, Optional.empty(), timestamp);
  }

  private static GuardrailDecisionRecord decision(
      String id,
      String interactionId,
      String sessionId,
      Optional<String> flowId,
      Instant timestamp) {
    return new GuardrailDecisionRecord(
        id,
        "support-agent",
        interactionId,
        sessionId,
        flowId,
        "no-refunds",
        Optional.empty(),
        "policy",
        GuardrailDecisionRecord.Boundary.BEFORE_MODEL_CALL,
        GuardrailDecisionRecord.Decision.ALLOW,
        "",
        false,
        GuardrailDecisionRecord.FailMode.FAIL_CLOSED,
        Duration.ZERO,
        Optional.empty(),
        timestamp);
  }

  private static ActivityRecord activity(String id, String flowId, Instant timestamp) {
    return new ActivityRecord(
        id,
        "coordinator-agent",
        "coordinator-1",
        flowId,
        List.of(new ActivityRecord.Event.TaskAssigned("task-1")),
        timestamp);
  }

  private static EvaluationRecord evaluation(String id, String interactionId, Instant timestamp) {
    return new EvaluationRecord(
        id,
        "quality-evaluator",
        Optional.empty(),
        EvaluationRecord.Trigger.ON_INTERACTION,
        interactionId,
        "coordinator-agent",
        new EvaluationRecord.Outcome.Verdict(Evaluation.of(true, "good")),
        timestamp);
  }

  @Test
  public void listsTheInteractionsOfAnAgentInASessionNewestFirst() {
    var first = interaction("i1", "support-agent", "session-1", Instant.ofEpochSecond(1));
    var second = interaction("i2", "support-agent", "session-1", Instant.ofEpochSecond(2));
    var ledger =
        TestLedgerClient.create()
            .seed(first)
            .seed(second)
            .seed(interaction("i3", "support-agent", "session-2", Instant.ofEpochSecond(3)))
            .seed(interaction("i4", "other-agent", "session-1", Instant.ofEpochSecond(4)));

    assertEquals(List.of(second, first), ledger.getInteractions("support-agent", "session-1"));
    assertTrue(ledger.getInteractions("support-agent", "session-3").isEmpty());
  }

  @Test
  public void listsTheEvaluationsOfAWorkflowRunNewestFirst() {
    var first = workflowEvaluation("e1", "workflow-1", Instant.ofEpochSecond(1));
    var second = workflowEvaluation("e2", "workflow-1", Instant.ofEpochSecond(2));
    var ofInteraction = interactionEvaluation("e3", "interaction-1", Instant.ofEpochSecond(3));
    var ledger =
        TestLedgerClient.create()
            .seed(first)
            .seed(second)
            .seed(workflowEvaluation("e4", "workflow-2", Instant.ofEpochSecond(4)))
            .seed(ofInteraction);

    assertEquals(
        List.of(second, first), ledger.getWorkflowEvaluations("order-workflow", "workflow-1"));
    assertTrue(ledger.getWorkflowEvaluations("other-workflow", "workflow-1").isEmpty());
    assertEquals(List.of(ofInteraction), ledger.getEvaluations("interaction-1"));
  }

  @Test
  public void listsTheGuardrailDecisionsOfAnInteractionNewestFirst() {
    var first = decision("g1", "interaction-1", "session-1", Instant.ofEpochSecond(1));
    var second = decision("g2", "interaction-1", "session-1", Instant.ofEpochSecond(2));
    var ledger =
        TestLedgerClient.create()
            .seed(first)
            .seed(second)
            .seed(decision("g3", "interaction-2", "session-1", Instant.ofEpochSecond(3)));

    assertEquals(List.of(second, first), ledger.getGuardrailDecisions("interaction-1"));
    assertTrue(ledger.getGuardrailDecisions("interaction-3").isEmpty());
  }

  @Test
  public void readsTheRecordsOfAFlowOldestFirst() {
    var flow = Optional.of("flow-1");
    var otherFlow = Optional.of("flow-2");
    var interaction = interaction("i1", "coordinator-agent", "c1", flow, Instant.ofEpochSecond(1));
    var activity = activity("a1", "flow-1", Instant.ofEpochSecond(2));
    var decision = decision("g1", "i1", "c1", flow, Instant.ofEpochSecond(3));
    var evaluation = evaluation("e1", "i1", Instant.ofEpochSecond(4));
    var ledger =
        TestLedgerClient.create()
            .seed(evaluation)
            .seed(decision)
            .seed(activity)
            .seed(interaction)
            .seed(interaction("i2", "coordinator-agent", "c2", otherFlow, Instant.ofEpochSecond(1)))
            .seed(activity("a2", "flow-2", Instant.ofEpochSecond(2)))
            .seed(decision("g2", "i2", "c2", otherFlow, Instant.ofEpochSecond(3)))
            .seed(evaluation("e2", "i2", Instant.ofEpochSecond(4)))
            .seed(interaction("i3", "support-agent", "s1", Instant.ofEpochSecond(1)))
            .seed(evaluation("e3", "i3", Instant.ofEpochSecond(4)));

    var page = ledger.getFlowRecords("flow-1");

    assertEquals(List.of(interaction, activity, decision, evaluation), page.records());
    assertEquals(Optional.empty(), page.nextPosition());
    assertEquals(page, ledger.getFlowRecordsAsync("flow-1").toCompletableFuture().join());
    assertEquals(new FlowPage(List.of(), Optional.empty(), 0), ledger.getFlowRecords("flow-3"));
  }

  @Test
  public void readsAnEvaluationInTheFlowOfItsInteraction() {
    var interaction =
        interaction(
            "i1", "coordinator-agent", "c1", Optional.of("flow-1"), Instant.ofEpochSecond(1));
    var evaluation = evaluation("e1", "i1", Instant.ofEpochSecond(2));
    var ledger =
        TestLedgerClient.create()
            .seed(interaction)
            .seed(evaluation)
            .seed(evaluation("e2", "unknown-interaction", Instant.ofEpochSecond(3)));

    assertEquals(List.of(interaction, evaluation), ledger.getFlowRecords("flow-1").records());
  }

  @Test
  public void readsTheRecordsOfAFlowWithTheSameTimestampInTheOrderTheyWereSeeded() {
    var timestamp = Instant.ofEpochSecond(1);
    var first = activity("a2", "flow-1", timestamp);
    var second = interaction("i1", "coordinator-agent", "c1", Optional.of("flow-1"), timestamp);
    var third = activity("a1", "flow-1", timestamp);
    var ledger = TestLedgerClient.create().seed(first).seed(second).seed(third);

    assertEquals(
        List.<FlowRecord>of(first, second, third), ledger.getFlowRecords("flow-1").records());
  }

  @Test
  public void readsTheRecordsOfAFlowInPagesOf100() {
    var ledger = TestLedgerClient.create();
    var seeded = new ArrayList<FlowRecord>();
    for (int i = 0; i < 101; i++) {
      var activity = activity("a" + i, "flow-1", Instant.ofEpochSecond(i));
      seeded.add(activity);
      ledger.seed(activity);
    }

    FlowPage first = ledger.getFlowRecords("flow-1");
    assertEquals(seeded.subList(0, 100), first.records());
    assertEquals(Optional.of(101L), first.nextPosition());
    assertEquals(101L, first.continuationPosition());

    FlowPage second = ledger.getFlowRecords("flow-1", first.nextPosition().get());
    assertEquals(List.of(seeded.get(100)), second.records());
    assertEquals(Optional.empty(), second.nextPosition());
    assertEquals(102L, second.continuationPosition());
  }

  @Test
  public void readsTheRecordsRecordedAfterTheLastPageFromItsContinuationPosition() {
    var first = activity("a1", "flow-1", Instant.ofEpochSecond(1));
    var ledger = TestLedgerClient.create().seed(first);
    FlowPage last = ledger.getFlowRecords("flow-1");
    assertEquals(List.of(first), last.records());
    assertEquals(Optional.empty(), last.nextPosition());

    FlowPage caughtUp = ledger.getFlowRecords("flow-1", last.continuationPosition());
    assertEquals(List.of(), caughtUp.records());
    assertEquals(last.continuationPosition(), caughtUp.continuationPosition());

    var second = activity("a2", "flow-1", Instant.ofEpochSecond(2));
    ledger.seed(second);
    FlowPage continued = ledger.getFlowRecords("flow-1", last.continuationPosition());
    assertEquals(List.of(second), continued.records());
    assertEquals(Optional.empty(), continued.nextPosition());
  }

  @Test
  public void rejectsAnEmptyFlowId() {
    var ledger = TestLedgerClient.create();

    assertThrows(IllegalArgumentException.class, () -> ledger.getFlowRecords(""));
    assertThrows(IllegalArgumentException.class, () -> ledger.getFlowRecords("", 1));
    var async =
        assertThrows(
            CompletionException.class,
            () -> ledger.getFlowRecordsAsync("").toCompletableFuture().join());
    assertTrue(async.getCause() instanceof IllegalArgumentException);
  }
}
