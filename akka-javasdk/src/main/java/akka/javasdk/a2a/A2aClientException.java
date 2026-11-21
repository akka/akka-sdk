/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

/**
 * A call to a remote A2A agent failed before the agent could answer, or its answer could not be
 * used. Examples are an unreachable agent, a timeout, a missing token and a reply that is not valid
 * A2A.
 */
public class A2aClientException extends RuntimeException {

  public A2aClientException(String message) {
    super(message);
  }

  public A2aClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
