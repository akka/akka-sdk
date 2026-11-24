/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * The record of one classification, as fetched from the ledger.
 *
 * @param classificationId the globally unique id of the classification, and the id of the record in
 *     the ledger
 * @param classifierName the name that the service configures for the classifier that produced the
 *     classification
 * @param score the score the classifier gave, or empty when it gave none
 * @param label the label the classifier assigned, or empty when it assigned none
 * @param confidence the confidence of the classifier in its result, or empty when it reported none
 * @param attributes the attributes the classifier attached to the classification
 * @param flowId the flow of the component that injected the {@link
 *     akka.javasdk.agent.ClassifierClient} that called the classifier, or empty when that component
 *     ran in no flow. Empty when a guardrail or another classifier called the classifier.
 * @param evaluationId the evaluation that the call of the classifier belongs to. The component that
 *     injected the {@link akka.javasdk.agent.ClassifierClient} is the evaluator, or a component
 *     that the evaluator calls. Empty when the call belongs to no evaluation, and when a guardrail
 *     or another classifier called the classifier.
 * @param timestamp when the classification was recorded
 */
public record ClassificationRecord(
    String classificationId,
    String classifierName,
    Optional<Double> score,
    Optional<String> label,
    Optional<Double> confidence,
    Map<String, String> attributes,
    Optional<String> flowId,
    Optional<String> evaluationId,
    Instant timestamp)
    implements FlowRecord {

  public ClassificationRecord {
    attributes = Map.copyOf(attributes);
  }

  /** The id of the record in the ledger. */
  @Override
  public String recordId() {
    return classificationId;
  }
}
