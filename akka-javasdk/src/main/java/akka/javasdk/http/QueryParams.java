/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;


import akka.http.javadsl.model.Query;
import akka.japi.Pair;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Represents the query parameters of a request.
 */
public record QueryParams(Query query) {

  /**
   * Returns the value of the first parameter with the given key if it exists.
   */
  public Optional<String> get(String key){
    return query.get(key);
  }

  /**
   * Returns the Integer value of the first parameter with the given key if it exists.
   */
  public Optional<Integer> getInteger(String key){
    return query.get(key).map(Integer::parseInt);
  }

  /**
   * Returns the Long value of the first parameter with the given key if it exists.
   */
  public Optional<Long> getLong(String key) {
    return query.get(key).map(Long::parseLong);
  }

  /**
   * Returns the Boolean value of the first parameter with the given key if it exists.
   */
  public Optional<Boolean> getBoolean(String key) {
    return query.get(key).map(Boolean::parseBoolean);
  }

  /**
   * Returns the Double value of the first parameter with the given key if it exists.
   */
  public Optional<Double> getDouble(String key) {
    return query.get(key).map(Double::parseDouble);
  }

  /**
   * Returns the Float value of the first parameter with the given key if it exists.
   */
  public Optional<Float> getFloat(String key) {
    return query.get(key).map(Float::parseFloat);
  }

  /**
   * Returns the Short value of the first parameter with the given key if it exists.
   */
  public Optional<Short> getShort(String key) {
    return query.get(key).map(Short::parseShort);
  }

  /**
   * Returns the Character value of the first parameter with the given key if it exists.
   */
  public Optional<Character> getChar(String key) {
    return query.get(key).map(s -> s.charAt(0));
  }

  /**
   * Returns the value of all parameters with the given key.
   */
  public List<String> getAll(String key) {
    return query.getAll(key);
  }

  /**
   * Returns the value of all parameters with the given key using mapper function.
   */
  public <T> List<T> getAll(String key, Function<String, T> mapper) {
    return query.getAll(key).stream().map(mapper).toList();
  }

  /**
   * Returns a `List` of all parameters. Use the `toMap()`
   * method to filter out entries with duplicated keys.
   */
  public List<Pair<String, String>> toList() {
    return query.toList();
  }

  /**
   * Returns a key/value map of the parameters. Use
   * the `toList()` method to return all parameters if keys may occur
   * multiple times.
   */
  public Map<String, String> toMap(){
    return query.toMap();
  }

  /**
   * Returns a `Map` of all parameters. Use the `toMap()`
   * method to filter out entries with duplicated keys.
   */
  public Map<String, List<String>> toMultiMap(){
    return query.toMultiMap();
  }
}
