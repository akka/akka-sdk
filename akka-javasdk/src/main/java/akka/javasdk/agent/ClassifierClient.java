/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.annotation.DoNotInherit;
import java.util.concurrent.CompletionStage;

/**
 * Client for invoking a configured {@link Classifier} by name.
 *
 * <p>Can be injected in agents, guardrails, evaluators, and application code — including into
 * another classifier's constructor, to compose an ensemble out of several configured classifiers.
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface ClassifierClient {

  /**
   * Classifies {@code input} with the classifier configured under {@code name}, blocking for the
   * result.
   *
   * @throws IllegalArgumentException if no classifier is configured with that name
   */
  Classification classify(String name, String input);

  /**
   * Async variant of {@link #classify}.
   *
   * @throws IllegalArgumentException if no classifier is configured with that name
   */
  CompletionStage<Classification> classifyAsync(String name, String input);
}
