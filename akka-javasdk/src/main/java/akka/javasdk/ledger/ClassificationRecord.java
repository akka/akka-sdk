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
 * @param classificationId the globally unique id of the classification
 * @param classifierComponentId the component id of the classifier that produced the classification
 * @param label the label the classifier assigned, or empty when it assigned none
 * @param score the score the classifier gave, or empty when it gave none
 * @param confidence the confidence of the classifier in its result, or empty when it reported none
 * @param attributes the attributes the classifier attached to the classification
 * @param flowId the flow the classification was produced in, or empty when it was produced in none
 * @param evaluationId the evaluation the classification was produced in, or empty when it was
 *     produced outside an evaluation
 * @param timestamp when the classification was recorded
 */
public record ClassificationRecord(
    String classificationId,
    String classifierComponentId,
    Optional<String> label,
    Optional<Double> score,
    Optional<Double> confidence,
    Map<String, String> attributes,
    Optional<String> flowId,
    Optional<String> evaluationId,
    Instant timestamp) {}
