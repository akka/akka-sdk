/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.typesafe.config.Config;

/**
 * Context information available to a classifier constructor. Gives access to the classifier's name,
 * its configuration, and a client for looking up other configured classifiers.
 */
public interface ClassifierContext {

  /** The name of the classifier. */
  String name();

  /** The config section for the specific classifier. */
  Config config();

  /**
   * A client for invoking other configured classifiers by name, for composing e.g. an ensemble
   * classifier out of several underlying ones. Equivalent to injecting a {@link ClassifierClient}
   * directly into the constructor, which is the preferred style.
   */
  ClassifierClient classifierClient();
}
