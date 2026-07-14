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
 * Unlike the classifiers themselves, which the user implements, the client is how they are
 * <em>called</em>: it never hands back a {@link Classifier} instance, only classifies through one
 * by name, the same way the rest of the SDK talks to a component through a client rather than
 * returning the component.
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
