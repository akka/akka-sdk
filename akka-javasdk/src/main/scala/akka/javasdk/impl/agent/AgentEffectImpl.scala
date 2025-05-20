/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.agent.Agent.Effect
import akka.javasdk.agent.Agent.Effect.Builder
import akka.javasdk.agent.Agent.Effect.OnSuccessBuilder
import akka.javasdk.agent.ModelProvider
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
      RequestModel(
        modelProvider = ModelProvider.fromConfig(),
        systemMessage = ConstantSystemMessage(""),
        userMessage = "",
        responseType = classOf[String],
        replyMetadata = Metadata.EMPTY)
  }

  sealed trait SystemMessage
  final case class ConstantSystemMessage(message: String) extends SystemMessage
  final case class TemplateSystemMessage(templateId: String) extends SystemMessage

  final case class RequestModel(
      modelProvider: ModelProvider,
      systemMessage: SystemMessage,
      userMessage: String,
      responseType: Class[_],
      replyMetadata: Metadata)
      extends PrimaryEffectImpl {

    def withProvider(provider: ModelProvider): RequestModel =
      copy(modelProvider = provider)
  }

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

  private def updateRequestModel(f: RequestModel => RequestModel): Unit = {
    _primaryEffect match {
      case NoPrimaryEffect =>
        _primaryEffect = f(RequestModel.empty)
      case req: RequestModel =>
        _primaryEffect = f(req)
    }
  }

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

  override def modelProvider(provider: ModelProvider): Builder = {
    updateRequestModel(_.withProvider(provider))
    this
  }

  override def systemMessage(message: String): Builder = {
    updateRequestModel(_.copy(systemMessage = ConstantSystemMessage(message)))
    this
  }

  override def systemMessageFromTemplate(templateId: String): Builder = {
    updateRequestModel(_.copy(systemMessage = TemplateSystemMessage(templateId)))
    this
  }

  override def userMessage(message: String): OnSuccessBuilder = {
    updateRequestModel(_.copy(userMessage = message))
    this
  }

  override def thenReply(): AgentEffectImpl[String] =
    this.asInstanceOf[AgentEffectImpl[String]]

  override def thenReply(metadata: Metadata): AgentEffectImpl[String] = {
    updateRequestModel(_.copy(replyMetadata = metadata))
    this.asInstanceOf[AgentEffectImpl[String]]
  }

  override def thenReplyAs[T](responseType: Class[T]): AgentEffectImpl[T] =
    thenReplyAs[T](responseType, Metadata.EMPTY)

  override def thenReplyAs[T](responseType: Class[T], metadata: Metadata): AgentEffectImpl[T] = {
    updateRequestModel(_.copy(responseType = responseType, replyMetadata = metadata))
    this.asInstanceOf[AgentEffectImpl[T]]
  }

}
