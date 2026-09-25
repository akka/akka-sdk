/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components;

import akka.javasdk.SanitizerContext;
import akka.javasdk.TextSanitizer;

/** Masks log messages, which a sanitizer the service implements can do. */
public class LogSanitizer implements TextSanitizer {

  private final String replacement;

  public LogSanitizer(SanitizerContext context) {
    this.replacement = context.config().getString("replacement");
  }

  @Override
  public String sanitize(String text) {
    return text.replace("logsecret", replacement);
  }
}
