/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.function
import scala.jdk.FunctionConverters.enrichAsScalaFromFunction
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.agent.Agent.Effect
import akka.javasdk.agent.Agent.Effect.Builder
import akka.javasdk.agent.Agent.Effect.FailureBuilder
import akka.javasdk.agent.Agent.Effect.MappingFailureBuilder
import akka.javasdk.agent.Agent.Effect.MappingResponseBuilder
import akka.javasdk.agent.Agent.Effect.OnSuccessBuilder
import akka.javasdk.agent.MemoryProvider
import akka.javasdk.agent.ModelProvider
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.PrimaryEffectImpl
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.RequestModel
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.effect.SecondaryEffectImpl

import java.util
import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object BaseAgentEffectBuilder {
  sealed trait PrimaryEffectImpl

  object RequestModel {
    val empty: RequestModel =
      RequestModel(
        modelProvider = ModelProvider.fromConfig(),
        systemMessage = ConstantSystemMessage(""),
        userMessage = "",
        responseType = classOf[String],
        responseMapping = None,
        failureMapping = None,
        replyMetadata = Metadata.EMPTY,
        memoryProvider = MemoryProvider.fromConfig(),
        Seq.empty,
        Seq.empty)
  }

  sealed trait SystemMessage
  final case class ConstantSystemMessage(message: String) extends SystemMessage
  final case class TemplateSystemMessage(templateId: String, args: Object*) extends SystemMessage

  final case class RequestModel(
      modelProvider: ModelProvider,
      systemMessage: SystemMessage,
      userMessage: String,
      responseType: Class[_],
      responseMapping: Option[Function1[Any, Any]],
      failureMapping: Option[Throwable => Any],
      replyMetadata: Metadata,
      memoryProvider: MemoryProvider,
      toolInstances: Seq[Any],
      toolClasses: Seq[Class[_]])
      extends PrimaryEffectImpl {

    def withProvider(provider: ModelProvider): RequestModel =
      copy(modelProvider = provider)

    def withMemory(provider: MemoryProvider): RequestModel =
      copy(memoryProvider = provider)

    def addToolInstances(toolInstances: Seq[Any]): RequestModel = {
      copy(toolInstances = this.toolInstances ++ toolInstances)
    }

    def addToolClasses(toolClasses: Seq[Class[_]]): RequestModel = {
      copy(toolClasses = this.toolClasses ++ toolClasses)
    }
  }

  case object NoPrimaryEffect extends PrimaryEffectImpl
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] trait AgentEffectImpl {
  def primaryEffect: PrimaryEffectImpl
  def secondaryEffect: SecondaryEffectImpl
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class BaseAgentEffectBuilder[Reply]
    extends Builder
    with OnSuccessBuilder
    with Effect[Reply]
    with AgentEffectImpl {
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

  override def reply[T](message: T): BaseAgentEffectBuilder[T] =
    reply(message, Metadata.EMPTY)

  override def reply[T](message: T, metadata: Metadata): BaseAgentEffectBuilder[T] = {
    _secondaryEffect = MessageReplyImpl(message, metadata)
    this.asInstanceOf[BaseAgentEffectBuilder[T]]
  }

  override def error[T](description: String): BaseAgentEffectBuilder[T] = {
    _secondaryEffect = ErrorReplyImpl(description)
    this.asInstanceOf[BaseAgentEffectBuilder[T]]
  }

  def hasError(): Boolean =
    _secondaryEffect.isInstanceOf[ErrorReplyImpl]

  override def model(provider: ModelProvider): Builder = {
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

  override def systemMessageFromTemplate(templateId: String, args: Object*): Builder = {
    updateRequestModel(_.copy(systemMessage = TemplateSystemMessage(templateId, args)))
    this
  }

  override def userMessage(message: String): OnSuccessBuilder = {
    updateRequestModel(_.copy(userMessage = message))
    this
  }

  override def memory(provider: MemoryProvider): Builder = {
    updateRequestModel(_.withMemory(provider))
    this
  }

  override def thenReply(): BaseAgentEffectBuilder[String] =
    this.asInstanceOf[BaseAgentEffectBuilder[String]]

  override def thenReply(metadata: Metadata): BaseAgentEffectBuilder[String] = {
    updateRequestModel(_.copy(replyMetadata = metadata))
    this.asInstanceOf[BaseAgentEffectBuilder[String]]
  }

  override def responseAs[T](responseType: Class[T]): MappingResponseBuilder[T] = {
    updateRequestModel(_.copy(responseType = responseType))
    new MappingResponseEffectBuilder(_primaryEffect.asInstanceOf[RequestModel])
  }

  override def map[T](mapper: function.Function[String, T]): MappingResponseBuilder[T] = {
    updateRequestModel(_.copy(responseMapping = Some(mapper.asScala.asInstanceOf[Function1[Any, Any]])))
    new MappingResponseEffectBuilder(_primaryEffect.asInstanceOf[RequestModel])
  }

  override def onFailure(exceptionHandler: function.Function[Throwable, String]): FailureBuilder[String] = {
    updateRequestModel(_.copy(failureMapping = Some(exceptionHandler.asScala.asInstanceOf[Function1[Throwable, Any]])))
    new MappingResponseEffectBuilder(_primaryEffect.asInstanceOf[RequestModel])
  }

  override def tools(toolInstances: Any*): Builder = {
    updateRequestModel(_.addToolInstances(toolInstances))
    this
  }

  override def tools(toolInstances: util.List[AnyRef]): Builder = {
    updateRequestModel(_.addToolInstances(toolInstances.asScala.toSeq))
    this
  }

  override def toolClasses(toolClasses: Class[_]*): Builder = {
    updateRequestModel(_.addToolClasses(toolClasses))
    this
  }

  override def toolClasses(toolClasses: util.List[Class[_]]): Builder = {
    updateRequestModel(_.addToolClasses(toolClasses.asScala.toSeq))
    this
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class MappingResponseEffectBuilder[Reply](private var _primaryEffect: RequestModel)
    extends MappingResponseBuilder[Reply]
    with MappingFailureBuilder[Reply]
    with FailureBuilder[Reply]
    with Effect[Reply]
    with AgentEffectImpl {

  private def updateRequestModel(f: RequestModel => RequestModel): Unit = {
    _primaryEffect = f(_primaryEffect)
  }

  override def map[T](mapper: function.Function[Reply, T]): MappingFailureBuilder[T] = {
    updateRequestModel(_.copy(responseMapping = Some(mapper.asScala.asInstanceOf[Function1[Any, Any]])))
    this.asInstanceOf[MappingResponseEffectBuilder[T]]
  }

  override def onFailure(exceptionHandler: function.Function[Throwable, Reply]): FailureBuilder[Reply] = {
    updateRequestModel(_.copy(failureMapping = Some(exceptionHandler.asScala.asInstanceOf[Function1[Throwable, Any]])))
    this.asInstanceOf[MappingResponseEffectBuilder[Reply]]
  }

  override def thenReply(): MappingResponseEffectBuilder[Reply] =
    this.asInstanceOf[MappingResponseEffectBuilder[Reply]]

  override def thenReply(metadata: Metadata): MappingResponseEffectBuilder[Reply] = {
    updateRequestModel(_.copy(replyMetadata = metadata))
    this.asInstanceOf[MappingResponseEffectBuilder[Reply]]
  }

  override def primaryEffect: PrimaryEffectImpl = _primaryEffect

  override def secondaryEffect: SecondaryEffectImpl = NoSecondaryEffectImpl
}
