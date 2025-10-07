/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.concurrent.Future
import scala.util.Failure

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.GuardrailContext
import akka.javasdk.agent.SimilarityGuard
import akka.javasdk.agent.TextGuardrail
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

  final class SpiGuardrailAdapter(entry: GuardrailEntry) extends SpiAgent.Guardrail {
    private val guardrail = entry.guardrail

    override def evaluate(content: SpiAgent.Guardrail.Content): Future[SpiAgent.Guardrail.Result] = {
      content match {
        case textContent: SpiAgent.Guardrail.TextContent =>
          guardrail match {
            case textGuardrail: TextGuardrail =>
              val result = textGuardrail.evaluate(textContent.text)
              Future.successful(new SpiAgent.Guardrail.Result(result.passed, result.explanation))
            case other =>
              // it's sealed to only TextGuardrail so this shouldn't happen unless we add more types
              Future.failed(
                new IllegalStateException(s"Only TextGuardrail is supported, but was [${other.getClass.getName}]"))
          }

        case other =>
          Future.failed(
            new IllegalArgumentException(s"Only text content is supported, but was [${other.getClass.getName}]"))
      }
    }

    override val name: String =
      entry.configuredGuardrail.name

    override val category: String =
      entry.configuredGuardrail.category

    override val reportOnly: Boolean =
      entry.configuredGuardrail.reportOnly
  }

  private def toSpiGuardrail(entry: GuardrailEntry): SpiAgent.Guardrail =
    entry.guardrail match {
      case g: SimilarityGuard => toSpiSimilarityGuard(g, entry.configuredGuardrail)
      case _                  => new SpiGuardrailAdapter(entry)
    }

  private def toSpiSimilarityGuard(g: SimilarityGuard, c: ConfiguredGuardrail): SpiAgent.SimilarityGuard =
    new SpiAgent.SimilarityGuard(c.name, c.category, c.reportOnly, g.badExamplesResourceDir, g.threshold)
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
    configuredGuardrails.foldLeft(Map.empty[String, Vector[GuardrailEntry]]) { case (acc, c) =>
      if (c.useFor.nonEmpty) {
        c.agents.foldLeft(acc) { case (acc2, componentId) =>
          acc2.updated(componentId, acc2.getOrElse(componentId, Vector.empty) :+ GuardrailEntry(c, createGuardrail(c)))
        }
      } else {
        acc
      }
    }
  }

  private lazy val guardrailsByRole: Map[String, Seq[GuardrailEntry]] = {
    configuredGuardrails.foldLeft(Map.empty[String, Vector[GuardrailEntry]]) { case (acc, c) =>
      if (c.useFor.nonEmpty) {
        c.agentRoles.foldLeft(acc) { case (acc2, role) =>
          acc2.updated(role, acc2.getOrElse(role, Vector.empty) :+ GuardrailEntry(c, createGuardrail(c)))
        }
      } else {
        acc
      }
    }
  }

  private def createGuardrail(c: ConfiguredGuardrail): Guardrail = {
    val guardrailContext = new GuardrailContextImpl(c.name, c.config)
    system.dynamicAccess
      .createInstanceFor[Guardrail](c.implementationClass, (classOf[GuardrailContext] -> guardrailContext) :: Nil)
      .recoverWith { case _: ClassNotFoundException | _: NoSuchMethodException =>
        system.dynamicAccess.createInstanceFor[Guardrail](c.implementationClass, Nil)
      }
      .recoverWith { case _: ClassNotFoundException | _: NoSuchMethodException =>
        Failure(
          new IllegalArgumentException(s"Guardrail [${c.name}] must implement [${classOf[Guardrail].getName}] and " +
          s"optionally have a constructor with GuardrailContext parameter"))
      }
      .get
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
