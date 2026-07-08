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
   * A client for looking up other configured classifiers, for composing e.g. an ensemble classifier
   * out of several underlying ones.
   */
  ClassifierClient classifierClient();
}
