/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.agent.Agent.StreamEffect
import akka.javasdk.agent.MemoryProvider
import akka.javasdk.agent.ModelProvider
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.effect.SecondaryEffectImpl

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class AgentStreamEffectImpl
    extends StreamEffect.Builder
    with StreamEffect.OnSuccessBuilder
    with StreamEffect {
  // note that it's using the same effects as AgentEffectImpl
  import BaseAgentEffectBuilder._

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

  override def reply(message: String): AgentStreamEffectImpl =
    reply(message, Metadata.EMPTY)

  override def reply(message: String, metadata: Metadata): AgentStreamEffectImpl = {
    _secondaryEffect = MessageReplyImpl(message, metadata)
    this.asInstanceOf[AgentStreamEffectImpl]
  }

  override def error(description: String): AgentStreamEffectImpl = {
    _secondaryEffect = ErrorReplyImpl(description)
    this.asInstanceOf[AgentStreamEffectImpl]
  }

  def hasError(): Boolean =
    _secondaryEffect.isInstanceOf[ErrorReplyImpl]

  override def model(provider: ModelProvider): StreamEffect.Builder = {
    updateRequestModel(_.withProvider(provider))
    this
  }

  override def systemMessage(message: String): StreamEffect.Builder = {
    updateRequestModel(_.copy(systemMessage = ConstantSystemMessage(message)))
    this
  }

  override def systemMessageFromTemplate(templateId: String): StreamEffect.Builder = {
    updateRequestModel(_.copy(systemMessage = TemplateSystemMessage(templateId)))
    this
  }

  override def systemMessageFromTemplate(templateId: String, args: Object*): StreamEffect.Builder = {
    updateRequestModel(_.copy(systemMessage = TemplateSystemMessage(templateId, args)))
    this
  }

  override def userMessage(message: String): StreamEffect.OnSuccessBuilder = {
    updateRequestModel(_.copy(userMessage = message))
    this
  }

  override def memory(provider: MemoryProvider): StreamEffect.Builder = {
    updateRequestModel(_.withMemory(provider))
    this
  }

  override def thenReply(): AgentStreamEffectImpl =
    this.asInstanceOf[AgentStreamEffectImpl]

  override def thenReply(metadata: Metadata): AgentStreamEffectImpl = {
    updateRequestModel(_.copy(replyMetadata = metadata))
    this.asInstanceOf[AgentStreamEffectImpl]
  }

}
