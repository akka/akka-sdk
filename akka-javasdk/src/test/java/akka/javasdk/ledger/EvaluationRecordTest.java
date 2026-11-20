/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class EvaluationRecordTest {

  private static EvaluationRecord record(EvaluationRecord.Subject subject) {
    return new EvaluationRecord(
        "evaluation-1",
        "order-evaluator",
        Optional.empty(),
        EvaluationRecord.Trigger.MANUAL,
        subject,
        Map.of("interactionIds", "i1,i2"),
        new EvaluationRecord.Outcome.Inconclusive("no verdict"),
        Instant.EPOCH,
        Optional.empty());
  }

  @Test
  @SuppressWarnings("deprecation")
  public void readsTheInteractionFieldsOfAnInteractionSubject() {
    var record = record(new EvaluationRecord.Subject.Interaction("interaction-1", "support-agent"));

    assertEquals("interaction-1", record.interactionId());
    assertEquals("support-agent", record.agentComponentId());
  }

  @Test
  @SuppressWarnings("deprecation")
  public void rejectsTheInteractionFieldsOfAWorkflowSubject() {
    var record = record(new EvaluationRecord.Subject.Workflow("workflow-1", "order-workflow"));

    assertThrows(IllegalStateException.class, record::interactionId);
    assertThrows(IllegalStateException.class, record::agentComponentId);
  }

  @Test
  @SuppressWarnings("deprecation")
  public void buildsAnInteractionSubjectFromTheDeprecatedConstructor() {
    var record =
        new EvaluationRecord(
            "evaluation-1",
            "quality-evaluator",
            Optional.empty(),
            EvaluationRecord.Trigger.ON_INTERACTION,
            "interaction-1",
            "support-agent",
            new EvaluationRecord.Outcome.Inconclusive("no verdict"),
            Instant.EPOCH);

    assertEquals(
        new EvaluationRecord.Subject.Interaction("interaction-1", "support-agent"),
        record.subject());
    assertEquals(Map.of(), record.attributes());
    assertEquals(Optional.empty(), record.flowId());
  }

  @Test
  @SuppressWarnings("deprecation")
  public void buildsAnEmptyFlowIdFromTheConstructorWithoutAFlowId() {
    var record =
        new EvaluationRecord(
            "evaluation-1",
            "order-evaluator",
            Optional.empty(),
            EvaluationRecord.Trigger.MANUAL,
            new EvaluationRecord.Subject.Workflow("workflow-1", "order-workflow"),
            Map.of(),
            new EvaluationRecord.Outcome.Inconclusive("no verdict"),
            Instant.EPOCH);

    assertEquals(Optional.empty(), record.flowId());
  }
}
