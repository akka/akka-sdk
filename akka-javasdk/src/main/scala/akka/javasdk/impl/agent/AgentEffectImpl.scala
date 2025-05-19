/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.agent.Agent.Effect
import akka.javasdk.agent.Agent.Effect.Builder
import akka.javasdk.agent.Agent.Effect.OnSuccessBuilder
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.effect.SecondaryEffectImpl

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object AgentEffectImpl {
  sealed trait PrimaryEffectImpl
  object RequestModel {
    val empty: RequestModel =
      RequestModel(systemMessage = "", userMessage = "", responseType = classOf[String], replyMetadata = Metadata.EMPTY)
  }
  final case class RequestModel(
      systemMessage: String,
      userMessage: String,
      responseType: Class[_],
      replyMetadata: Metadata)
      extends PrimaryEffectImpl
  case object NoPrimaryEffect extends PrimaryEffectImpl
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class AgentEffectImpl[Reply] extends Builder with OnSuccessBuilder with Effect[Reply] {
  import AgentEffectImpl._

  private var _primaryEffect: PrimaryEffectImpl = NoPrimaryEffect
  private var _secondaryEffect: SecondaryEffectImpl = NoSecondaryEffectImpl

  def primaryEffect: PrimaryEffectImpl = _primaryEffect

  def secondaryEffect: SecondaryEffectImpl =
    _secondaryEffect

  override def reply[T](message: T): AgentEffectImpl[T] =
    reply(message, Metadata.EMPTY)

  override def reply[T](message: T, metadata: Metadata): AgentEffectImpl[T] = {
    _secondaryEffect = MessageReplyImpl(message, metadata)
    this.asInstanceOf[AgentEffectImpl[T]]
  }

  override def error[T](description: String): AgentEffectImpl[T] = {
    _secondaryEffect = ErrorReplyImpl(description)
    this.asInstanceOf[AgentEffectImpl[T]]
  }

  def hasError(): Boolean =
    _secondaryEffect.isInstanceOf[ErrorReplyImpl]

  override def systemMessage(message: String): Builder = {
    _primaryEffect match {
      case NoPrimaryEffect =>
        _primaryEffect = RequestModel.empty.copy(systemMessage = message)
      case req: RequestModel =>
        _primaryEffect = req.copy(systemMessage = message)
    }
    this
  }

  override def userMessage(message: String): OnSuccessBuilder = {
    _primaryEffect match {
      case NoPrimaryEffect =>
        _primaryEffect = RequestModel.empty.copy(userMessage = message)
      case req: RequestModel =>
        _primaryEffect = req.copy(userMessage = message)
    }
    this
  }

  override def thenReply(): AgentEffectImpl[String] =
    this.asInstanceOf[AgentEffectImpl[String]]

  override def thenReply(metadata: Metadata): AgentEffectImpl[String] = {
    _primaryEffect match {
      case NoPrimaryEffect =>
        _primaryEffect = RequestModel.empty.copy(replyMetadata = metadata)
      case req: RequestModel =>
        _primaryEffect = req.copy(replyMetadata = metadata)
    }
    this.asInstanceOf[AgentEffectImpl[String]]
  }

  override def thenReplyAs[T](responseType: Class[T]): AgentEffectImpl[T] =
    thenReplyAs[T](responseType, Metadata.EMPTY)

  override def thenReplyAs[T](responseType: Class[T], metadata: Metadata): AgentEffectImpl[T] = {
    _primaryEffect match {
      case NoPrimaryEffect =>
        _primaryEffect = RequestModel.empty.copy(responseType = responseType, replyMetadata = metadata)
      case req: RequestModel =>
        _primaryEffect = req.copy(responseType = responseType, replyMetadata = metadata)
    }
    this.asInstanceOf[AgentEffectImpl[T]]
  }

}
