/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.annotation.InternalApi;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** INTERNAL USE Not for user extension or instantiation */
@InternalApi
public final class ModelAttributes {

  private static final Logger log = LoggerFactory.getLogger(ModelAttributes.class);

  private ModelAttributes() {}

  /** Returns the entries whose value is a {@code String} or a {@code List} of {@code String}. */
  public static Map<String, Object> keepStringValues(Map<String, Object> attributes) {
    // Safety net: the runtime filters the model attributes first.
    if (attributes == null) {
      return Map.of();
    }

    var kept = new HashMap<String, Object>();
    attributes.forEach(
        (key, value) -> {
          if (key != null && isStringOrStringList(value)) {
            kept.put(key, value instanceof List<?> list ? List.copyOf(list) : value);
          }
        });

    return Map.copyOf(kept);
  }

  /** Logs a warning with the keys of {@code attributes} that are not in {@code kept}. */
  public static void logDroppedAttributes(
      String componentId,
      String sessionId,
      Map<String, Object> attributes,
      Map<String, Object> kept) {
    if (attributes == null || kept.size() >= attributes.size()) {
      return;
    }

    var dropped = new HashSet<>(attributes.keySet());
    dropped.removeAll(kept.keySet());

    log.warn(
        "Agent [{}] session [{}]: dropped model attributes {} that are not String or List of"
            + " String",
        componentId,
        sessionId,
        dropped);
  }

  private static boolean isStringOrStringList(Object value) {
    return value instanceof String
        || (value instanceof List<?> list && list.stream().allMatch(e -> e instanceof String));
  }
}
