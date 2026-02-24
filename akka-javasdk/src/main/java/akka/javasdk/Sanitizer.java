/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.annotation.DoNotInherit;

/**
 * Sanitizer that applies all configured sanitization rules for the service.
 *
 * <p>Can be injected in all components, allows for applying manual sanitization according to
 * service configuration in arbitrary logic.
 *
 * <p>Not for user extension, implementation provided by the runtime.
 */
@DoNotInherit
public interface Sanitizer {
  String sanitize(String string);
}
