/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

/** The tool arguments and responses the ledger record tests decode. */
public final class ToolPayloads {

  private ToolPayloads() {}

  public record Order(String id, String status) {}

  public record GetOrder(String orderId) {}
}
