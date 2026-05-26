/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.annotation.nowarn
import scala.concurrent.Future
import scala.util.Failure

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.agent.Decision
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.GuardrailContext
import akka.javasdk.agent.ModelGuardrail
import akka.javasdk.agent.SimilarityGuard
import akka.javasdk.agent.TextGuardrail
import akka.javasdk.agent.ToolGuardrail
import akka.javasdk.impl.agent.ConfiguredGuardrail.UseFor
import akka.runtime.sdk.spi.SpiAgent
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object GuardrailProvider {

  final case class GuardrailEntry(configuredGuardrail: ConfiguredGuardrail, guardrail: Guardrail)
  final case class AgentGuardrails(entries: Seq[GuardrailEntry]) {
    private def collectGuardrails(useFor: UseFor): Seq[SpiAgent.Guardrail] =
      entries.collect {
        case entry if entry.configuredGuardrail.useFor.contains(useFor) => toSpiGuardrail(entry)
      }

    val modelRequestGuardrails: Seq[SpiAgent.Guardrail] =
      collectGuardrails(UseFor.ModelRequest)
    val modelResponseGuardrails: Seq[SpiAgent.Guardrail] =
      collectGuardrails(UseFor.ModelResponse)
    val mcpToolRequestGuardrails: Seq[SpiAgent.Guardrail] =
      collectGuardrails(UseFor.McpToolRequest)
    val mcpToolResponseGuardrails: Seq[SpiAgent.Guardrail] =
      collectGuardrails(UseFor.McpToolResponse)
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

  final class ToolGuardrailAdapter(entry: GuardrailEntry, guardrail: ToolGuardrail) extends SpiAgent.Guardrail {

    override def evaluate(content: SpiAgent.Guardrail.Content): Future[SpiAgent.Guardrail.Result] =
      // The per-call context is a placeholder at this stage; boundary-specific fields
      // are populated by the tool-guardrail-binding follow-up.
      decisionToSpiResult(guardrail.evaluate(ToolGuardrailContextImpl))

    override val name: String = entry.configuredGuardrail.name
    override val category: String = entry.configuredGuardrail.category
    override val reportOnly: Boolean = entry.configuredGuardrail.reportOnly
  }

  final class ModelGuardrailAdapter(entry: GuardrailEntry, guardrail: ModelGuardrail) extends SpiAgent.Guardrail {

    override def evaluate(content: SpiAgent.Guardrail.Content): Future[SpiAgent.Guardrail.Result] =
      // The per-call context is a placeholder at this stage; boundary-specific fields
      // are populated by the model-guardrail-binding follow-up.
      decisionToSpiResult(guardrail.evaluate(ModelGuardrailContextImpl))

    override val name: String = entry.configuredGuardrail.name
    override val category: String = entry.configuredGuardrail.category
    override val reportOnly: Boolean = entry.configuredGuardrail.reportOnly
  }

  // Decision.Error becomes a failed Future so the cause Throwable flows through the runtime's
  // existing handling in AgentGuardrailInteractions, where it ends up as the cause of the
  // AgentException reaching the user's onFailure mapper.
  private def decisionToSpiResult(decision: Decision): Future[SpiAgent.Guardrail.Result] =
    decision match {
      case _: Decision.Pass  => Future.successful(new SpiAgent.Guardrail.Result(true, ""))
      case b: Decision.Block => Future.successful(new SpiAgent.Guardrail.Result(false, b.reason))
      case e: Decision.Error => Future.failed(new RuntimeException(e.reason, e.cause))
    }

  @nowarn("cat=deprecation")
  private def toSpiGuardrail(entry: GuardrailEntry): SpiAgent.Guardrail =
    entry.guardrail match {
      case g: SimilarityGuard => toSpiSimilarityGuard(g, entry.configuredGuardrail)
      case g: TextGuardrail   => new TextGuardrailAdapter(entry, g)
      case g: ToolGuardrail   => new ToolGuardrailAdapter(entry, g)
      case g: ModelGuardrail  => new ModelGuardrailAdapter(entry, g)
    }

  private def toSpiSimilarityGuard(g: SimilarityGuard, c: ConfiguredGuardrail): SpiAgent.SimilarityGuard =
    new SpiAgent.SimilarityGuard(c.name, c.category, c.reportOnly, g.badExamplesResourceDir, g.threshold)

  private val ToolSideUseFor: Set[UseFor] = Set(UseFor.McpToolRequest, UseFor.McpToolResponse)
  private val ModelSideUseFor: Set[UseFor] = Set(UseFor.ModelRequest, UseFor.ModelResponse)
}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class GuardrailProvider(system: ActorSystem[_], applicationConfig: Config) {
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
    val implementsText = instance.isInstanceOf[TextGuardrail]
    val implementsTool = instance.isInstanceOf[ToolGuardrail]
    val implementsModel = instance.isInstanceOf[ModelGuardrail]
    val count = Seq(implementsText, implementsTool, implementsModel).count(identity)

    if (count > 1)
      throw new IllegalArgumentException(
        s"Guardrail [$guardrailName] must implement only one of " +
        s"[${classOf[ToolGuardrail].getName}] or [${classOf[ModelGuardrail].getName}], " +
        s"but [${instance.getClass.getName}] implements multiple")
  }

  // ToolGuardrail must bind to tool-side use-for; ModelGuardrail to model-side.
  // (When new boundary names land, this validation moves with them.)
  private def validateUseFor(c: ConfiguredGuardrail, instance: Guardrail): Unit =
    instance match {
      case _: ToolGuardrail if !c.useFor.subsetOf(ToolSideUseFor) =>
        val invalid = c.useFor.diff(ToolSideUseFor)
        throw new IllegalArgumentException(
          s"ToolGuardrail [${c.name}] can only be bound to tool-side use-for values " +
          s"(mcp-tool-request, mcp-tool-response), but was also bound to [${invalid.mkString(", ")}]")
      case _: ModelGuardrail if !c.useFor.subsetOf(ModelSideUseFor) =>
        val invalid = c.useFor.diff(ModelSideUseFor)
        throw new IllegalArgumentException(
          s"ModelGuardrail [${c.name}] can only be bound to model-side use-for values " +
          s"(model-request, model-response), but was also bound to [${invalid.mkString(", ")}]")
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
    AgentGuardrails(deduplicated.values.toVector)
  }

}
