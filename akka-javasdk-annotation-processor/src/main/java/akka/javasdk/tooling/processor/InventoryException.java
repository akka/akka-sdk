/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

/** A failure to read the service config for the service inventory. */
final class InventoryException extends Exception {

  InventoryException(String message) {
    super(message);
  }

  InventoryException(String message, Throwable cause) {
    super(message, cause);
  }
}
