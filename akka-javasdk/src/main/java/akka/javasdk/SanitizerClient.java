/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.annotation.DoNotInherit;
import java.util.concurrent.CompletionStage;

/**
 * Client for masking text with a configured sanitizer by name.
 *
 * <p>Can be injected in service setup, endpoints, agents, consumers, timed actions and workflows.
 * Reaches every configured sanitizer, including the ones a {@link TextSanitizer} implements.
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface SanitizerClient {

  /**
   * Masks {@code text} with the sanitizer configured under {@code name}, blocking for the result.
   *
   * @throws IllegalArgumentException if no sanitizer is configured with that name
   */
  String sanitize(String name, String text);

  /**
   * Async variant of {@link #sanitize}. The returned stage fails with an {@link
   * IllegalArgumentException} when no sanitizer is configured with that name.
   */
  CompletionStage<String> sanitizeAsync(String name, String text);
}
