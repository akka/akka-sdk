/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.common;

import akka.platform.javasdk.annotations.ForwardHeaders;

import java.util.Set;

public class ForwardHeadersExtractor {

  public static Set<String> extractFrom(Class<?> clazz) {
    ForwardHeaders forwardHeaders = clazz.getAnnotation(ForwardHeaders.class);
    if (forwardHeaders != null) {
      return Set.of(forwardHeaders.value());
    } else {
      return Set.of();
    }
  }
}
