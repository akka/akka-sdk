/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

/**
 * A log sanitizer masks the sensitive parts of a log message.
 *
 * <p>The runtime calls it for every log line of the service, including the runtime's own, while the
 * log event is written, and the thread that writes the event waits for the result. Keep an
 * implementation fast and self-contained: a pure function of the message and its configuration,
 * with no blocking and no calls to other services. Do not log from it.
 *
 * <p>An implementation has one public constructor, which takes no parameter or a {@link
 * SanitizerContext} for the sanitizer's configured name and config section.
 *
 * <p>A class can implement both this interface and {@link TextSanitizer}, to mask log messages and
 * the text of agents with the same logic. It is then constructed as a log sanitizer.
 *
 * <p>Log sanitizers are enabled with configuration; see sanitization documentation.
 */
public interface LogSanitizer {

  /**
   * Returns {@code message} with its sensitive parts masked, or {@code message} itself when there
   * is nothing to mask.
   *
   * <p>When this method throws, the runtime replaces the log message with the reason and writes the
   * event at warning level or higher.
   *
   * @return the masked message
   */
  String sanitize(String message);
}
