/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.evaluation.Subject;
import akka.javasdk.ledger.ClassificationRecord;
import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.Junit5LogCapturing;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Full-integration test of the classification record: the runtime records each classification, the
 * classifier client returns the id of the record, and the ledger client fetches the record by that
 * id.
 */
@ExtendWith(Junit5LogCapturing.class)
public class ClassificationLedgerIntegrationTest extends TestKitSupport {

  private ClassificationRecord awaitClassification(String classificationId) {
    return Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .ignoreException(NoSuchElementException.class)
        .until(() -> getLedgerClient().getClassification(classificationId), found -> true);
  }

  @Test
  public void fetchesTheRecordOfAClassificationByTheIdThatTheClientReturns() {
    var result =
        getClassifierClient().classifyWithResult("toxicity-test-classifier", "some toxic text");

    assertThat(result.classification().label()).contains("toxic");
    var classificationId = result.classificationId().orElseThrow();

    var record = awaitClassification(classificationId);
    assertThat(record.classificationId()).isEqualTo(classificationId);
    assertThat(record.classifierName()).isEqualTo("toxicity-test-classifier");
    assertThat(record.label()).contains("toxic");
    assertThat(record.score()).contains(1.0);
    assertThat(record.flowId()).isEmpty();
    assertThat(record.evaluationId()).isEmpty();
  }

  @Test
  public void recordsTheEvaluationOfTheEvaluatorThatCalledTheClassifier() {
    var workflowId = UUID.randomUUID().toString();
    var run = new Subject.Workflow(workflowId, "evaluated-workflow");

    var evaluationId =
        componentClient
            .forEvaluator(ClassifyingEvaluator.class)
            .startEvaluation(run, Map.of("text", "a perfectly fine message"));

    List<EvaluationRecord> evaluations =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .until(
                () -> getLedgerClient().getWorkflowEvaluations("evaluated-workflow", workflowId),
                found -> !found.isEmpty());
    var evaluation = evaluations.getFirst().evaluation().orElseThrow();
    assertThat(evaluation.explanation()).isEqualTo("classified as clean");
    var classificationId = evaluation.attributes().get("classificationId");
    assertThat(classificationId).isNotEmpty();

    var record = awaitClassification(classificationId);
    assertThat(record.classifierName()).isEqualTo("toxicity-test-classifier");
    assertThat(record.label()).contains("clean");
    assertThat(record.evaluationId()).isEqualTo(Optional.of(evaluationId));
  }
}
