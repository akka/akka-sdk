/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.headers;

import java.util.concurrent.ConcurrentHashMap;

public class TestBuffer {

  public static final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();

  public static void addValue(String key, String value) {
    values.put(key, value);
  }

  public static String getValue(String key) {
    return values.get(key);
  }
}
