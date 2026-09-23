/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.runtime.sdk.spi.SpiEvaluator
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject

/**
 * INTERNAL API
 *
 * Reads the config-based bindings for an evaluator component from
 * `akka.javasdk.evaluation.evaluators.<evaluator-component-id>`. Each key under `agents` is an agent component id the
 * evaluator evaluates, and each key under `agent-roles` is an agent role, where `*` is every agent that has a role. The
 * value is a (possibly empty) config object for the settings of that binding. Each evaluator and binding config is
 * merged (as a fallback) with the defaults under `akka.javasdk.evaluation.defaults`, so settings such as `enabled`
 * always resolve; disabled evaluators and bindings produce no bindings.
 */
@InternalApi
private[impl] object EvaluatorSettings {

  private val EvaluatorsPath = "akka.javasdk.evaluation.evaluators"
  private val EvaluatorDefaultsPath = "akka.javasdk.evaluation.defaults.evaluator"
  private val AgentDefaultsPath = "akka.javasdk.evaluation.defaults.agent"

  private def configAt(config: Config, path: String): Config =
    if (config.hasPath(path)) config.getConfig(path) else ConfigFactory.empty()

  private def agentBindingEvent(trigger: String): SpiEvaluator.AgentBindingEvent =
    trigger.toLowerCase match {
      case "interaction" => SpiEvaluator.AgentBindingEvent.Interaction
      case other =>
        throw new IllegalArgumentException(
          s"Unknown evaluator agent binding trigger [$other], supported: [interaction]")
    }

  /**
   * The agent bindings configured for the given evaluator: one for each enabled entry under `agents`, and one for each
   * agent of this service whose role has an enabled entry under `agent-roles`. The most specific entry decides for an
   * agent, enabled or not: its entry under `agents`, then the entry for its role, then `*`.
   *
   * @param agentRoles
   *   the role of every agent of this service, by component id
   */
  def agentBindings(
      config: Config,
      evaluatorComponentId: String,
      agentRoles: Map[String, Option[String]]): Seq[SpiEvaluator.Binding] = {
    val evaluators = configAt(config, EvaluatorsPath)
    val evaluatorDefaults = configAt(config, EvaluatorDefaultsPath)
    val agentDefaults = configAt(config, AgentDefaultsPath)

    evaluators.root().asScala.get(evaluatorComponentId) match {
      case Some(evaluator: ConfigObject) =>
        val evaluatorConfig = evaluator.toConfig.withFallback(evaluatorDefaults)
        if (!evaluatorConfig.getBoolean("enabled")) Seq.empty
        else {
          val byAgent = bindingEvents(evaluatorConfig, "agents", agentDefaults, "agent binding")
          val byRole = bindingEvents(evaluatorConfig, "agent-roles", agentDefaults, "agent role binding")

          val boundByAgent = byAgent.collect { case (agentComponentId, Some(event)) => agentComponentId -> event }
          val boundByRole = agentRoles
            .collect {
              case (agentComponentId, Some(role)) if !byAgent.contains(agentComponentId) =>
                agentComponentId -> byRole.get(role).orElse(byRole.get("*")).flatten
            }
            .collect { case (agentComponentId, Some(event)) => agentComponentId -> event }

          (boundByAgent ++ boundByRole).toSeq.sortBy(_._1).map { case (agentComponentId, event) =>
            new SpiEvaluator.AgentBinding(agentComponentId, event)
          }
        }
      case _ =>
        Seq.empty
    }
  }

  /** The trigger of each entry under `path`, or `None` for a disabled entry. */
  private def bindingEvents(
      evaluatorConfig: Config,
      path: String,
      defaults: Config,
      kind: String): Map[String, Option[SpiEvaluator.AgentBindingEvent]] =
    if (!evaluatorConfig.hasPath(path)) Map.empty
    else {
      val entries = evaluatorConfig.getObject(path)
      entries
        .keySet()
        .asScala
        .map { key =>
          val entryConfig = (entries.get(key) match {
            case entry: ConfigObject => entry.toConfig
            case _                   => ConfigFactory.empty()
          }).withFallback(defaults)

          val event =
            if (!entryConfig.getBoolean("enabled")) None
            else if (!entryConfig.hasPath("trigger"))
              throw new IllegalArgumentException(
                s"Evaluator $kind [$key] must specify 'trigger' (supported: [interaction])")
            else Some(agentBindingEvent(entryConfig.getString("trigger")))
          key -> event
        }
        .toMap
    }
}
