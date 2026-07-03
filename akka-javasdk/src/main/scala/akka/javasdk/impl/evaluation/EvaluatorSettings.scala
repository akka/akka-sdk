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
 * evaluator evaluates, where the value is a (possibly empty) config object for per-agent settings. Each evaluator and
 * agent binding config is merged (as a fallback) with the defaults under `akka.javasdk.evaluation.defaults`, so
 * settings such as `enabled` always resolve; disabled evaluators and agents produce no bindings.
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

  /** The agent bindings configured for the given evaluator, one per enabled agent under `agents`. */
  def agentBindings(config: Config, evaluatorComponentId: String): Seq[SpiEvaluator.Binding] = {
    val evaluators = configAt(config, EvaluatorsPath)
    val evaluatorDefaults = configAt(config, EvaluatorDefaultsPath)
    val agentDefaults = configAt(config, AgentDefaultsPath)

    evaluators.root().asScala.get(evaluatorComponentId) match {
      case Some(evaluator: ConfigObject) =>
        val evaluatorConfig = evaluator.toConfig.withFallback(evaluatorDefaults)
        if (evaluatorConfig.getBoolean("enabled") && evaluatorConfig.hasPath("agents")) {
          val agents = evaluatorConfig.getObject("agents")
          agents.keySet().asScala.toSeq.sorted.flatMap { agentComponentId =>
            val agentConfig = (agents.get(agentComponentId) match {
              case agent: ConfigObject => agent.toConfig
              case _                   => ConfigFactory.empty()
            }).withFallback(agentDefaults)

            if (!agentConfig.getBoolean("enabled")) None
            else if (!agentConfig.hasPath("trigger"))
              throw new IllegalArgumentException(
                s"Evaluator agent binding [$agentComponentId] must specify 'trigger' (supported: [interaction])")
            else
              Some(new SpiEvaluator.AgentBinding(agentComponentId, agentBindingEvent(agentConfig.getString("trigger"))))
          }
        } else {
          Seq.empty
        }
      case _ =>
        Seq.empty
    }
  }
}
