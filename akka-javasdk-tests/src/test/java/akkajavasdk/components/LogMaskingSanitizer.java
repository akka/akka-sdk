/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components;

import akka.javasdk.LogSanitizer;
import akka.javasdk.SanitizerContext;

/** Masks log messages, which a sanitizer the service implements can do. */
public class LogMaskingSanitizer implements LogSanitizer {

  private final String replacement;

  public LogMaskingSanitizer(SanitizerContext context) {
    this.replacement = context.config().getString("replacement");
  }

  @Override
  public String sanitize(String message) {
    return message.replace("logsecret", replacement);
  }
}
