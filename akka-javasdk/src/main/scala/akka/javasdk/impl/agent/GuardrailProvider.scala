/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.concurrent.CompletionStage

import scala.annotation.nowarn
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
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
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.ForwardCompat.knownOr
import akka.javasdk.impl.agent.ConfiguredGuardrail.UseFor
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiAgentGuardrails
import akka.runtime.sdk.spi.SpiConfiguredGuardrail
import akka.runtime.sdk.spi.SpiGuardrail
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
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
      override val origin: ToolCallGuardrail.ToolOrigin,
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
          case GuardrailEntry(configured, _: ModelCallSimilarityGuard) =>
            new SpiGuardrail.SimilarityGuard(
              toSettings(configured),
              configured.config.getString("bad-examples-resource-dir"),
              configured.config.getDouble("threshold"))
          case GuardrailEntry(configured, g: ModelCallGuardrail) =>
            new ModelCallGuardrailAdapter(toSettings(configured), g, tracerFactory)
        },
        agentResponseGuardrails = entries.collect { case GuardrailEntry(configured, g: AgentResponseGuardrail) =>
          new AgentResponseGuardrailAdapter(toSettings(configured), g, tracerFactory)
        })

    // The label of each AgentResponseGuardrail entry without `report-only = true`.
    private[agent] lazy val enforcingResponseGuardrailLabels: Seq[String] =
      guardrails.agentResponseGuardrails
        .map(_.settings)
        .filterNot(_.reportOnly)
        .map(s => s"Guardrail [${s.name}]")
        .sorted

    /**
     * The startup error for a streaming agent bound to AgentResponseGuardrail entries without `report-only = true`.
     */
    def streamingResponseGuardrailError(componentId: String, isStreaming: Boolean): Option[String] =
      Option.when(isStreaming && enforcingResponseGuardrailLabels.nonEmpty) {
        s"Agent [$componentId] streams its reply. It cannot use an AgentResponseGuardrail entry without " +
        s"`report-only = true`: ${enforcingResponseGuardrailLabels.mkString(", ")}. Return Agent.Effect from " +
        "the agent's command handler, or remove the agent from each guardrail. " +
        "See the agent guardrails documentation for the other options."
      }

    // The ToolCallGuardrails applicable to the given tool. An entry with an empty `tools` set
    // applies to every tool on the agent; otherwise only to the named tools.
    private[agent] def toolCallGuardrails(toolName: String): Seq[SpiGuardrail.ToolCall] =
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
            toToolOrigin(ctx.origin),
            Option(ctx.telemetryContext),
            tracerFactory)))
  }

  // An origin that this SDK does not know, or a null, maps to ToolOrigin.Unknown.
  private def toToolOrigin(origin: SpiGuardrail.ToolOrigin): ToolCallGuardrail.ToolOrigin =
    knownOr[ToolCallGuardrail.ToolOrigin](new ToolCallGuardrail.ToolOrigin.Unknown()) {
      origin match {
        case SpiGuardrail.ToolOrigin.FunctionTool   => new ToolCallGuardrail.ToolOrigin.FunctionTool()
        case mcp: SpiGuardrail.ToolOrigin.RemoteMcp => new ToolCallGuardrail.ToolOrigin.RemoteMcp(mcp.endpoint)
      }
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
        new Message.ToolCallResponse(t.id, t.name, t.contents.map(AgentImpl.fromSpiMessageContent).asJava, t.isError)
    }

  private def toSpiDecision(decision: CompletionStage[Decision]): Future[SpiGuardrail.Decision] =
    if (decision == null)
      Future.failed(new NullPointerException("Guardrail returned a null CompletionStage"))
    else
      ErrorHandling
        .asScalaUnwrapped(decision)
        .map {
          case allow: Allow => new SpiGuardrail.Allow(allow.reason)
          case deny: Deny   => new SpiGuardrail.Deny(deny.reason)
          case fail: Fail   => new SpiGuardrail.Fail(fail.reason, Option(fail.cause))
          case null         => null
        }(ExecutionContext.parasitic)

  private def toSettings(c: ConfiguredGuardrail): SpiGuardrail.Settings =
    new SpiGuardrail.Settings(c.name, c.category, c.reportOnly, c.failMode)

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

  private val DefaultJailbreak = "default jailbreak"
  private val DefaultModelCallJailbreak = "default model-call jailbreak"

  // Maps each deprecated use-for value to its replacement advice.
  private val DeprecatedUseFor: Seq[(UseFor, String)] =
    Seq(
      UseFor.ModelRequest -> "implement akka.javasdk.agent.ModelCallGuardrail for model-request",
      UseFor.ModelResponse -> "implement akka.javasdk.agent.AgentResponseGuardrail for model-response",
      UseFor.McpToolRequest -> "implement akka.javasdk.agent.ToolCallGuardrail for mcp-tool-request",
      UseFor.McpToolResponse -> "implement akka.javasdk.agent.ModelCallGuardrail for mcp-tool-response")

  // Maps each deprecated use-for value of a SimilarityGuard to its replacement advice.
  private val DeprecatedSimilarityGuardUseFor: Seq[(UseFor, String)] =
    Seq(
      UseFor.ModelRequest -> "use akka.javasdk.agent.ModelCallSimilarityGuard for model-request",
      UseFor.ModelResponse -> "remove model-response, which has no built-in replacement",
      UseFor.McpToolRequest -> "implement akka.javasdk.agent.ToolCallGuardrail for mcp-tool-request, which has no built-in replacement",
      UseFor.McpToolResponse -> "use akka.javasdk.agent.ModelCallSimilarityGuard for mcp-tool-response")

  // The similarity settings shared by SimilarityGuard and ModelCallSimilarityGuard.
  private val SimilarityGuardSettings = Seq("category", "threshold", "report-only", "bad-examples-resource-dir")

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

  // One instance per enabled guardrail.
  private lazy val enabledEntries: Seq[GuardrailEntry] =
    configuredGuardrails.filter(c => c.agents.nonEmpty || c.agentRoles.nonEmpty).map(createGuardrail)

  private lazy val guardrailsByComponentId: Map[String, Seq[GuardrailEntry]] = {
    enabledEntries.foldLeft(Map.empty[String, Vector[GuardrailEntry]]) { case (acc, entry) =>
      entry.configuredGuardrail.agents.foldLeft(acc) { case (acc2, componentId) =>
        acc2.updated(componentId, acc2.getOrElse(componentId, Vector.empty) :+ entry)
      }
    }
  }

  private lazy val guardrailsByRole: Map[String, Seq[GuardrailEntry]] = {
    enabledEntries.foldLeft(Map.empty[String, Vector[GuardrailEntry]]) { case (acc, entry) =>
      entry.configuredGuardrail.agentRoles.foldLeft(acc) { case (acc2, role) =>
        acc2.updated(role, acc2.getOrElse(role, Vector.empty) :+ entry)
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
        rejectFailMode(c)
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

  @nowarn("cat=deprecation")
  private def warnOnDeprecatedUseFor(entry: GuardrailEntry): Unit = {
    val c = entry.configuredGuardrail
    val adviceByUseFor = entry.guardrail match {
      case _: SimilarityGuard => similarityGuardAdvice(c)
      case _: TextGuardrail   => DeprecatedUseFor
      case _                  => Nil
    }

    val advice = adviceByUseFor.collect { case (useFor, text) if c.useFor.contains(useFor) => text }

    if (advice.nonEmpty)
      log.warn("Guardrail [{}] uses deprecated use-for values. Instead, {}.", c.name, advice.mkString("; "))
  }

  private def similarityGuardAdvice(c: ConfiguredGuardrail): Seq[(UseFor, String)] =
    DeprecatedSimilarityGuardUseFor.map {
      case (UseFor.ModelRequest, _) if c.name == DefaultJailbreak => UseFor.ModelRequest -> defaultJailbreakAdvice(c)
      case other                                                  => other
    }

  private def defaultJailbreakAdvice(c: ConfiguredGuardrail): String = {
    val changed = changedDefaultJailbreakSettings(c)
    val advice = s"enable \"$DefaultModelCallJailbreak\" for the same agents and agent roles"
    if (changed.isEmpty) advice
    else s"$advice and set ${changed.mkString(", ")} on it"
  }

  // The settings of "default jailbreak" that differ from "default model-call jailbreak", as `key = value`.
  private def changedDefaultJailbreakSettings(c: ConfiguredGuardrail): Seq[String] = {
    val guardrailsConfig = applicationConfig.getConfig("akka.javasdk.agent.guardrails")
    val modelCallPath = s"\"$DefaultModelCallJailbreak\""
    if (!guardrailsConfig.hasPath(modelCallPath)) Seq.empty
    else {
      val modelCall = guardrailsConfig.getConfig(modelCallPath)
      SimilarityGuardSettings.flatMap { key =>
        val current = Option.when(c.config.hasPath(key))(c.config.getValue(key).render(ConfigRenderOptions.concise()))
        val target = Option.when(modelCall.hasPath(key))(modelCall.getValue(key).render(ConfigRenderOptions.concise()))
        current.filterNot(target.contains).map(value => s"$key = $value")
      }
    }
  }

  private def validateTextGuardrailUseFor(c: ConfiguredGuardrail): Unit =
    if (c.useFor.isEmpty)
      throw new IllegalArgumentException(
        s"TextGuardrail [${c.name}] must define use-for with one or more of " +
        s"[model-request, model-response, mcp-tool-request, mcp-tool-response] or [*]")

  // ToolCallGuardrail, ModelCallGuardrail and AgentResponseGuardrail bind to their boundary by type.
  private def rejectUseFor(c: ConfiguredGuardrail, instance: Guardrail): Unit =
    if (c.config.hasPath("use-for")) {
      val defaultJailbreakHint =
        if (c.name == DefaultJailbreak)
          s" The use-for of [$DefaultJailbreak] comes from reference.conf. To use " +
          s"akka.javasdk.agent.ModelCallSimilarityGuard, enable \"$DefaultModelCallJailbreak\" instead."
        else ""

      throw new IllegalArgumentException(
        s"Guardrail [${c.name}] must not define use-for. [${instance.getClass.getName}] binds to its " +
        "boundary by type: ToolCallGuardrail before each tool call, ModelCallGuardrail before each " +
        "model call, and AgentResponseGuardrail on the final agent reply." + defaultJailbreakHint)
    }

  private def rejectFailMode(c: ConfiguredGuardrail): Unit =
    if (c.config.hasPath("fail-mode"))
      throw new IllegalArgumentException(
        s"TextGuardrail [${c.name}] must not define fail-mode. A TextGuardrail has no fail mode. " +
        "Use fail-mode with ToolCallGuardrail, ModelCallGuardrail or AgentResponseGuardrail.")

  def validate(): Unit =
    enabledEntries.foreach { entry =>
      warnOnDeprecatedUseFor(entry)
      warnOnIgnoredFailMode(entry.configuredGuardrail)
    }

  private def warnOnIgnoredFailMode(c: ConfiguredGuardrail): Unit =
    if (c.reportOnly && c.config.hasPath("fail-mode"))
      log.warn("Guardrail [{}] sets fail-mode, but fail-mode has no effect while report-only is true.", c.name)

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
        failMode = g.failMode,
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
    val modelCallNames = entries.collect { case GuardrailEntry(c, _: ModelCallSimilarityGuard) => c.name }.sorted

    if (modelCallNames.nonEmpty) {
      val legacy = entries
        .collect { case GuardrailEntry(c, _: SimilarityGuard) => c }
        .filter(c => overlapsModelCallSimilarityGuard(c).nonEmpty)
        .sortBy(_.name)

      legacy.foreach { c =>
        val overlap = overlapsModelCallSimilarityGuard(c).map(_.configName)
        val advice =
          if (c.useFor.size == overlap.size) s"Remove the agents and agent roles from [${c.name}]."
          else s"Remove ${overlap.mkString(", ")} from the use-for of [${c.name}]."

        log.warn(
          "Agent [{}] has both the deprecated SimilarityGuard [{}] with use-for [{}] and the " +
          "ModelCallSimilarityGuard [{}]. Both check the user message and the tool results. {}",
          componentId,
          c.name,
          overlap.mkString(", "),
          modelCallNames.mkString(", "),
          advice)
      }
    }
  }

  // The use-for values of a SimilarityGuard that a ModelCallSimilarityGuard also checks.
  private def overlapsModelCallSimilarityGuard(c: ConfiguredGuardrail): Seq[UseFor] =
    Seq(UseFor.ModelRequest, UseFor.McpToolResponse).filter(c.useFor.contains)

}
