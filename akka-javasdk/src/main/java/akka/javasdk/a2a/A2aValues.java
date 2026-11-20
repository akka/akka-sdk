/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Copy helpers that keep the records immutable and never null. */
final class A2aValues {
  private A2aValues() {}

  static <T> List<T> list(List<T> values) {
    return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
  }

  // A JSON object can hold null values, which Map.copyOf rejects.
  static <V> Map<String, V> map(Map<String, V> values) {
    return values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  // Metadata stays null when absent so that it is left out of the JSON.
  static <V> Map<String, V> optionalMap(Map<String, V> values) {
    return values == null ? null : map(values);
  }

  static void exactlyOne(String type, Object... members) {
    int count = 0;
    for (Object member : members) {
      if (member != null) count++;
    }
    if (count != 1) {
      throw new IllegalArgumentException(
          type + " must have exactly one member set, but " + count + " are set");
    }
  }
}
