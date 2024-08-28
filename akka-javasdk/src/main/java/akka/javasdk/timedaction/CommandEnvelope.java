/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.timedaction;

import akka.javasdk.Metadata;
import akka.javasdk.impl.action.CommandEnvelopeImpl;

/** A command envelope. */
public interface CommandEnvelope<T> {
  /**
   * The metadata associated with the command.
   *
   * @return The metadata.
   */
  Metadata metadata();

  /**
   * The payload of the command.
   *
   * @return The payload.
   */
  T payload();

  /**
   * Create a command envelope.
   *
   * @param payload The payload of the command.
   * @return The command envelope.
   */
  static <T> CommandEnvelope<T> of(T payload) {
    return new CommandEnvelopeImpl<>(payload, Metadata.EMPTY);
  }

  /**
   * Create a command envelope.
   *
   * @param payload The payload of the command.
   * @param metadata The metadata associated with the command.
   * @return The command envelope.
   */
  static <T> CommandEnvelope<T> of(T payload, Metadata metadata) {
    return new CommandEnvelopeImpl<>(payload, metadata);
  }
}
