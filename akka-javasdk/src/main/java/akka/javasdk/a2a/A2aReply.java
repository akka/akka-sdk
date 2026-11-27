/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import java.util.List;
import java.util.Map;

/**
 * The answer of an A2A endpoint to a message.
 *
 * @param metadata plain JSON values: strings, numbers, booleans, null, and maps and lists of those
 */
public record A2aReply(List<A2aPart> parts, Map<String, Object> metadata) {

  /** A reply with one text part. */
  public static A2aReply text(String text) {
    return of(A2aPart.text(text));
  }

  /**
   * A reply with one data part.
   *
   * @param value plain JSON values: strings, numbers, booleans, null, and maps and lists of those
   */
  public static A2aReply data(Object value) {
    return of(A2aPart.data(value));
  }

  /** A reply with the given parts. */
  public static A2aReply of(A2aPart... parts) {
    return new A2aReply(List.of(parts), Map.of());
  }

  /** A copy of this reply with the given metadata. */
  public A2aReply withMetadata(Map<String, Object> metadata) {
    return new A2aReply(parts, metadata);
  }
}
