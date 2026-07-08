/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.annotation.DoNotInherit;

/**
 * Injectable client for looking up configured classifiers by name.
 *
 * <p>Can be injected in agents, guardrails, evaluators, and application code, to invoke a
 * configured {@link Classifier} inline wherever it's needed.
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface ClassifierClient {

  /**
   * Looks up the classifier configured under {@code name}.
   *
   * @throws IllegalArgumentException if no classifier is configured with that name
   */
  Classifier classifier(String name);
}
