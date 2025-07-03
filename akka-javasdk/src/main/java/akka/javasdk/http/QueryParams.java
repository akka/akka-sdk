/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Interface for accessing HTTP query parameters with type-safe getters.
 * <p>
 * QueryParams provides convenient methods for extracting query parameters from HTTP requests
 * with automatic type conversion for common types like integers, booleans, and doubles.
 * <p>
 * <strong>Access:</strong>
 * Available through {@link RequestContext#queryParams()} when processing HTTP requests
 * in endpoint methods.
 * <p>
 * <strong>Type Safety:</strong>
 * The typed getter methods ({@link #getInteger}, {@link #getBoolean}, etc.) handle conversion
 * from string values to the requested type, returning {@code Optional.empty()} if the parameter
 * is missing or cannot be converted.
 * <p>
 * <strong>Multiple Values:</strong>
 * Query parameters can have multiple values (e.g., {@code ?tag=java&tag=http}). Use
 * {@link #getAll(String)} to retrieve all values for a parameter name.
 * <p>
 * <strong>Map Conversion:</strong>
 * Use {@link #toMap()} for a simple key-value map (first value only) or {@link #toMultiMap()}
 * to preserve all values for parameters that appear multiple times.
 */
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
