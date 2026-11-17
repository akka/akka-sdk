/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/** Exception thrown when the API of an AI model answers with a server error status (5xx). */
public final class InternalServerException extends RuntimeException {

  public InternalServerException(String message) {
    super(message);
  }
}
