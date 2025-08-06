/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Exception thrown when there is a failure with the AI model. This can include model errors,
 * invalid requests, or other model-related issues.
 */
public final class ModelException extends RuntimeException {

  public ModelException(String message) {
    super(message);
  }
}
