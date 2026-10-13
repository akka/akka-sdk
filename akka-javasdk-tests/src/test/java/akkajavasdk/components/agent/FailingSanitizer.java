/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.TextSanitizer;

/**
 * Throws for a text that contains "failsanitizer", returns null for a text that contains
 * "nullsanitizer", and returns any other text unchanged. The message of the exception holds the
 * text, as it can for a real sanitizer.
 */
public class FailingSanitizer implements TextSanitizer {

  @Override
  public String sanitize(String text) {
    if (text.contains("failsanitizer")) {
      throw new IllegalStateException("cannot mask: " + text);
    }
    if (text.contains("nullsanitizer")) {
      return null;
    }
    return text;
  }
}
