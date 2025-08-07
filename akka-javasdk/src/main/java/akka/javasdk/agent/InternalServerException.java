/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Exception thrown when there is an internal failure within the agent system. This indicates an
 * unexpected error that occurred during agent processing.
 */
public final class InternalServerException extends RuntimeException {

  public InternalServerException(String message) {
    super(message);
  }
}
