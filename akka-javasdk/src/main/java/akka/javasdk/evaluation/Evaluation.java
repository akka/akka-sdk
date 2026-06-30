/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of an evaluation, recorded by an {@link Evaluator}.
 *
 * <p>An {@code Evaluation} captures whether the evaluated interaction passed, an explanation for
 * the verdict, and optional quantitative ({@code score}) and categorical ({@code label}) results.
 * Arbitrary additional {@code attributes} can be attached for downstream analysis.
 *
 * <p>Create instances with the fluent factory methods and {@code with*} builders, for example:
 *
 * <pre>{@code
 * Evaluation.passed("Response was accurate and helpful")
 *     .withScore(0.92)
 *     .withLabel("excellent");
 * }</pre>
 */
public final class Evaluation {

  private final boolean passed;
  private final String explanation;
  private final Optional<Double> score;
  private final Optional<String> label;
  private final Map<String, String> attributes;

  private Evaluation(
      boolean passed,
      String explanation,
      Optional<Double> score,
      Optional<String> label,
      Map<String, String> attributes) {
    this.passed = passed;
    this.explanation = Objects.requireNonNull(explanation, "explanation must not be null");
    this.score = score;
    this.label = label;
    this.attributes = Map.copyOf(attributes);
  }

  /** Create an evaluation that passed, with the given explanation. */
  public static Evaluation passed(String explanation) {
    return new Evaluation(true, explanation, Optional.empty(), Optional.empty(), Map.of());
  }

  /** Create an evaluation that failed, with the given explanation. */
  public static Evaluation failed(String explanation) {
    return new Evaluation(false, explanation, Optional.empty(), Optional.empty(), Map.of());
  }

  /**
   * Create an evaluation with the given pass/fail verdict and explanation.
   *
   * @param passed whether the evaluated interaction passed
   * @param explanation the reason for the verdict
   */
  public static Evaluation of(boolean passed, String explanation) {
    return new Evaluation(passed, explanation, Optional.empty(), Optional.empty(), Map.of());
  }

  /** Return a copy of this evaluation with the given numeric score. */
  public Evaluation withScore(double score) {
    return new Evaluation(passed, explanation, Optional.of(score), label, attributes);
  }

  /** Return a copy of this evaluation with the given categorical label. */
  public Evaluation withLabel(String label) {
    return new Evaluation(passed, explanation, score, Optional.of(label), attributes);
  }

  /** Return a copy of this evaluation with the given attribute added. */
  public Evaluation withAttribute(String key, String value) {
    var updated = new java.util.HashMap<>(attributes);
    updated.put(key, value);
    return new Evaluation(passed, explanation, score, label, updated);
  }

  /** Return a copy of this evaluation with all the given attributes added. */
  public Evaluation withAttributes(Map<String, String> attributes) {
    var updated = new java.util.HashMap<>(this.attributes);
    updated.putAll(attributes);
    return new Evaluation(passed, explanation, score, label, updated);
  }

  /**
   * @return whether the evaluated interaction passed
   */
  public boolean passed() {
    return passed;
  }

  /**
   * @return the reason for the verdict
   */
  public String explanation() {
    return explanation;
  }

  /**
   * @return the numeric score, if one was set
   */
  public Optional<Double> score() {
    return score;
  }

  /**
   * @return the categorical label, if one was set
   */
  public Optional<String> label() {
    return label;
  }

  /**
   * @return additional attributes attached to this evaluation
   */
  public Map<String, String> attributes() {
    return attributes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Evaluation that)) return false;
    return passed == that.passed
        && explanation.equals(that.explanation)
        && score.equals(that.score)
        && label.equals(that.label)
        && attributes.equals(that.attributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(passed, explanation, score, label, attributes);
  }

  @Override
  public String toString() {
    return "Evaluation["
        + "passed="
        + passed
        + ", explanation="
        + explanation
        + ", score="
        + score
        + ", label="
        + label
        + ", attributes="
        + attributes
        + ']';
  }
}
