/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.impl.serialization.Serializer;
import akka.runtime.sdk.spi.BytesPayload;
import java.lang.reflect.Type;

/**
 * Internal helper to verify serialization/deserialization of entity events, state, commands, and
 * responses. Supports both JSON and Protobuf serialization formats.
 */
final class EntitySerializationChecker {

  private static Serializer serializer = new Serializer();

  static void verifySerDer(Object object, Object entity) {
    try {
      BytesPayload bytesPayload = serializer.toBytes(object);
      // For protobuf, we need to deserialize with the expected type
      // For JSON with type hints, fromBytes(bytesPayload) works
      if (serializer.isProtobuf(bytesPayload)) {
        serializer.fromBytes(object.getClass(), bytesPayload);
      } else {
        serializer.fromBytes(bytesPayload);
      }
    } catch (Exception e) {
      fail(object, entity, e);
    }
  }

  /** different deserialization for responses, state, and commands */
  static void verifySerDerWithExpectedType(Class<?> expectedClass, Object object, Object entity) {
    try {
      BytesPayload bytesPayload = serializer.toBytes(object);
      serializer.fromBytes(expectedClass, bytesPayload);
    } catch (Exception e) {
      fail(object, entity, e);
    }
  }

  /** different deserialization for responses, state, and commands */
  static void verifySerDerWithExpectedType(Type expectedType, Object object, Object entity) {
    try {
      BytesPayload bytesPayload = serializer.toBytes(object);
      serializer.fromBytes(expectedType, bytesPayload);
    } catch (Exception e) {
      fail(object, entity, e);
    }
  }

  private static void fail(Object object, Object entity, Exception e) {
    throw new RuntimeException(
        "Failed to serialize or deserialize "
            + object.getClass().getName()
            + ". Make sure that all events, commands, responses and state are serializable for "
            + entity.getClass().getName(),
        e);
  }
}
