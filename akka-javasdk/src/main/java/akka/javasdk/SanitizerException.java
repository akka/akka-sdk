/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

/**
 * Exception thrown when a sanitizer fails while it masks text of an agent request. This includes
 * the name of the sanitizer. The request fails, and the runtime does not pass the text on.
 *
 * <p>The cause is the exception that the sanitizer failed with, or {@code null} when the masked
 * text is {@code null}. The message of the cause can hold the text that the sanitizer was masking.
 * Do not log the cause, and do not return its message to the caller.
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
