/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.runtime.sdk.spi.BytesPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonSupport {

  // object mapper for HTTP endpoints and explicit serialization/deserialization
  // of objects in user code, customizable for by users, not used for "internal" serialization in
  // component client, views etc.
  private static final ObjectMapper objectMapper =
      akka.javasdk.impl.serialization.JsonSerializer.newObjectMapperWithDefaults();

  /**
   * The Jackson ObjectMapper that is used for encoding and decoding JSON for HTTP endpoints and
   * HTTP requests.
   *
   * <p>You may adjust its configuration, but that must only be performed before starting the
   * service, from {@link akka.javasdk.ServiceSetup#onStartup}.
   */
  public static ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  // internal serialization object, but using the public/configurable mapper
  private static akka.javasdk.impl.serialization.JsonSerializer jsonSerializer =
      new akka.javasdk.impl.serialization.JsonSerializer(objectMapper);

  private JsonSupport() {}

  /**
   * Encode the given value as JSON using Jackson.
   *
   * @param value the object to encode as JSON, must be an instance of a class properly annotated
   *     with the needed Jackson annotations.
   * @throws IllegalArgumentException if the given value cannot be turned into JSON
   */
  public static <T> akka.util.ByteString encodeToAkkaByteString(T value) {
    try {
      return akka.util.ByteString.fromArrayUnsafe(
          objectMapper.writerFor(value.getClass()).writeValueAsBytes(value));
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(
          "Could not encode [" + value.getClass().getName() + "] as JSON", ex);
    }
  }

  /**
   * Encode the given value as JSON using Jackson.
   *
   * @param value the object to encode as JSON, must be an instance of a class properly annotated
   *     with the needed Jackson annotations.
   * @throws IllegalArgumentException if the given value cannot be turned into JSON
   */
  public static <T> String encodeToString(T value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(
          "Could not encode [" + value.getClass().getName() + "] as JSON", ex);
    }
  }

  /**
   * Decode the given bytes to an instance of T using Jackson. The bytes must be the JSON string as
   * bytes.
   *
   * @param valueClass The type of class to deserialize the object to, the class must have the
   *     proper Jackson annotations for deserialization.
   * @param bytes The bytes to deserialize.
   * @return The decoded object
   * @throws IllegalArgumentException if the given value cannot be decoded to a T
   */
  public static <T> T decodeJson(Class<T> valueClass, akka.util.ByteString bytes) {
    return jsonSerializer.fromBytes(
        valueClass, new BytesPayload(bytes, jsonSerializer.contentTypeFor(valueClass)));
  }

  /**
   * Decode the given bytes to an instance of T using Jackson. The bytes must be the JSON string as
   * bytes.
   *
   * @param valueClass The type of class to deserialize the object to, the class must have the
   *     proper Jackson annotations for deserialization.
   * @param bytes The bytes to deserialize.
   * @return The decoded object
   * @throws IllegalArgumentException if the given value cannot be decoded to a T
   */
  public static <T> T decodeJson(Class<T> valueClass, byte[] bytes) {
    return decodeJson(valueClass, akka.util.ByteString.fromArrayUnsafe(bytes));
  }
}
