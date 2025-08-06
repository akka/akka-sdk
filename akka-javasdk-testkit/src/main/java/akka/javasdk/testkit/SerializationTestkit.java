/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.impl.serialization.JsonSerializer;
import akka.runtime.sdk.spi.BytesPayload;
import akka.util.ByteString;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;

/** Helper class for serializing and deserializing objects for testing schema migration. */
public final class SerializationTestkit {

  private record SerializedPayload(String contentType, byte[] bytes) {}

  private static final JsonSerializer jsonSerializer = new JsonSerializer();

  public static <T> byte[] serialize(T value) {
    BytesPayload bytesPayload = jsonSerializer.toBytes(value);
    SerializedPayload serializedPayload =
        new SerializedPayload(bytesPayload.contentType(), bytesPayload.bytes().toArray());
    try {
      return jsonSerializer.objectMapper().writeValueAsBytes(serializedPayload);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Unexpected serialization error", e);
    }
  }

  public static <T> T deserialize(Class<T> valueClass, byte[] bytes) {
    try {
      SerializedPayload serializedPayload =
          jsonSerializer.objectMapper().readValue(bytes, SerializedPayload.class);
      return jsonSerializer.fromBytes(
          valueClass,
          new BytesPayload(
              ByteString.fromArray(serializedPayload.bytes), serializedPayload.contentType));
    } catch (IOException e) {
      throw new RuntimeException("Unexpected deserialization error", e);
    }
  }
}
