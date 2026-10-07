/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.annotation.DoNotInherit;

/**
 * Sanitizer that applies every configured pattern and predefined sanitization rule of the service,
 * whatever each of them is configured to apply to.
 *
 * <p>Can be injected in all components, allows for applying manual sanitization according to
 * service configuration in arbitrary logic.
 *
 * <p>Not for user extension, implementation provided by the runtime.
 *
 * @deprecated Use {@link SanitizerClient}, which masks with one configured sanitizer by name.
 */
@Deprecated(since = "3.7.0", forRemoval = true)
@DoNotInherit
public interface Sanitizer {
  String sanitize(String string);
}
