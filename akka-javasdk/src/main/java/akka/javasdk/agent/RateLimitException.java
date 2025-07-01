/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Exception thrown when rate limits are exceeded when calling an AI model or external service.
 * This indicates that too many requests have been made within a time window.
 */
final public class RateLimitException extends RuntimeException {

  public RateLimitException(String message) {
    super(message);
  }

}
