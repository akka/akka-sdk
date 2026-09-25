/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A text sanitizer masks the sensitive parts of a text. Can be implemented with rules or regex, a
 * detection model, or an external service — the implementation is just user code calling out to
 * whatever it needs.
 *
 * <p>An implementation has a public constructor into which the platform's managed dependencies can
 * be injected — including a {@link SanitizerContext} for the sanitizer's configured name and config
 * section, and a {@link akka.javasdk.agent.ClassifierClient} for a sanitizer that masks what a
 * configured classifier detects.
 *
 * <p>The runtime calls a sanitizer for the text of the entries it is configured for: the text of an
 * agent's model request, what an agent's tool returned, and log messages. Application code calls it
 * by name through an injected {@link SanitizerClient}.
 *
 * <p>An implementation configured for the {@code logs} application point runs while a log event is
 * written, and the thread writing the event waits for it. Keep it fast, and do not log from it: the
 * runtime masks a line the sanitizer logs itself with the configured patterns only, rather than
 * calling the sanitizer again.
 *
 * <p>Text sanitizers are enabled with configuration; see sanitization documentation.
 */
public interface TextSanitizer {

  /**
   * Returns {@code text} with its sensitive parts masked, or {@code text} itself when there is
   * nothing to mask.
   *
   * <p>This is the method to implement. It may block: sanitizers are always invoked on virtual
   * threads. The text can be of any size, from a single tool result to a whole model request.
   *
   * <p>A thrown exception fails the call the text was masked for, so unmasked text is never passed
   * on.
   *
   * @return the masked text
   */
  String sanitize(String text);

  /**
   * Async variant of {@link #sanitize}, for implementations that prefer composing futures.
   *
   * <p>The default implementation delegates to {@link #sanitize}. When this method is overridden,
   * {@link #sanitize} is no longer used, and it is then safe to have it throw {@link
   * UnsupportedOperationException} or return {@code null}.
   *
   * @return a CompletionStage with the masked text
   */
  default CompletionStage<String> sanitizeAsync(String text) {
    return CompletableFuture.completedFuture(sanitize(text));
  }
}
