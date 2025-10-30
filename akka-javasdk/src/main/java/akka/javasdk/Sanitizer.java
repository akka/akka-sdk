/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

/**
 * Can be injected in all components, allows for applying manual sanitization according to service
 * configuration in arbitrary logic.
 */
public interface Sanitizer {
  String sanitize(String string);
}
