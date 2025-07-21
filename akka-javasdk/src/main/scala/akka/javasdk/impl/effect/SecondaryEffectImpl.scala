/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.effect

import akka.annotation.InternalApi
import akka.javasdk.CommandException
import akka.javasdk.Metadata

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] sealed trait SecondaryEffectImpl

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] case object NoSecondaryEffectImpl extends SecondaryEffectImpl {}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class MessageReplyImpl[T](message: T, metadata: Metadata) extends SecondaryEffectImpl {
  if (message == null)
    throw new IllegalArgumentException("Reply must not be null")
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class ErrorReplyImpl(commandException: CommandException) extends SecondaryEffectImpl {}
