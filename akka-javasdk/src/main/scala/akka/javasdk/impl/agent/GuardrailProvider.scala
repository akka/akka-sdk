/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.annotation.nowarn
import scala.concurrent.Future
import scala.util.Failure
import scala.util.control.NonFatal

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.agent.Decision
import akka.javasdk.agent.Decision.Allow
import akka.javasdk.agent.Decision.Deny
import akka.javasdk.agent.Decision.Fail
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.GuardrailContext
import akka.javasdk.agent.ModelGuardrail
import akka.javasdk.agent.SimilarityGuard
import akka.javasdk.agent.TextGuardrail
import akka.javasdk.agent.ToolGuardrail
import akka.javasdk.impl.agent.ConfiguredGuardrail.UseFor
import akka.runtime.sdk.spi.SpiAgent
import com.typesafe.config.Config
import io.opentelemetry.api.trace.Tracer

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object GuardrailProvider {

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
          // TODO: thrown exceptions and explicit Decision.fail(...) currently collapse onto the same
          // failed-Future path. Pending an internal decision on fail-closed (thrown) vs configurable
          // fail-closed/fail-open (explicit error) — keep them separable when that lands.
          evaluateSafely(
            guardrail.evaluate(
              new ToolGuardrailContextImpl(
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
        case textContent: SpiAgent.Guardrail.TextContent =>
          // TODO: thrown exceptions and explicit Decision.fail(...) currently collapse onto the same
          // failed-Future path. Pending an internal decision on fail-closed (thrown) vs configurable
          // fail-closed/fail-open (explicit error) — keep them separable when that lands.
          evaluateSafely(
            guardrail.evaluate(
              new ModelGuardrailContextImpl(textContent.text, Option(textContent.telemetryContext), tracerFactory)))
        case other =>
          Future.failed(
            new IllegalArgumentException(s"Only text content is supported, but was [${other.getClass.getName}]"))
      }

    override val name: String = entry.configuredGuardrail.name
    override val category: String = entry.configuredGuardrail.category
    override val reportOnly: Boolean = entry.configuredGuardrail.reportOnly
  }

  // A thrown exception from a guardrail's evaluate(...) is treated as if the guardrail had
  // returned Decision.fail(message, throwable) — propagated to the runtime as a failed Future.
  private def evaluateSafely(decision: => Decision): Future[SpiAgent.Guardrail.Result] =
    try decisionToSpiResult(decision)
    catch {
      case NonFatal(t) =>
        decisionToSpiResult(Decision.fail(Option(t.getMessage).getOrElse(t.getClass.getName), t))
    }

  // Decision.Fail becomes a failed Future so the cause Throwable flows through the runtime's
  // existing handling in AgentGuardrailInteractions, where it ends up as the cause of the
  // AgentException reaching the user's onFailure mapper.
  private def decisionToSpiResult(decision: Decision): Future[SpiAgent.Guardrail.Result] =
    decision match {
      case a: Allow => Future.successful(new SpiAgent.Guardrail.Result(true, a.reason))
      case d: Deny  => Future.successful(new SpiAgent.Guardrail.Result(false, d.reason))
      case e: Fail  => Future.failed(new RuntimeException(e.reason, e.cause))
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

  // ToolGuardrail currently binds only to the before-tool-call boundary (in-process function tools).
  // FIXME: extend to the MCP tool request/response boundaries. That requires ToolGuardrailAdapter to
  // build a ToolGuardrailContext from the MCP TextContent and ToolSideUseFor to also include
  // McpToolRequest/McpToolResponse.
  private val ToolSideUseFor: Set[UseFor] = Set(UseFor.BeforeToolCall)
  private val ModelSideUseFor: Set[UseFor] = Set(UseFor.ModelRequest, UseFor.ModelResponse)
}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class GuardrailProvider(
    system: ActorSystem[_],
    applicationConfig: Config,
    tracerFactory: () => Tracer) {
  import GuardrailProvider._

  lazy val configuredGuardrails: Seq[ConfiguredGuardrail] = {
    GuardrailSettings(applicationConfig.getConfig("akka.javasdk.agent.guardrails")).configuredGuardrails
  }

  private lazy val guardrailsByComponentId: Map[String, Seq[GuardrailEntry]] = {
    configuredGuardrails.foldLeft(Map.empty[String, Vector[GuardrailEntry]]) {
      case (acc, config) if config.useFor.nonEmpty =>
        config.agents.foldLeft(acc) { case (acc2, componentId) =>
          acc2.updated(
            componentId,
            acc2.getOrElse(componentId, Vector.empty) :+ GuardrailEntry(config, createGuardrail(config)))
        }
      case (acc, _) => acc
    }
  }

  private lazy val guardrailsByRole: Map[String, Seq[GuardrailEntry]] = {
    configuredGuardrails.foldLeft(Map.empty[String, Vector[GuardrailEntry]]) {
      case (acc, config) if config.useFor.nonEmpty =>
        config.agentRoles.foldLeft(acc) { case (acc2, role) =>
          acc2.updated(role, acc2.getOrElse(role, Vector.empty) :+ GuardrailEntry(config, createGuardrail(config)))
        }
      case (acc, _) => acc
    }
  }

  private def createGuardrail(c: ConfiguredGuardrail): Guardrail = {
    val guardrailContext = new GuardrailContextImpl(c.name, c.config)
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
    validateUseFor(c, instance)
    instance
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

  // ToolGuardrail must bind to before-tool-call; ModelGuardrail to model-side. before-tool-call is
  // reserved for ToolGuardrail, so any other guardrail declaring it is rejected.
  // (When new boundary names land, this validation moves with them.)
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
          s"(model-request, model-response), but was also bound to [${invalid.mkString(", ")}]")
      case _: ToolGuardrail | _: ModelGuardrail => // ok
      case _ if c.useFor.contains(UseFor.BeforeToolCall) =>
        throw new IllegalArgumentException(
          s"Guardrail [${c.name}] (class [${instance.getClass.getName}]) is bound to before-tool-call, " +
          s"which is only valid for a [${classOf[ToolGuardrail].getName}].")
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
