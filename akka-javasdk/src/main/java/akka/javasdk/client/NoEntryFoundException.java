/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

/** Thrown for a query where the query result would be a single entry but none was found. */
public final class NoEntryFoundException extends RuntimeException {
  // FIXME this matches what we have now but is still not great DX, Option or always a collection
  // would be better?

  public NoEntryFoundException(String message) {
    super(message);
  }
}
