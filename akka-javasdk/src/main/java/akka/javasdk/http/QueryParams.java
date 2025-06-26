/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Represents the query parameters of an HTTP request. */
public interface QueryParams {
  /** Returns the value of the first parameter with the given key if it exists. */
  Optional<String> getString(String key);

  /** Returns the Integer value of the first parameter with the given key if it exists. */
  Optional<Integer> getInteger(String key);

  /** Returns the Long value of the first parameter with the given key if it exists. */
  Optional<Long> getLong(String key);

  /** Returns the Boolean value of the first parameter with the given key if it exists. */
  Optional<Boolean> getBoolean(String key);

  /** Returns the Double value of the first parameter with the given key if it exists. */
  Optional<Double> getDouble(String key);

  /** Returns the value of all parameters with the given key. */
  List<String> getAll(String key);

  /** Returns the value of all parameters with the given key using mapper function. */
  <T> List<T> getAll(String key, Function<String, T> mapper);

  /**
   * Returns a key/value map of the parameters. Use the `toMultiMap()` method to return all
   * parameters if keys may occur multiple times.
   */
  Map<String, String> toMap();

  /**
   * Returns a `Map` of all parameters. Use the `toMap()` method to filter out entries with
   * duplicated keys.
   */
  Map<String, List<String>> toMultiMap();
}
