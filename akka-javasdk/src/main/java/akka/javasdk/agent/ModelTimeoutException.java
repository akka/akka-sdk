/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Exception thrown when a request to an AI model or external service times out.
 * This indicates that the operation took longer than the configured timeout period.
 */
final public class ModelTimeoutException extends RuntimeException {

  public ModelTimeoutException(String message) {
    super(message);
  }
}
