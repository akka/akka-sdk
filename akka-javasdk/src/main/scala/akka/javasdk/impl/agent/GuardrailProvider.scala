/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.concurrent.CompletionStage

import scala.annotation.nowarn
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.util.Failure
import scala.util.control.NonFatal

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.Tracing
import akka.javasdk.agent.Classification
import akka.javasdk.agent.ClassifierClient
import akka.javasdk.agent.Decision
import akka.javasdk.agent.Decision.Allow
import akka.javasdk.agent.Decision.Deny
import akka.javasdk.agent.Decision.Fail
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.GuardrailContext
import akka.javasdk.agent.MessageContent
import akka.javasdk.agent.ModelGuardrail
import akka.javasdk.agent.ModelGuardrail.CallContext.ConversationMessage
import akka.javasdk.agent.SimilarityGuard
import akka.javasdk.agent.TextGuardrail
import akka.javasdk.agent.ToolGuardrail
import akka.javasdk.impl.agent.ConfiguredGuardrail.UseFor
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.runtime.sdk.spi.SpiAgent
import com.typesafe.config.Config
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object GuardrailProvider {

  /**
   * INTERNAL API
   */
  @InternalApi private[javasdk] final class ToolGuardrailCallContextImpl(
      val agentId: String,
      val toolName: String,
      val toolCallId: String,
      val arguments: String,
      val sessionId: String,
      telemetryContext: Option[OtelContext],
      tracerFactory: () => Tracer)
      extends ToolGuardrail.CallContext {

    override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[javasdk] final class ModelGuardrailCallContextImpl(
      contentList: java.util.List[MessageContent],
      override val agentId: String,
      override val sessionId: String,
      override val modelName: String,
      boundaryValue: ModelGuardrail.CallContext.Boundary,
      conversationValue: java.util.Optional[ModelGuardrail.CallContext.ConversationContext],
      telemetryContext: Option[OtelContext],
      tracerFactory: () => Tracer)
      extends ModelGuardrail.CallContext {

    private val singleTextContent: Option[String] =
      contentList.asScala.toSeq match {
        case Seq(t: MessageContent.TextMessageContent) => Some(t.text())
        case _                                         => None
      }

    override def boundary(): ModelGuardrail.CallContext.Boundary = boundaryValue

    override def conversation(): java.util.Optional[ModelGuardrail.CallContext.ConversationContext] =
      conversationValue

    override val textOnly: Boolean = singleTextContent.isDefined

    override val text: String = singleTextContent.getOrElse("")

    override def contents(): java.util.List[MessageContent] = contentList

    override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)
  }

  final case class GuardrailEntry(configuredGuardrail: ConfiguredGuardrail, guardrail: Guardrail)

  final class AgentGuardrails(val entries: Seq[GuardrailEntry], tracerFactory: () => Tracer) {
    private def collectGuardrails(useFor: UseFor): Seq[SpiAgent.Guardrail] =
      entries.collect {
        case entry if entry.configuredGuardrail.useFor.contains(useFor) => toSpiGuardrail(entry, tracerFactory)
      }

    val modelRequestGuardrails: Seq[SpiAgent.Guardrail] =
      collectGuardrails(UseFor.ModelRequest)
    val modelResponseGuardrails: Seq[SpiAgent.Guardrail] =
      collectGuardrails(UseFor.ModelResponse)
    val mcpToolRequestGuardrails: Seq[SpiAgent.Guardrail] =
      collectGuardrails(UseFor.McpToolRequest)
    val mcpToolResponseGuardrails: Seq[SpiAgent.Guardrail] =
      collectGuardrails(UseFor.McpToolResponse)
    val beforeModelCallGuardrails: Seq[SpiAgent.Guardrail] =
      collectGuardrails(UseFor.BeforeModelCall)
    val beforeAgentResponseGuardrails: Seq[SpiAgent.Guardrail] =
      collectGuardrails(UseFor.BeforeAgentResponse)

    // The model-side guardrails grouped by their SPI boundaries, as handed to the runtime.
    // MCP and before-tool-call guardrails travel on their descriptors instead.
    val boundGuardrails: SpiAgent.BoundGuardrails =
      SpiAgent.BoundGuardrails
        .add(SpiAgent.GuardrailBoundary.ModelRequest, modelRequestGuardrails)
        .add(SpiAgent.GuardrailBoundary.ModelResponse, modelResponseGuardrails)
        .add(SpiAgent.GuardrailBoundary.BeforeModelCall, beforeModelCallGuardrails)
        .add(SpiAgent.GuardrailBoundary.BeforeAgentResponse, beforeAgentResponseGuardrails)

    // The before-tool-call guardrails applicable to the given tool. An entry with an empty `tools`
    // set applies to every tool on the agent; otherwise only to the named tools.
    private def beforeToolCallGuardrails(toolName: String): Seq[SpiAgent.Guardrail] =
      entries.collect {
        case entry
            if entry.configuredGuardrail.useFor.contains(UseFor.BeforeToolCall) &&
              (entry.configuredGuardrail.tools.isEmpty || entry.configuredGuardrail.tools.contains(toolName)) =>
          toSpiGuardrail(entry, tracerFactory)
      }

    // Returns the given tool descriptors with their applicable before-tool-call guardrails attached.
    // The runtime evaluates these at the before-tool-call boundary for in-process function tools.
    def withToolGuardrails(toolDescriptors: Seq[SpiAgent.ToolDescriptor]): Seq[SpiAgent.ToolDescriptor] =
      toolDescriptors.map { descriptor =>
        val guardrails = beforeToolCallGuardrails(descriptor.name)
        if (guardrails.isEmpty) descriptor
        else new SpiAgent.ToolDescriptor(descriptor.name, descriptor.description, descriptor.schema, guardrails)
      }
  }

  @nowarn("cat=deprecation")
  final class TextGuardrailAdapter(entry: GuardrailEntry, guardrail: TextGuardrail) extends SpiAgent.Guardrail {

    override def evaluate(content: SpiAgent.Guardrail.Content): Future[SpiAgent.Guardrail.Result] = {
      content match {
        case textContent: SpiAgent.Guardrail.TextContent =>
          val result = guardrail.evaluate(textContent.text)
          Future.successful(new SpiAgent.Guardrail.Result(result.passed, result.explanation))
        case other =>
          Future.failed(
            new IllegalArgumentException(s"Only text content is supported, but was [${other.getClass.getName}]"))
      }
    }

    override val name: String = entry.configuredGuardrail.name
    override val category: String = entry.configuredGuardrail.category
    override val reportOnly: Boolean = entry.configuredGuardrail.reportOnly
  }

  final class ToolGuardrailAdapter(entry: GuardrailEntry, guardrail: ToolGuardrail, tracerFactory: () => Tracer)
      extends SpiAgent.Guardrail {

    override def evaluate(content: SpiAgent.Guardrail.Content): Future[SpiAgent.Guardrail.Result] =
      content match {
        case toolCall: SpiAgent.Guardrail.ToolCallContent =>
          decideSafely(
            guardrail.decideAsync(
              new ToolGuardrailCallContextImpl(
                toolCall.agentId,
                toolCall.toolName,
                toolCall.toolCallId,
                toolCall.arguments,
                toolCall.sessionId,
                Option(toolCall.telemetryContext),
                tracerFactory)))
        case other =>
          Future.failed(
            new IllegalArgumentException(s"Only tool call content is supported, but was [${other.getClass.getName}]"))
      }

    override val name: String = entry.configuredGuardrail.name
    override val category: String = entry.configuredGuardrail.category
    override val reportOnly: Boolean = entry.configuredGuardrail.reportOnly
  }

  final class ModelGuardrailAdapter(entry: GuardrailEntry, guardrail: ModelGuardrail, tracerFactory: () => Tracer)
      extends SpiAgent.Guardrail {

    override def evaluate(content: SpiAgent.Guardrail.Content): Future[SpiAgent.Guardrail.Result] =
      content match {
        case agentResponse: SpiAgent.Guardrail.AgentResponseContent =>
          val contents = java.util.List.of[MessageContent](AgentImpl.fromSpiMessageContent(agentResponse.content))
          decideSafely(
            guardrail.decideAsync(
              new ModelGuardrailCallContextImpl(
                contents,
                agentResponse.agentId,
                agentResponse.sessionId,
                agentResponse.modelName,
                boundaryValue = ModelGuardrail.CallContext.Boundary.BEFORE_AGENT_RESPONSE,
                conversationValue = java.util.Optional.empty(),
                Option(agentResponse.telemetryContext),
                tracerFactory)))
        case modelCall: SpiAgent.Guardrail.ModelCallContent =>
          // contents() carries the newest frame entering this model call: the messages after the
          // last AI message (the user message on the first call, the tool results afterwards)
          val newestFrame = modelCall.messages.reverse
            .takeWhile(!_.isInstanceOf[SpiAgent.ContextMessage.AiMessage])
            .reverse
          val contents = newestFrame
            .flatMap {
              case u: SpiAgent.ContextMessage.UserMessage             => u.contents
              case t: SpiAgent.ContextMessage.ToolCallResponseMessage => t.contents
              case _                                                  => Seq.empty
            }
            .map(AgentImpl.fromSpiMessageContent)
            .asJava
          val conversation = new ModelGuardrail.CallContext.ConversationContext(
            modelCall.systemMessage,
            modelCall.messages.map(toConversationMessage).asJava)
          decideSafely(
            guardrail.decideAsync(
              new ModelGuardrailCallContextImpl(
                contents,
                modelCall.agentId,
                modelCall.sessionId,
                modelCall.modelName,
                boundaryValue = ModelGuardrail.CallContext.Boundary.BEFORE_MODEL_CALL,
                conversationValue = java.util.Optional.of(conversation),
                Option(modelCall.telemetryContext),
                tracerFactory)))
        case other =>
          Future.failed(
            new IllegalArgumentException(
              s"Only model call and agent response content is supported, but was [${other.getClass.getName}]"))
      }

    override val name: String = entry.configuredGuardrail.name
    override val category: String = entry.configuredGuardrail.category
    override val reportOnly: Boolean = entry.configuredGuardrail.reportOnly
  }

  // Maps a conversation entry from its SPI representation onto the public guardrail-facing ADT,
  // so guardrail implementations never see SPI types.
  private def toConversationMessage(message: SpiAgent.ContextMessage): ConversationMessage =
    message match {
      case u: SpiAgent.ContextMessage.UserMessage =>
        new ConversationMessage.UserMessage(u.contents.map(AgentImpl.fromSpiMessageContent).asJava)
      case a: SpiAgent.ContextMessage.AiMessage =>
        new ConversationMessage.AiMessage(
          Option(a.content).getOrElse(""),
          a.toolRequests.map(tr => new ConversationMessage.ToolCallRequest(tr.id, tr.name, tr.arguments)).asJava)
      case t: SpiAgent.ContextMessage.ToolCallResponseMessage =>
        new ConversationMessage.ToolCallResult(t.id, t.name, t.contents.map(AgentImpl.fromSpiMessageContent).asJava)
    }

  // A guardrail can fail to reach a verdict in three ways: throw from decide(...), return a failed
  // CompletionStage, or complete with an explicit Decision.Fail. All three are treated as if it had
  // returned new Decision.Fail(message, throwable). A null stage NPEs here and lands on the same path.
  //
  // TODO: thrown exceptions and explicit new Decision.Fail(...) currently collapse onto the same
  // failed-Future path. Pending an internal decision on fail-closed (thrown) vs configurable
  // fail-closed/fail-open (explicit error) — keep them separable when that lands.
  private def decideSafely(decide: => CompletionStage[Decision]): Future[SpiAgent.Guardrail.Result] = {
    val decision =
      try decide.asScala
      catch { case NonFatal(t) => Future.failed(t) }

    decision
      .recover { case NonFatal(t) => new Decision.Fail(Option(t.getMessage).getOrElse(t.getClass.getName), t) }(
        ExecutionContext.parasitic)
      .flatMap(decisionToSpiResult)(ExecutionContext.parasitic)
  }

  // Decision.Fail becomes a failed Future so the cause Throwable flows through the runtime's
  // existing handling in AgentGuardrailInteractions, where it ends up as the cause of the
  // AgentException reaching the user's onFailure mapper.
  private def decisionToSpiResult(decision: Decision): Future[SpiAgent.Guardrail.Result] =
    decision match {
      case a: Allow => Future.successful(new SpiAgent.Guardrail.Result(true, a.reason))
      case d: Deny  => Future.successful(new SpiAgent.Guardrail.Result(false, d.reason))
      case e: Fail  => Future.failed(new RuntimeException(e.reason, e.cause))
      case null     => Future.failed(new NullPointerException("Guardrail returned a null Decision"))
    }

  @nowarn("cat=deprecation")
  private def toSpiGuardrail(entry: GuardrailEntry, tracerFactory: () => Tracer): SpiAgent.Guardrail =
    entry.guardrail match {
      case g: SimilarityGuard => toSpiSimilarityGuard(g, entry.configuredGuardrail)
      case g: TextGuardrail   => new TextGuardrailAdapter(entry, g)
      case g: ToolGuardrail   => new ToolGuardrailAdapter(entry, g, tracerFactory)
      case g: ModelGuardrail  => new ModelGuardrailAdapter(entry, g, tracerFactory)
    }

  private def toSpiSimilarityGuard(g: SimilarityGuard, c: ConfiguredGuardrail): SpiAgent.SimilarityGuard =
    new SpiAgent.SimilarityGuard(c.name, c.category, c.reportOnly, g.badExamplesResourceDir, g.threshold)

  // Each guardrail interface is pinned to its own use-for values. The legacy values are reachable
  // only by the deprecated TextGuardrail; new boundaries only by the new interfaces.
  // FIXME: extend ToolGuardrail to the MCP tool request/response boundaries (MCP-as-tool-call
  // unification is a separate issue). That requires ToolGuardrailAdapter to build a
  // ToolGuardrailContext from the MCP TextContent and ToolSideUseFor to also include
  // McpToolRequest/McpToolResponse.
  private val ToolSideUseFor: Set[UseFor] = Set(UseFor.BeforeToolCall)
  private val ModelSideUseFor: Set[UseFor] = Set(UseFor.BeforeModelCall, UseFor.BeforeAgentResponse)
  private val TextSideUseFor: Set[UseFor] =
    Set(UseFor.ModelRequest, UseFor.ModelResponse, UseFor.McpToolRequest, UseFor.McpToolResponse)

  // Default classifierClient for call sites (and tests) that don't supply one; any call fails
  // descriptively instead of silently returning something.
  private val NoClassifiersConfigured: ClassifierClient = new ClassifierClient {
    override def classify(name: String, input: String): Classification =
      throw new IllegalArgumentException(s"No classifier configured with name [$name] (no ClassifierClient available)")
    override def classifyAsync(name: String, input: String): CompletionStage[Classification] =
      throw new IllegalArgumentException(s"No classifier configured with name [$name] (no ClassifierClient available)")
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class GuardrailProvider(
    system: ActorSystem[_],
    applicationConfig: Config,
    tracerFactory: () => Tracer,
    // Defaulted so existing call sites (and tests) that don't care about classifiers are unaffected;
    // SdkRunner always passes the real ClassifierClient.
    classifierClient: ClassifierClient = GuardrailProvider.NoClassifiersConfigured) {
  import GuardrailProvider._

  private val log = LoggerFactory.getLogger(classOf[GuardrailProvider])

  lazy val configuredGuardrails: Seq[ConfiguredGuardrail] = {
    GuardrailSettings(applicationConfig.getConfig("akka.javasdk.agent.guardrails")).configuredGuardrails
  }

  private lazy val guardrailsByComponentId: Map[String, Seq[GuardrailEntry]] = {
    configuredGuardrails.foldLeft(Map.empty[String, Vector[GuardrailEntry]]) {
      case (acc, config) if config.useFor.nonEmpty =>
        config.agents.foldLeft(acc) { case (acc2, componentId) =>
          acc2.updated(componentId, acc2.getOrElse(componentId, Vector.empty) :+ createGuardrail(config))
        }
      case (acc, _) => acc
    }
  }

  private lazy val guardrailsByRole: Map[String, Seq[GuardrailEntry]] = {
    configuredGuardrails.foldLeft(Map.empty[String, Vector[GuardrailEntry]]) {
      case (acc, config) if config.useFor.nonEmpty =>
        config.agentRoles.foldLeft(acc) { case (acc2, role) =>
          acc2.updated(role, acc2.getOrElse(role, Vector.empty) :+ createGuardrail(config))
        }
      case (acc, _) => acc
    }
  }

  private def createGuardrail(c: ConfiguredGuardrail): GuardrailEntry = {
    val guardrailContext = new GuardrailContextImpl(c.name, c.config, classifierClient)
    val instance = system.dynamicAccess
      .createInstanceFor[Guardrail](c.implementationClass, (classOf[GuardrailContext] -> guardrailContext) :: Nil)
      .recoverWith { case _: ClassNotFoundException | _: NoSuchMethodException =>
        system.dynamicAccess.createInstanceFor[Guardrail](c.implementationClass, Nil)
      }
      .recoverWith { case _: ClassNotFoundException | _: NoSuchMethodException | _: ClassCastException =>
        Failure(
          new IllegalArgumentException(s"Guardrail [${c.name}] must implement [${classOf[Guardrail].getName}] and " +
          s"optionally have a constructor with GuardrailContext parameter"))
      }
      .get

    validateSingleInterface(c.name, instance)
    warnOnDeprecatedUseFor(c)

    val expanded = expandWildcard(c, instance)
    validateUseFor(expanded, instance)
    GuardrailEntry(expanded, instance)
  }

  // Guardrail is sealed (permits TextGuardrail, ToolGuardrail, ModelGuardrail) so any instance
  // implements at least one. Reject classes that implement more than one — the dispatch in
  // toSpiGuardrail is otherwise ambiguous.
  @nowarn("cat=deprecation")
  private def validateSingleInterface(guardrailName: String, instance: Guardrail): Unit = {
    val implemented = Seq(
      Option.when(instance.isInstanceOf[TextGuardrail])(classOf[TextGuardrail].getName),
      Option.when(instance.isInstanceOf[ToolGuardrail])(classOf[ToolGuardrail].getName),
      Option.when(instance.isInstanceOf[ModelGuardrail])(classOf[ModelGuardrail].getName)).flatten

    if (implemented.size > 1)
      throw new IllegalArgumentException(
        s"Guardrail [$guardrailName] must implement only one of " +
        s"[${classOf[ToolGuardrail].getName}] or [${classOf[ModelGuardrail].getName}], " +
        s"but [${instance.getClass.getName}] implements [${implemented.mkString(", ")}]")
  }

  // "*" expands to the boundaries the guardrail's interface may bind to.
  @nowarn("cat=deprecation")
  private def expandWildcard(c: ConfiguredGuardrail, instance: Guardrail): ConfiguredGuardrail =
    if (!c.useFor.contains(UseFor.Wildcard)) c
    else {
      val expansion = instance match {
        case _: ToolGuardrail  => ToolSideUseFor
        case _: ModelGuardrail => ModelSideUseFor
        // TextGuardrail, including the built-in SimilarityGuard
        case _ => TextSideUseFor
      }
      c.copy(useFor = c.useFor - UseFor.Wildcard ++ expansion)
    }

  // Runs on the DECLARED use-for set (before wildcard expansion) so a "*" declaration
  // does not trigger the warning.
  private def warnOnDeprecatedUseFor(c: ConfiguredGuardrail): Unit = {
    val deprecated = c.useFor.intersect(Set[UseFor](UseFor.ModelRequest, UseFor.ModelResponse))
    if (deprecated.nonEmpty)
      log.warn(
        "Guardrail [{}] uses deprecated use-for value(s) [{}]. Implement " +
        "akka.javasdk.agent.ModelGuardrail and bind it to [before-model-call] (for model-request) " +
        "or [before-agent-response] (for model-response) instead.",
        c.name,
        deprecated.mkString(", "))
  }

  // Each interface may only bind to its own use-for values (see the pinned sets above).
  // Validation runs on the wildcard-expanded set, so a "*" declaration is always valid.
  @nowarn("cat=deprecation")
  private def validateUseFor(c: ConfiguredGuardrail, instance: Guardrail): Unit =
    instance match {
      case _: ToolGuardrail if !c.useFor.subsetOf(ToolSideUseFor) =>
        val invalid = c.useFor.diff(ToolSideUseFor)
        throw new IllegalArgumentException(
          s"ToolGuardrail [${c.name}] can only be bound to the before-tool-call use-for value, " +
          s"but was also bound to [${invalid.mkString(", ")}]")
      case _: ModelGuardrail if !c.useFor.subsetOf(ModelSideUseFor) =>
        val invalid = c.useFor.diff(ModelSideUseFor)
        throw new IllegalArgumentException(
          s"ModelGuardrail [${c.name}] can only be bound to model-side use-for values " +
          s"(before-model-call, before-agent-response), but was also bound to [${invalid.mkString(", ")}]")
      case _: TextGuardrail if !c.useFor.subsetOf(TextSideUseFor) =>
        val invalid = c.useFor.diff(TextSideUseFor)
        throw new IllegalArgumentException(
          s"TextGuardrail [${c.name}] can only be bound to the deprecated use-for values " +
          s"(model-request, model-response, mcp-tool-request, mcp-tool-response), " +
          s"but was also bound to [${invalid.mkString(", ")}]")
      case _ => // ok
    }

  def validate(): Unit = {
    guardrailsByComponentId
    guardrailsByRole
  }

  /**
   * The guardrails for a specific agent component.
   */
  def agentGuardrails(componentId: String, role: Option[String]): AgentGuardrails = {
    val byComponentId = guardrailsByComponentId.getOrElse(componentId, Vector.empty) ++ guardrailsByComponentId
      .getOrElse("*", Vector.empty)
    val all =
      role match {
        case Some(r) =>
          val byRole = guardrailsByRole.getOrElse(r, Vector.empty) ++ guardrailsByRole.getOrElse("*", Vector.empty)
          byComponentId ++ byRole
        case None =>
          byComponentId
      }
    // remove duplicates, only one per name since the name is the unique key
    val deduplicated =
      all.foldLeft(Map.empty[String, GuardrailEntry]) { case (acc, entry) =>
        val name = entry.configuredGuardrail.name
        if (acc.contains(name))
          acc
        else
          acc.updated(name, entry)
      }
    new AgentGuardrails(deduplicated.values.toVector, tracerFactory)
  }

}
