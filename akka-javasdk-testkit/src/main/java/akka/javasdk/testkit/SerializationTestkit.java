/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.impl.serialization.Serializer;
import akka.runtime.sdk.spi.BytesPayload;
import akka.util.ByteString;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;

/**
 * Helper class for serializing and deserializing objects for testing schema migration. Supports
 * both JSON and Protobuf serialization formats.
 */
public final class SerializationTestkit {

  private record SerializedPayload(String contentType, byte[] bytes) {}

  private static final Serializer serializer = new Serializer();

  /**
   * Serialize a value to bytes. Automatically uses protobuf format for protobuf messages and JSON
   * format for other types.
   */
  public static <T> byte[] serialize(T value) {
    BytesPayload bytesPayload = serializer.toBytes(value);
    SerializedPayload serializedPayload =
        new SerializedPayload(bytesPayload.contentType(), bytesPayload.bytes().toArray());
    try {
      return serializer.json().objectMapper().writeValueAsBytes(serializedPayload);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Unexpected serialization error", e);
    }
  }

  /**
   * Deserialize bytes to the expected type. Automatically detects the content type and uses the
   * appropriate deserializer.
   */
  public static <T> T deserialize(Class<T> valueClass, byte[] bytes) {
    try {
      SerializedPayload serializedPayload =
          serializer.json().objectMapper().readValue(bytes, SerializedPayload.class);
      return serializer.fromBytes(
          valueClass,
          new BytesPayload(
              ByteString.fromArray(serializedPayload.bytes), serializedPayload.contentType));
    } catch (IOException e) {
      throw new RuntimeException("Unexpected deserialization error", e);
    }
  }
}
