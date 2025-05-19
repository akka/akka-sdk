/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.agent.Agent
import akka.javasdk.agent.Agent.Effect
import akka.javasdk.agent.Agent.Effect.Builder
import akka.javasdk.agent.Agent.Effect.OnSuccessBuilder
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.effect.SecondaryEffectImpl
import akka.runtime.sdk.spi.SpiAgent

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object AgentEffectImpl {
  sealed trait PrimaryEffectImpl
  object RequestModel {
    val empty: RequestModel =
      RequestModel(
        model = SpiAgent.Model.empty,
        systemMessage = "",
        userMessage = "",
        responseType = classOf[String],
        replyMetadata = Metadata.EMPTY)
  }
  final case class RequestModel(
      model: SpiAgent.Model,
      systemMessage: String,
      userMessage: String,
      responseType: Class[_],
      replyMetadata: Metadata)
      extends PrimaryEffectImpl {

    def withProvider(provider: Agent.ModelProvider): RequestModel = {
      val spiProvider = provider match {
        case Agent.ModelProvider.ANTHROPIC => SpiAgent.ModelProvider.Anthropic
        case Agent.ModelProvider.OPEN_AI   => SpiAgent.ModelProvider.OpenAi
      }
      copy(model = model.withProvider(spiProvider))
    }

    def withApiKey(apiKey: String): RequestModel =
      copy(model = model.withApiKey(apiKey))
    def withModelName(modelName: String): RequestModel =
      copy(model = model.withModelName(modelName))
    def withBaseUrl(baseUrl: String): RequestModel =
      copy(model = model.withBaseUrl(baseUrl))
    def withTemperature(value: Double): RequestModel =
      copy(model = model.withTemperature(value))
    def withTopP(value: Double): RequestModel =
      copy(model = model.withTopP(value))
    def withMaxTokens(value: Int): RequestModel =
      copy(model = model.withMaxTokens(value))
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

  override def modelProvider(provider: Agent.ModelProvider): Builder = {
    updateRequestModel(_.withProvider(provider))
    this
  }

  override def modelApiKey(key: String): Builder = {
    updateRequestModel(_.withApiKey(key))
    this
  }

  override def modelName(name: String): Builder = {
    updateRequestModel(_.withModelName(name))
    this
  }

  override def modelBaseUrl(url: String): Builder = {
    updateRequestModel(_.withBaseUrl(url))
    this
  }

  override def modelTemperature(value: Double): Builder = {
    updateRequestModel(_.withTemperature(value))
    this
  }

  override def modelTopP(value: Double): Builder = {
    updateRequestModel(_.withTopP(value))
    this
  }

  override def modelMaxTokens(value: Int): Builder = {
    updateRequestModel(_.withMaxTokens(value))
    this
  }

  override def systemMessage(message: String): Builder = {
    updateRequestModel(_.copy(systemMessage = message))
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
