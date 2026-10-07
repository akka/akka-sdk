/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.SanitizerContext;
import akka.javasdk.TextSanitizer;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Records every text it is asked to mask, so a test can tell a sanitizer that is configured but
 * never invoked from one that runs.
 */
public class RecordingSanitizer implements TextSanitizer {

  private static final Queue<String> SEEN = new ConcurrentLinkedQueue<>();

  public static List<String> seen() {
    return List.copyOf(SEEN);
  }

  public static void reset() {
    SEEN.clear();
  }

  private final String replacement;

  public RecordingSanitizer(SanitizerContext context) {
    this.replacement = context.config().getString("replacement");
  }

  @Override
  public String sanitize(String text) {
    SEEN.add(text);
    return text.replace("recordedsecret", replacement);
  }
}
