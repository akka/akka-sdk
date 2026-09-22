/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util
import java.util.function

import scala.annotation.nowarn
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.jdk.FunctionConverters.enrichAsScalaFromFunction

import akka.annotation.InternalApi
import akka.javasdk.CommandException
import akka.javasdk.Metadata
import akka.javasdk.agent
import akka.javasdk.agent.Agent.Effect
import akka.javasdk.agent.Agent.Effect.Builder
import akka.javasdk.agent.Agent.Effect.FailureBuilder
import akka.javasdk.agent.Agent.Effect.JudgmentBuilder
import akka.javasdk.agent.Agent.Effect.MappingFailureBuilder
import akka.javasdk.agent.Agent.Effect.MappingResponseBuilder
import akka.javasdk.agent.Agent.Effect.OnSuccessBuilder
import akka.javasdk.agent.ContentLoader
import akka.javasdk.agent.ImageLoader
import akka.javasdk.agent.Judgment
import akka.javasdk.agent.JudgmentModelProvider
import akka.javasdk.agent.MemoryProvider
import akka.javasdk.agent.ModelProvider
import akka.javasdk.agent.Question
import akka.javasdk.agent.RemoteMcpTools
import akka.javasdk.agent.UserMessage
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.PrimaryEffectImpl
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.RequestJudgment
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.RequestModel
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.effect.SecondaryEffectImpl

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
        userMessage = UserMessage.from(""),
        responseType = classOf[String],
        includeJsonSchema = false,
        responseMapping = None,
        failureMapping = None,
        replyMetadata = Metadata.EMPTY,
        memoryProvider = MemoryProvider.fromConfig(),
        toolInstancesOrClasses = Seq.empty,
        mcpTools = Seq.empty,
        contentLoader = None)
  }

  sealed trait SystemMessage
  final case class ConstantSystemMessage(message: String) extends SystemMessage
  final case class TemplateSystemMessage(templateId: String, args: Object*) extends SystemMessage

  final case class RequestModel(
      modelProvider: ModelProvider,
      systemMessage: SystemMessage,
      userMessage: UserMessage,
      responseType: Class[_],
      includeJsonSchema: Boolean,
      responseMapping: Option[Function1[Any, Any]],
      failureMapping: Option[Throwable => Any],
      replyMetadata: Metadata,
      memoryProvider: MemoryProvider,
      toolInstancesOrClasses: Seq[AnyRef],
      mcpTools: Seq[RemoteMcpTools],
      contentLoader: Option[ContentLoader])
      extends PrimaryEffectImpl {

    def withProvider(provider: ModelProvider): RequestModel =
      copy(modelProvider = provider)

    def withMemory(provider: MemoryProvider): RequestModel =
      copy(memoryProvider = provider)

    def withContentLoader(loader: ContentLoader): RequestModel =
      copy(contentLoader = Some(loader))

    def addTool(tool: AnyRef): RequestModel = {
      copy(toolInstancesOrClasses = this.toolInstancesOrClasses :+ tool)
    }
    def addTools(tools: Seq[AnyRef]): RequestModel = {
      copy(toolInstancesOrClasses = this.toolInstancesOrClasses ++ tools)
    }

    def addMcpTools(tools: Seq[RemoteMcpTools]): RequestModel =
      copy(mcpTools = mcpTools ++ tools)

  }

  object RequestJudgment {
    val empty: RequestJudgment =
      RequestJudgment(
        provider = JudgmentModelProvider.fromConfig(),
        state = None,
        questions = Vector.empty,
        responseMapping = None,
        failureMapping = None,
        replyMetadata = Metadata.EMPTY)

    def validateComplete(request: RequestJudgment): Unit = {
      if (request.state.isEmpty)
        throw new IllegalStateException("A judgment request needs a state, call state(...) before thenReply()")
      if (request.questions.isEmpty)
        throw new IllegalStateException(
          "A judgment request needs at least one question, call question(...) before thenReply()")
    }
  }

  /**
   * A structured judgment request. The state is serialized to JSON when the effect is converted to the SPI.
   */
  final case class RequestJudgment(
      provider: JudgmentModelProvider,
      state: Option[AnyRef],
      questions: Vector[(String, Question)],
      responseMapping: Option[Function1[Any, Any]],
      failureMapping: Option[Throwable => Any],
      replyMetadata: Metadata)
      extends PrimaryEffectImpl

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
      case _: RequestJudgment =>
        throw new IllegalStateException("A judgment request cannot be combined with a model request")
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

  override def error[T](message: String): BaseAgentEffectBuilder[T] = {
    error(new CommandException(message))
  }

  override def error[T](commandException: CommandException): BaseAgentEffectBuilder[T] = {
    _secondaryEffect = ErrorReplyImpl(commandException)
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

  override def userMessage(text: String): OnSuccessBuilder = {
    updateRequestModel(_.copy(userMessage = UserMessage.from(text)))
    this
  }

  override def userMessage(message: agent.UserMessage): OnSuccessBuilder = {

    updateRequestModel(_.copy(userMessage = message))
    this
  }

  override def memory(provider: MemoryProvider): Builder = {
    updateRequestModel(_.withMemory(provider))
    this
  }

  override def mcpTools(tools: RemoteMcpTools, moreTools: RemoteMcpTools*): Builder = {
    updateRequestModel(_.addMcpTools(tools +: moreTools))
    this
  }

  override def mcpTools(tools: util.List[RemoteMcpTools]): Builder = {
    updateRequestModel(_.addMcpTools(tools.asScala.toSeq))
    this
  }

  @nowarn("msg=deprecated")
  override def imageLoader(loader: ImageLoader): Builder = {
    updateRequestModel(_.withContentLoader(loader))
    this
  }

  override def contentLoader(loader: ContentLoader): Builder = {
    updateRequestModel(_.withContentLoader(loader))
    this
  }

  override def thenReply(): BaseAgentEffectBuilder[String] =
    this.asInstanceOf[BaseAgentEffectBuilder[String]]

  override def thenReply(metadata: Metadata): BaseAgentEffectBuilder[String] = {
    updateRequestModel(_.copy(replyMetadata = metadata))
    this.asInstanceOf[BaseAgentEffectBuilder[String]]
  }

  override def responseAs[T](responseType: Class[T]): MappingResponseBuilder[T] = {
    updateRequestModel(_.copy(responseType = responseType, includeJsonSchema = false))
    new MappingResponseEffectBuilder(_primaryEffect.asInstanceOf[RequestModel])
  }

  override def responseConformsTo[T](responseType: Class[T]): MappingResponseBuilder[T] = {
    require(responseType != classOf[String], "Response schema not supported for plain String response")
    updateRequestModel(_.copy(responseType = responseType, includeJsonSchema = true))
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

  override def tools(tool: AnyRef, tools: AnyRef*): Builder = {
    updateRequestModel(_.addTool(tool).addTools(tools))
    this
  }

  override def judgment(): JudgmentBuilder = {
    if (_primaryEffect != NoPrimaryEffect || _secondaryEffect != NoSecondaryEffectImpl)
      throw new IllegalStateException(
        "judgment() must be the first call on the effects builder, it cannot be combined with a model request or a reply")
    new JudgmentEffectBuilder(RequestJudgment.empty)
  }

  override def tools(toolInstancesOrClasses: util.List[AnyRef]): Builder = {
    updateRequestModel(_.addTools(toolInstancesOrClasses.asScala.toSeq))
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

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class JudgmentEffectBuilder(private var _primaryEffect: RequestJudgment)
    extends JudgmentBuilder
    with Effect[Judgment]
    with AgentEffectImpl {

  private def update(f: RequestJudgment => RequestJudgment): Unit =
    _primaryEffect = f(_primaryEffect)

  override def model(provider: JudgmentModelProvider): JudgmentBuilder = {
    if (provider == null) throw new IllegalArgumentException("provider must not be null")
    update(_.copy(provider = provider))
    this
  }

  override def state(state: AnyRef): JudgmentBuilder = {
    state match {
      case null =>
        throw new IllegalArgumentException("state must not be null")
      case _: java.lang.Number | _: java.lang.Boolean | _: java.lang.Character =>
        throw new IllegalArgumentException(
          s"state must be a String, an object or a collection, not [${state.getClass.getName}]")
      case _ =>
    }
    update(_.copy(state = Some(state)))
    this
  }

  override def question(key: String, question: Question): JudgmentBuilder = {
    if (key == null || key.isBlank) throw new IllegalArgumentException("question key must not be blank")
    if (question == null) throw new IllegalArgumentException(s"question [$key] must not be null")
    if (_primaryEffect.questions.exists(_._1 == key))
      throw new IllegalArgumentException(s"duplicate question key [$key]")
    question match {
      case choice: Question.Choice if choice.options.isEmpty =>
        throw new IllegalArgumentException(s"choice question [$key] must have at least one option")
      case _ =>
    }
    update(req => req.copy(questions = req.questions :+ (key -> question)))
    this
  }

  override def map[T](mapper: function.Function[Judgment, T]): MappingFailureBuilder[T] =
    new JudgmentMappingEffectBuilder[T](
      _primaryEffect.copy(responseMapping = Some(mapper.asScala.asInstanceOf[Function1[Any, Any]])))

  override def onFailure(exceptionHandler: function.Function[Throwable, Judgment]): FailureBuilder[Judgment] =
    new JudgmentMappingEffectBuilder[Judgment](
      _primaryEffect.copy(failureMapping = Some(exceptionHandler.asScala.asInstanceOf[Function1[Throwable, Any]])))

  override def thenReply(): Effect[Judgment] = {
    RequestJudgment.validateComplete(_primaryEffect)
    this
  }

  override def thenReply(metadata: Metadata): Effect[Judgment] = {
    RequestJudgment.validateComplete(_primaryEffect)
    update(_.copy(replyMetadata = metadata))
    this
  }

  override def primaryEffect: PrimaryEffectImpl = _primaryEffect

  override def secondaryEffect: SecondaryEffectImpl = NoSecondaryEffectImpl
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class JudgmentMappingEffectBuilder[Reply](private var _primaryEffect: RequestJudgment)
    extends MappingFailureBuilder[Reply]
    with FailureBuilder[Reply]
    with Effect[Reply]
    with AgentEffectImpl {

  override def onFailure(exceptionHandler: function.Function[Throwable, Reply]): FailureBuilder[Reply] = {
    _primaryEffect =
      _primaryEffect.copy(failureMapping = Some(exceptionHandler.asScala.asInstanceOf[Function1[Throwable, Any]]))
    this
  }

  override def thenReply(): Effect[Reply] = {
    RequestJudgment.validateComplete(_primaryEffect)
    this
  }

  override def thenReply(metadata: Metadata): Effect[Reply] = {
    RequestJudgment.validateComplete(_primaryEffect)
    _primaryEffect = _primaryEffect.copy(replyMetadata = metadata)
    this
  }

  override def primaryEffect: PrimaryEffectImpl = _primaryEffect

  override def secondaryEffect: SecondaryEffectImpl = NoSecondaryEffectImpl
}
