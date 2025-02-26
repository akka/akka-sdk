/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.impl.serialization.JsonSerializer;
import akka.runtime.sdk.spi.BytesPayload;

final class EntitySerializationChecker {

  private static JsonSerializer jsonSerializer = new JsonSerializer();

  static void verifySerDer(Object object, Object entity) {
    try {
      BytesPayload bytesPayload = jsonSerializer.toBytes(object);
      jsonSerializer.fromBytes(bytesPayload);
    } catch (Exception e) {
      fail(object, entity, e);
    }
  }

  /**
   * different deserialization for responses, state, and commands
   */
  static void verifySerDerWithExpectedType(Class<?> expectedClass, Object object, Object entity) {
    try {
      BytesPayload bytesPayload = jsonSerializer.toBytes(object);
      jsonSerializer.fromBytes(expectedClass, bytesPayload);
    } catch (Exception e) {
      fail(object, entity, e);
    }
  }

  private static void fail(Object object, Object entity, Exception e) {
    throw new RuntimeException("Failed to serialize or deserialize " + object.getClass().getName() + ". Make sure that all events, commands, responses and state are serializable for " + entity.getClass().getName(), e);
  }
}
