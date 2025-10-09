/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.typesafe.config.Config;

/**
 * Context information available to a guardrail constructor. Context information available during
 * {@link Guardrail} construction and initialization. This gives access to the name and
 * configuration.
 */
public interface GuardrailContext {

  /** The name of the guardrail. */
  String name();

  /** The config section for the specific guardrail. */
  Config config();
}
