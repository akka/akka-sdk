/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

/**
 * Exception for a sanitizer that fails while it masks text. It includes the name of the sanitizer.
 *
 * <p>For an agent request, the request fails, and the runtime does not pass the text on. The
 * function given to {@code onFailure} of the agent receives this exception. The caller of the agent
 * does not.
 *
 * <p>A call of {@link SanitizerClient} fails with this exception when the sanitizer is a class that
 * fails or returns {@code null}.
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
