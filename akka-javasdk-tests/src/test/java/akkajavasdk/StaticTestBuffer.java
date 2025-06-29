/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import java.util.concurrent.ConcurrentHashMap;

public class StaticTestBuffer {

  public static final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();

  public static void addValue(String key, String value) {
    values.put(key, value);
  }

  public static String getValue(String key) {
    return values.get(key);
  }
}
