/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Map;
import java.util.Optional;

/**
 * The result of a {@link Classifier}: a score and/or label, with optional confidence and
 * attributes.
 *
 * @param score a numeric score, if the classifier produces one
 * @param label a categorical label, if the classifier produces one (may be derived from the score)
 * @param confidence the classifier's confidence in its own result, if available
 * @param attributes any extra key-value detail the classifier wants to attach
 * @param explanation why the classifier reached this result, if it says why
 */
public record Classification(
    Optional<Double> score,
    Optional<String> label,
    Optional<Double> confidence,
    Map<String, String> attributes,
    Optional<String> explanation) {

  public Classification {
    attributes = Map.copyOf(attributes); // defensive, unmodifiable copy
  }

  /** Creates a {@link Classification} with only a score. */
  public static Classification score(double score) {
    return new Classification(
        Optional.of(score), Optional.empty(), Optional.empty(), Map.of(), Optional.empty());
  }

  /** Creates a {@link Classification} with only a label. */
  public static Classification label(String label) {
    return new Classification(
        Optional.empty(), Optional.of(label), Optional.empty(), Map.of(), Optional.empty());
  }

  /** Creates a {@link Classification} with both a score and a label. */
  public static Classification of(double score, String label) {
    return new Classification(
        Optional.of(score), Optional.of(label), Optional.empty(), Map.of(), Optional.empty());
  }

  /** Return a copy of this classification with the given explanation. */
  public Classification withExplanation(String explanation) {
    return new Classification(score, label, confidence, attributes, Optional.of(explanation));
  }
}
