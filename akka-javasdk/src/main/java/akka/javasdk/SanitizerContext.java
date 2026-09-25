/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import com.typesafe.config.Config;

/**
 * Context information available to a {@link TextSanitizer} constructor. Gives access to the
 * sanitizer's name and its configuration.
 *
 * <p>To mask what a configured classifier detects, inject an {@link
 * akka.javasdk.agent.ClassifierClient} into the constructor.
 */
public interface SanitizerContext {

  /** The name of the sanitizer. */
  String name();

  /** The config section for the specific sanitizer. */
  Config config();
}
