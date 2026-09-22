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
import akka.javasdk.agent.AgentResponseGuardrail
import akka.javasdk.agent.Classification
import akka.javasdk.agent.ClassifierClient
import akka.javasdk.agent.Decision
import akka.javasdk.agent.Decision.Allow
import akka.javasdk.agent.Decision.Deny
import akka.javasdk.agent.Decision.Fail
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.Guardrail.Message
import akka.javasdk.agent.Guardrail.Message.AiMessage
import akka.javasdk.agent.GuardrailContext
import akka.javasdk.agent.ModelCallGuardrail
import akka.javasdk.agent.SimilarityGuard
import akka.javasdk.agent.TextGuardrail
import akka.javasdk.agent.ToolCallGuardrail
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
  @InternalApi private[javasdk] final class ToolCallGuardrailCallContextImpl(
      override val agentId: String,
      override val toolName: String,
      override val toolCallId: String,
      override val arguments: String,
      override val sessionId: String,
      telemetryContext: Option[OtelContext],
      tracerFactory: () => Tracer)
      extends ToolCallGuardrail.CallContext {

    override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[javasdk] final class ModelCallGuardrailCallContextImpl(
      override val systemMessage: String,
      spiMessages: Seq[SpiAgent.ContextMessage],
      override val agentId: String,
      override val sessionId: String,
      override val modelName: String,
      telemetryContext: Option[OtelContext],
      tracerFactory: () => Tracer)
      extends ModelCallGuardrail.CallContext {

    override lazy val messages: java.util.List[Message] =
      spiMessages.map(toMessage).asJava

    override lazy val newMessages: java.util.List[Message] =
      spiMessages
        .drop(spiMessages.lastIndexWhere(_.isInstanceOf[SpiAgent.ContextMessage.AiMessage]) + 1)
        .map(toMessage)
        .asJava

    override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[javasdk] final class AgentResponseGuardrailCallContextImpl(
      override val reply: Message.AiMessage,
      override val agentId: String,
      override val sessionId: String,
      override val modelName: String,
      telemetryContext: Option[OtelContext],
      tracerFactory: () => Tracer)
      extends AgentResponseGuardrail.CallContext {

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
      entries.collect { case entry @ GuardrailEntry(_, _: ModelCallGuardrail) =>
        toSpiGuardrail(entry, tracerFactory)
      }
    val beforeAgentResponseGuardrails: Seq[SpiAgent.Guardrail] =
      entries.collect { case entry @ GuardrailEntry(_, _: AgentResponseGuardrail) =>
        toSpiGuardrail(entry, tracerFactory)
      }

    // The model-side guardrails grouped by their SPI boundaries, as handed to the runtime.
    // MCP and before-tool-call guardrails travel on their descriptors instead.
    val boundGuardrails: SpiAgent.BoundGuardrails =
      SpiAgent.BoundGuardrails
        .add(SpiAgent.GuardrailBoundary.ModelRequest, modelRequestGuardrails)
        .add(SpiAgent.GuardrailBoundary.ModelResponse, modelResponseGuardrails)
        .add(SpiAgent.GuardrailBoundary.BeforeModelCall, beforeModelCallGuardrails)
        .add(SpiAgent.GuardrailBoundary.BeforeAgentResponse, beforeAgentResponseGuardrails)

    // The ToolCallGuardrails applicable to the given tool. An entry with an empty `tools` set
    // applies to every tool on the agent; otherwise only to the named tools.
    private def beforeToolCallGuardrails(toolName: String): Seq[SpiAgent.Guardrail] =
      entries.collect {
        case entry @ GuardrailEntry(configured, _: ToolCallGuardrail)
            if configured.tools.isEmpty || configured.tools.contains(toolName) =>
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

  final class ToolCallGuardrailAdapter(entry: GuardrailEntry, guardrail: ToolCallGuardrail, tracerFactory: () => Tracer)
      extends SpiAgent.Guardrail {

    override def evaluate(content: SpiAgent.Guardrail.Content): Future[SpiAgent.Guardrail.Result] =
      content match {
        case toolCall: SpiAgent.Guardrail.ToolCallContent =>
          decideSafely(
            guardrail.decideAsync(
              new ToolCallGuardrailCallContextImpl(
                toolCall.agentId,
                toolCall.toolName,
                Option(toolCall.toolCallId).getOrElse(""),
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

  final class ModelCallGuardrailAdapter(
      entry: GuardrailEntry,
      guardrail: ModelCallGuardrail,
      tracerFactory: () => Tracer)
      extends SpiAgent.Guardrail {

    override def evaluate(content: SpiAgent.Guardrail.Content): Future[SpiAgent.Guardrail.Result] =
      content match {
        case modelCall: SpiAgent.Guardrail.ModelCallContent =>
          decideSafely(
            guardrail.decideAsync(
              new ModelCallGuardrailCallContextImpl(
                modelCall.systemMessage,
                modelCall.messages,
                modelCall.agentId,
                modelCall.sessionId,
                modelCall.modelName,
                Option(modelCall.telemetryContext),
                tracerFactory)))
        case other =>
          Future.failed(
            new IllegalArgumentException(s"Only model call content is supported, but was [${other.getClass.getName}]"))
      }

    override val name: String = entry.configuredGuardrail.name
    override val category: String = entry.configuredGuardrail.category
    override val reportOnly: Boolean = entry.configuredGuardrail.reportOnly
  }

  final class AgentResponseGuardrailAdapter(
      entry: GuardrailEntry,
      guardrail: AgentResponseGuardrail,
      tracerFactory: () => Tracer)
      extends SpiAgent.Guardrail {

    override def evaluate(content: SpiAgent.Guardrail.Content): Future[SpiAgent.Guardrail.Result] =
      content match {
        case agentResponse: SpiAgent.Guardrail.AgentResponseContent =>
          agentResponse.content match {
            case text: SpiAgent.TextMessageContent =>
              val reply = new Message.AiMessage(text.text, java.util.List.of())
              decideSafely(
                guardrail.decideAsync(
                  new AgentResponseGuardrailCallContextImpl(
                    reply,
                    agentResponse.agentId,
                    agentResponse.sessionId,
                    agentResponse.modelName,
                    Option(agentResponse.telemetryContext),
                    tracerFactory)))
            case other =>
              Future.failed(
                new IllegalArgumentException(
                  s"Only a text agent response is supported, but was [${other.getClass.getName}]"))
          }
        case other =>
          Future.failed(
            new IllegalArgumentException(
              s"Only agent response content is supported, but was [${other.getClass.getName}]"))
      }

    override val name: String = entry.configuredGuardrail.name
    override val category: String = entry.configuredGuardrail.category
    override val reportOnly: Boolean = entry.configuredGuardrail.reportOnly
  }

  // Maps a conversation entry from its SPI representation onto the public guardrail-facing ADT,
  // so guardrail implementations never see SPI types.
  private def toMessage(message: SpiAgent.ContextMessage): Message =
    message match {
      case u: SpiAgent.ContextMessage.UserMessage =>
        new Message.UserMessage(u.contents.map(AgentImpl.fromSpiMessageContent).asJava)
      case a: SpiAgent.ContextMessage.AiMessage =>
        new Message.AiMessage(
          Option(a.content).getOrElse(""),
          a.toolRequests.map(tr => new AiMessage.ToolCallRequest(tr.id, tr.name, tr.arguments)).asJava)
      case t: SpiAgent.ContextMessage.ToolCallResponseMessage =>
        new Message.ToolCallResponse(t.id, t.name, t.contents.map(AgentImpl.fromSpiMessageContent).asJava)
    }

  // A guardrail can fail to reach a verdict in three ways: throw from decide(...), return a failed
  // CompletionStage, or complete with an explicit Decision.Fail. All three are treated as if it had
  // returned new Decision.Fail(message, throwable). A null stage NPEs here and lands on the same path.
  // decisionToSpiResult rejects a null Decision.
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
      case g: SimilarityGuard        => toSpiSimilarityGuard(g, entry.configuredGuardrail)
      case g: TextGuardrail          => new TextGuardrailAdapter(entry, g)
      case g: ToolCallGuardrail      => new ToolCallGuardrailAdapter(entry, g, tracerFactory)
      case g: ModelCallGuardrail     => new ModelCallGuardrailAdapter(entry, g, tracerFactory)
      case g: AgentResponseGuardrail => new AgentResponseGuardrailAdapter(entry, g, tracerFactory)
    }

  private def toSpiSimilarityGuard(g: SimilarityGuard, c: ConfiguredGuardrail): SpiAgent.SimilarityGuard =
    new SpiAgent.SimilarityGuard(c.name, c.category, c.reportOnly, g.badExamplesResourceDir, g.threshold)

  // The use-for values a TextGuardrail can bind to. "*" expands to all of them.
  // FIXME: extend ToolCallGuardrail to the MCP tool request/response boundaries (MCP-as-tool-call
  // unification is a separate issue). That requires ToolCallGuardrailAdapter to build a
  // ToolCallGuardrail.CallContext from the MCP TextContent.
  private val TextGuardrailUseFor: Set[UseFor] =
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
    configuredGuardrails.foldLeft(Map.empty[String, Vector[GuardrailEntry]]) { case (acc, config) =>
      config.agents.foldLeft(acc) { case (acc2, componentId) =>
        acc2.updated(componentId, acc2.getOrElse(componentId, Vector.empty) :+ createGuardrail(config))
      }
    }
  }

  private lazy val guardrailsByRole: Map[String, Seq[GuardrailEntry]] = {
    configuredGuardrails.foldLeft(Map.empty[String, Vector[GuardrailEntry]]) { case (acc, config) =>
      config.agentRoles.foldLeft(acc) { case (acc2, role) =>
        acc2.updated(role, acc2.getOrElse(role, Vector.empty) :+ createGuardrail(config))
      }
    }
  }

  @nowarn("cat=deprecation")
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

    instance match {
      case _: TextGuardrail =>
        warnOnDeprecatedUseFor(c)
        val expanded = expandWildcard(c)
        validateTextGuardrailUseFor(expanded)
        GuardrailEntry(expanded, instance)

      case _ =>
        rejectUseFor(c, instance)
        GuardrailEntry(c, instance)
    }
  }

  // Fails when the instance implements more than one guardrail interface.
  // toSpiGuardrail maps each instance to exactly one adapter.
  @nowarn("cat=deprecation")
  private def validateSingleInterface(guardrailName: String, instance: Guardrail): Unit = {
    val implemented = Seq(
      Option.when(instance.isInstanceOf[TextGuardrail])(classOf[TextGuardrail].getName),
      Option.when(instance.isInstanceOf[ToolCallGuardrail])(classOf[ToolCallGuardrail].getName),
      Option.when(instance.isInstanceOf[ModelCallGuardrail])(classOf[ModelCallGuardrail].getName),
      Option.when(instance.isInstanceOf[AgentResponseGuardrail])(classOf[AgentResponseGuardrail].getName)).flatten

    if (implemented.size > 1)
      throw new IllegalArgumentException(
        s"Guardrail [$guardrailName] must implement only one of " +
        s"[${classOf[ToolCallGuardrail].getName}], [${classOf[ModelCallGuardrail].getName}] or " +
        s"[${classOf[AgentResponseGuardrail].getName}], " +
        s"but [${instance.getClass.getName}] implements [${implemented.mkString(", ")}]")
  }

  private def expandWildcard(c: ConfiguredGuardrail): ConfiguredGuardrail =
    if (!c.useFor.contains(UseFor.Wildcard)) c
    else c.copy(useFor = c.useFor - UseFor.Wildcard ++ TextGuardrailUseFor)

  // Runs on the DECLARED use-for set (before wildcard expansion) so a "*" declaration
  // does not trigger the warning.
  private def warnOnDeprecatedUseFor(c: ConfiguredGuardrail): Unit = {
    val deprecated = c.useFor.intersect(Set[UseFor](UseFor.ModelRequest, UseFor.ModelResponse))
    if (deprecated.nonEmpty)
      log.warn(
        "Guardrail [{}] uses deprecated use-for value(s) [{}]. Implement " +
        "akka.javasdk.agent.ModelCallGuardrail (for model-request) or " +
        "akka.javasdk.agent.AgentResponseGuardrail (for model-response) instead.",
        c.name,
        deprecated.mkString(", "))
  }

  private def validateTextGuardrailUseFor(c: ConfiguredGuardrail): Unit =
    if (c.useFor.isEmpty)
      throw new IllegalArgumentException(
        s"TextGuardrail [${c.name}] must define use-for with one or more of " +
        s"[model-request, model-response, mcp-tool-request, mcp-tool-response] or [*]")

  // ToolCallGuardrail, ModelCallGuardrail and AgentResponseGuardrail bind to their boundary by type.
  private def rejectUseFor(c: ConfiguredGuardrail, instance: Guardrail): Unit =
    if (c.config.hasPath("use-for"))
      throw new IllegalArgumentException(
        s"Guardrail [${c.name}] must not define use-for. [${instance.getClass.getName}] binds to its " +
        "boundary by type: ToolCallGuardrail before each tool call, ModelCallGuardrail before each " +
        "model call, and AgentResponseGuardrail on the final agent reply.")

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
