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
import akka.javasdk.agent.ModelCallSimilarityGuard
import akka.javasdk.agent.SimilarityGuard
import akka.javasdk.agent.TextGuardrail
import akka.javasdk.agent.ToolCallGuardrail
import akka.javasdk.impl.agent.ConfiguredGuardrail.UseFor
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiAgentGuardrails
import akka.runtime.sdk.spi.SpiConfiguredGuardrail
import akka.runtime.sdk.spi.SpiGuardrail
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
    @nowarn("cat=deprecation")
    private def collectLegacyGuardrails(useFor: UseFor): Seq[SpiAgent.Guardrail] =
      entries.collect {
        case entry @ GuardrailEntry(configured, g: TextGuardrail) if configured.useFor.contains(useFor) =>
          toLegacySpiGuardrail(entry, g)
      }

    val legacyModelRequestGuardrails: Seq[SpiAgent.Guardrail] =
      collectLegacyGuardrails(UseFor.ModelRequest)
    val legacyModelResponseGuardrails: Seq[SpiAgent.Guardrail] =
      collectLegacyGuardrails(UseFor.ModelResponse)
    val hasLegacyModelGuardrails: Boolean =
      legacyModelRequestGuardrails.nonEmpty || legacyModelResponseGuardrails.nonEmpty

    val legacyMcpToolRequestGuardrails: Seq[SpiAgent.Guardrail] =
      collectLegacyGuardrails(UseFor.McpToolRequest)
    val legacyMcpToolResponseGuardrails: Seq[SpiAgent.Guardrail] =
      collectLegacyGuardrails(UseFor.McpToolResponse)

    val guardrails: SpiAgentGuardrails =
      new SpiAgentGuardrails(
        modelCallGuardrails = entries.collect {
          case GuardrailEntry(configured, g: ModelCallSimilarityGuard) =>
            new SpiGuardrail.SimilarityGuard(toSettings(configured), g.badExamplesResourceDir, g.threshold)
          case GuardrailEntry(configured, g: ModelCallGuardrail) =>
            new ModelCallGuardrailAdapter(toSettings(configured), g, tracerFactory)
        },
        agentResponseGuardrails = entries.collect { case GuardrailEntry(configured, g: AgentResponseGuardrail) =>
          new AgentResponseGuardrailAdapter(toSettings(configured), g, tracerFactory)
        })

    // The ToolCallGuardrails applicable to the given tool. An entry with an empty `tools` set
    // applies to every tool on the agent; otherwise only to the named tools.
    private def toolCallGuardrails(toolName: String): Seq[SpiGuardrail.ToolCall] =
      entries.collect {
        case GuardrailEntry(configured, g: ToolCallGuardrail)
            if configured.tools.isEmpty || configured.tools.contains(toolName) =>
          new ToolCallGuardrailAdapter(toSettings(configured), g, tracerFactory)
      }

    /** The given tool descriptors with their tool-call guardrails attached. */
    def withToolGuardrails(toolDescriptors: Seq[SpiAgent.ToolDescriptor]): Seq[SpiAgent.ToolDescriptor] =
      toolDescriptors.map { descriptor =>
        val guardrails = toolCallGuardrails(descriptor.name)
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

  final class ToolCallGuardrailAdapter(
      override val settings: SpiGuardrail.Settings,
      guardrail: ToolCallGuardrail,
      tracerFactory: () => Tracer)
      extends SpiGuardrail.ToolCall {

    override def decide(ctx: SpiGuardrail.ToolCallContext): Future[SpiGuardrail.Decision] =
      toSpiDecision(
        guardrail.decideAsync(
          new ToolCallGuardrailCallContextImpl(
            ctx.agentId,
            ctx.toolName,
            Option(ctx.toolCallId).getOrElse(""),
            ctx.arguments,
            ctx.sessionId,
            Option(ctx.telemetryContext),
            tracerFactory)))
  }

  final class ModelCallGuardrailAdapter(
      override val settings: SpiGuardrail.Settings,
      guardrail: ModelCallGuardrail,
      tracerFactory: () => Tracer)
      extends SpiGuardrail.ModelCall {

    override def decide(ctx: SpiGuardrail.ModelCallContext): Future[SpiGuardrail.Decision] =
      toSpiDecision(
        guardrail.decideAsync(
          new ModelCallGuardrailCallContextImpl(
            ctx.systemMessage,
            ctx.messages,
            ctx.agentId,
            ctx.sessionId,
            ctx.modelName,
            Option(ctx.telemetryContext),
            tracerFactory)))
  }

  final class AgentResponseGuardrailAdapter(
      override val settings: SpiGuardrail.Settings,
      guardrail: AgentResponseGuardrail,
      tracerFactory: () => Tracer)
      extends SpiGuardrail.AgentResponse {

    override def decide(ctx: SpiGuardrail.AgentResponseContext): Future[SpiGuardrail.Decision] =
      toSpiDecision(
        guardrail.decideAsync(
          new AgentResponseGuardrailCallContextImpl(
            new Message.AiMessage(ctx.response, java.util.List.of()),
            ctx.agentId,
            ctx.sessionId,
            ctx.modelName,
            Option(ctx.telemetryContext),
            tracerFactory)))
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

  private def toSpiDecision(decision: CompletionStage[Decision]): Future[SpiGuardrail.Decision] =
    if (decision == null)
      Future.failed(new NullPointerException("Guardrail returned a null CompletionStage"))
    else
      decision.asScala.map {
        case allow: Allow => new SpiGuardrail.Allow(allow.reason)
        case deny: Deny   => new SpiGuardrail.Deny(deny.reason)
        case fail: Fail   => new SpiGuardrail.Fail(fail.reason, Option(fail.cause))
        case null         => null
      }(ExecutionContext.parasitic)

  private def toSettings(c: ConfiguredGuardrail): SpiGuardrail.Settings =
    new SpiGuardrail.Settings(c.name, c.category, c.reportOnly)

  @nowarn("cat=deprecation")
  private def toLegacySpiGuardrail(entry: GuardrailEntry, guardrail: TextGuardrail): SpiAgent.Guardrail =
    guardrail match {
      case g: SimilarityGuard => toSpiSimilarityGuard(g, entry.configuredGuardrail)
      case g                  => new TextGuardrailAdapter(entry, g)
    }

  @nowarn("cat=deprecation")
  private def toSpiSimilarityGuard(g: SimilarityGuard, c: ConfiguredGuardrail): SpiAgent.SimilarityGuard =
    new SpiAgent.SimilarityGuard(c.name, c.category, c.reportOnly, g.badExamplesResourceDir, g.threshold)

  // The use-for values a TextGuardrail can bind to. "*" expands to all of them.
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
      case textGuardrail: TextGuardrail =>
        warnOnDeprecatedUseFor(c, textGuardrail)
        val expanded = expandWildcard(c)
        validateTextGuardrailUseFor(expanded)
        GuardrailEntry(expanded, instance)

      case _ =>
        rejectUseFor(c, instance)
        GuardrailEntry(c, instance)
    }
  }

  // Fails when the instance implements more than one guardrail interface.
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

  // Runs on the DECLARED use-for set (before wildcard expansion).
  @nowarn("cat=deprecation")
  private def warnOnDeprecatedUseFor(c: ConfiguredGuardrail, instance: TextGuardrail): Unit =
    instance match {
      // FIXME: warn about mcp-tool-request and mcp-tool-response once an MCP replacement for SimilarityGuard exists.
      case _: SimilarityGuard =>
        if (c.useFor.contains(UseFor.ModelRequest) || c.useFor.contains(UseFor.Wildcard)) {
          if (c.name == "default jailbreak")
            log.warn(
              "Guardrail [default jailbreak] uses deprecated akka.javasdk.agent.SimilarityGuard with use-for " +
              "[model-request]. Enable \"default model-call jailbreak\" and remove \"default jailbreak\" " +
              "from your agents instead.")
          else
            log.warn(
              "Guardrail [{}] uses deprecated akka.javasdk.agent.SimilarityGuard with use-for [model-request]. " +
              "Use akka.javasdk.agent.ModelCallSimilarityGuard without use-for instead.",
              c.name)
        }
        if (c.useFor.contains(UseFor.ModelResponse))
          warnImplementNewInterface(c.name, Set[UseFor](UseFor.ModelResponse))

      case _ =>
        warnImplementNewInterface(c.name, c.useFor.intersect(Set[UseFor](UseFor.ModelRequest, UseFor.ModelResponse)))
    }

  private def warnImplementNewInterface(guardrailName: String, deprecated: Set[UseFor]): Unit =
    if (deprecated.nonEmpty)
      log.warn(
        "Guardrail [{}] uses deprecated use-for value(s) [{}]. Implement " +
        "akka.javasdk.agent.ModelCallGuardrail (for model-request) or " +
        "akka.javasdk.agent.AgentResponseGuardrail (for model-response) instead.",
        guardrailName,
        deprecated.map(_.configName).mkString(", "))

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
   * The entries to hand to the runtime.
   *
   * @param enabledForComponents
   *   the component ids of the agents that a guardrail applies to, by guardrail name
   */
  def spiGuardrails(enabledForComponents: String => Set[String]): Seq[SpiConfiguredGuardrail] =
    configuredGuardrails.map { g =>
      new SpiConfiguredGuardrail(
        name = g.name,
        implementationClass = g.implementationClass,
        enabledForComponents = enabledForComponents(g.name),
        reportOnly = g.reportOnly,
        useFor = g.useFor.map(_.toString),
        config = g.config,
        controlId = g.controlId)
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

    warnOnBothSimilarityGuards(componentId, deduplicated.values.toSeq)

    new AgentGuardrails(deduplicated.values.toVector, tracerFactory)
  }

  @nowarn("cat=deprecation")
  private def warnOnBothSimilarityGuards(componentId: String, entries: Seq[GuardrailEntry]): Unit = {
    // FIXME: count the MCP use-for values once an MCP replacement for SimilarityGuard exists.
    val legacyNames = entries.collect {
      case GuardrailEntry(c, _: SimilarityGuard) if c.useFor.contains(UseFor.ModelRequest) => c.name
    }
    val modelCallNames = entries.collect { case GuardrailEntry(c, _: ModelCallSimilarityGuard) => c.name }

    if (legacyNames.nonEmpty && modelCallNames.nonEmpty)
      log.warn(
        "Agent [{}] has both the deprecated SimilarityGuard [{}] with use-for [model-request] and the " +
        "ModelCallSimilarityGuard [{}]. Both check the user message and the tool results. Keep only one.",
        componentId,
        legacyNames.sorted.mkString(", "),
        modelCallNames.sorted.mkString(", "))
  }

}
