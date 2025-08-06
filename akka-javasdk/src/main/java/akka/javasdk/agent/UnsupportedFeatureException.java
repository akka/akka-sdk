/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Exception thrown when an unsupported feature is requested from an AI model or service. This
 * indicates that the feature is not available or not supported by the current configuration.
 */
public final class UnsupportedFeatureException extends RuntimeException {

  public UnsupportedFeatureException(String message) {
    super(message);
  }
}
