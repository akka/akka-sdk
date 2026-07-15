/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.typesafe.config.Config;

/**
 * Context information available to a classifier constructor. Gives access to the classifier's name
 * and its configuration.
 *
 * <p>To invoke other configured classifiers — for example, to compose an ensemble out of several
 * underlying ones — inject a {@link ClassifierClient} into the constructor.
 */
public interface ClassifierContext {

  /** The name of the classifier. */
  String name();

  /** The config section for the specific classifier. */
  Config config();
}
