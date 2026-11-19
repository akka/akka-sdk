/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.evaluation.Subject;
import akka.javasdk.ledger.ClassificationRecord;
import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.Junit5LogCapturing;
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

  private Optional<ClassificationRecord> receivedClassificationOf(String evaluationId) {
    return ClassificationRecordConsumer.receivedRecords.stream()
        .filter(ClassificationRecord.class::isInstance)
        .map(ClassificationRecord.class::cast)
        .filter(record -> record.evaluationId().equals(Optional.of(evaluationId)))
        .findFirst();
  }

  @Test
  public void deliversTheSelectedKindsOnly() {
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

    assertThat(ClassificationRecordConsumer.receivedRecords)
        .allMatch(ClassificationRecord.class::isInstance);

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
}
