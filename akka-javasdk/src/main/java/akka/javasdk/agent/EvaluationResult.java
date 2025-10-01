/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * When an {@link Agent} returns an {@code EvaluationResult} it is tracked in metrics and traces.
 */
public interface EvaluationResult {
  String explanation();

  boolean passed();
}
