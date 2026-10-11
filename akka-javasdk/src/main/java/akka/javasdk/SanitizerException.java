/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

/**
 * Exception thrown when a sanitizer fails while it masks text of an agent request. The request
 * fails, so the text is not passed on. This includes the name of the sanitizer.
 */
public final class SanitizerException extends RuntimeException {

  private final String sanitizerName;

  public SanitizerException(String message, String sanitizerName, Throwable cause) {
    super(message, cause);
    this.sanitizerName = sanitizerName;
  }

  /**
   * Returns the configured name of the sanitizer that failed.
   *
   * @return the sanitizer name
   */
  public String getSanitizerName() {
    return sanitizerName;
  }
}
