/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.impl.ControlId
import akka.runtime.sdk.spi.EvaluatorDescriptor
import akka.runtime.sdk.spi.SpiEvaluator
import akka.runtime.sdk.spi.SpiWorkflowEvaluator
import akka.runtime.sdk.spi.WorkflowEvaluatorDescriptor
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
 * always resolve; disabled evaluators and bindings produce no bindings. A binding, not an evaluator, may set
 * `sampling-ratio`, the share of the interactions of the agent that the evaluator evaluates, and `trigger-on-failure`,
 * whether the evaluator also evaluates failed interactions.
 */
@InternalApi
private[impl] object EvaluatorSettings {

  private val EvaluatorsPath = "akka.javasdk.evaluation.evaluators"
  private val EvaluatorDefaultsPath = "akka.javasdk.evaluation.defaults.evaluator"
  private val AgentDefaultsPath = "akka.javasdk.evaluation.defaults.agent"
  private val SamplingRatioKey = "sampling-ratio"
  private val TriggerOnFailureKey = "trigger-on-failure"

  /** The settings of an enabled binding entry. */
  private final case class BindingSettings(
      event: SpiEvaluator.AgentBindingEvent,
      samplingRatio: Double,
      triggerOnFailure: Boolean)

  private def configAt(config: Config, path: String): Config =
    if (config.hasPath(path)) config.getConfig(path) else ConfigFactory.empty()

  /** The `sampling-ratio` of the given config, where `entry` names the config for the error. */
  private def samplingRatio(config: Config, entry: String): Double = {
    val ratio = config.getDouble(SamplingRatioKey)
    // the runtime treats a ratio above 1.0 as 1.0, so a percentage would evaluate every interaction
    if (!(ratio >= 0.0 && ratio <= 1.0))
      throw new IllegalArgumentException(
        s"$entry must define [$SamplingRatioKey] between 0.0 and 1.0, but defines [$ratio]")
    ratio
  }

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
    // checked on its own, so that the error for a binding is about a value that the binding defines
    samplingRatio(agentDefaults, s"Evaluator defaults [$AgentDefaultsPath]")

    evaluators.root().asScala.get(evaluatorComponentId) match {
      case Some(evaluator: ConfigObject) =>
        val evaluatorConfig = evaluator.toConfig.withFallback(evaluatorDefaults)

        // the SDK reads these keys only from a binding, so on the evaluator they would be lost
        Seq(SamplingRatioKey, TriggerOnFailureKey).find(evaluatorConfig.hasPath).foreach { key =>
          throw new IllegalArgumentException(
            s"Evaluator [$evaluatorComponentId] must define [$key] in a binding or in [$AgentDefaultsPath], " +
            s"not on the evaluator or in [$EvaluatorDefaultsPath]")
        }

        if (!evaluatorConfig.getBoolean("enabled")) Seq.empty
        else {
          val byAgent = bindingSettings(evaluatorComponentId, evaluatorConfig, "agents", agentDefaults, "agent binding")
          val byRole =
            bindingSettings(evaluatorComponentId, evaluatorConfig, "agent-roles", agentDefaults, "agent role binding")

          val boundByAgent = byAgent.collect { case (agentComponentId, Some(settings)) =>
            agentComponentId -> settings
          }
          val boundByRole = agentRoles
            .collect {
              case (agentComponentId, Some(role)) if !byAgent.contains(agentComponentId) =>
                agentComponentId -> byRole.get(role).orElse(byRole.get("*")).flatten
            }
            .collect { case (agentComponentId, Some(settings)) => agentComponentId -> settings }

          (boundByAgent ++ boundByRole).toSeq.sortBy(_._1).map { case (agentComponentId, settings) =>
            new SpiEvaluator.AgentBinding(
              agentComponentId,
              settings.event,
              settings.samplingRatio,
              settings.triggerOnFailure)
          }
        }
      case _ =>
        Seq.empty
    }
  }

  /**
   * The `control-id` of the given evaluator, or `None` when it has no entry or the entry has none. This method reads it
   * even when the evaluator is disabled.
   *
   * @throws IllegalArgumentException
   *   if `control-id` is in a binding of the evaluator or under `akka.javasdk.evaluation.defaults`
   */
  def controlId(config: Config, evaluatorComponentId: String): Option[String] = {
    val entry = s"Evaluator [$evaluatorComponentId]"
    val evaluator = configAt(config, EvaluatorsPath).root().asScala.get(evaluatorComponentId).collect {
      case evaluator: ConfigObject => evaluator.toConfig
    }

    // the SDK reads the id only from the entry of the evaluator, so an id anywhere else would be lost
    val misplaced = Seq(EvaluatorDefaultsPath, AgentDefaultsPath).filter(configAt(config, _).hasPath(ControlId.Key)) ++
      evaluator.toSeq.flatMap(bindingsWithControlId)
    misplaced.headOption.foreach { path =>
      throw new IllegalArgumentException(s"$entry must define [${ControlId.Key}] on the evaluator, not in [$path]")
    }

    evaluator.flatMap(ControlId.read(_, entry))
  }

  /** The path of each binding under `agents` and `agent-roles` that defines `control-id`. */
  private def bindingsWithControlId(evaluator: Config): Seq[String] =
    Seq("agents", "agent-roles").flatMap { path =>
      evaluator.root().get(path) match {
        case bindings: ConfigObject =>
          bindings.asScala.toSeq.collect {
            case (key, binding: ConfigObject) if binding.toConfig.hasPath(ControlId.Key) => s"$path.$key"
          }.sorted
        case _ => Nil
      }
    }

  /** The agent bindings and the control id of the given evaluator, see [[agentBindings]] and [[controlId]]. */
  def configuredEvaluator(
      config: Config,
      evaluatorComponentId: String,
      agentRoles: Map[String, Option[String]]): ConfiguredEvaluator =
    ConfiguredEvaluator(
      bindings = agentBindings(config, evaluatorComponentId, agentRoles),
      controlId = controlId(config, evaluatorComponentId))

  /**
   * The trigger, the sampling ratio and the failure flag of each entry under `path`, or `None` for a disabled entry.
   */
  private def bindingSettings(
      evaluatorComponentId: String,
      evaluatorConfig: Config,
      path: String,
      defaults: Config,
      kind: String): Map[String, Option[BindingSettings]] =
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

          val settings =
            if (!entryConfig.getBoolean("enabled")) None
            else if (!entryConfig.hasPath("trigger"))
              throw new IllegalArgumentException(
                s"Evaluator $kind [$key] must specify 'trigger' (supported: [interaction])")
            else {
              val event = agentBindingEvent(entryConfig.getString("trigger"))
              val ratio = samplingRatio(entryConfig, s"Evaluator [$evaluatorComponentId] $kind [$key]")
              Some(BindingSettings(event, ratio, entryConfig.getBoolean(TriggerOnFailureKey)))
            }
          key -> settings
        }
        .toMap
    }
}

/**
 * INTERNAL API
 *
 * The agent bindings and the control id of one evaluator.
 */
@InternalApi
private[impl] final case class ConfiguredEvaluator(bindings: Seq[SpiEvaluator.Binding], controlId: Option[String]) {

  /** The descriptor to hand to the runtime for an evaluator, with the bindings and the control id of this entry. */
  def evaluatorDescriptor(
      componentId: String,
      implementationName: String,
      name: Option[String],
      description: Option[String],
      instanceFactory: SpiEvaluator.FactoryContext => SpiEvaluator,
      provided: Boolean): EvaluatorDescriptor =
    new EvaluatorDescriptor(
      componentId,
      implementationName,
      name = name,
      description = description,
      bindings = bindings,
      instanceFactory = instanceFactory,
      provided = provided,
      controlId = controlId)

  /** As [[evaluatorDescriptor]], for a durable evaluator. */
  def workflowEvaluatorDescriptor(
      componentId: String,
      implementationName: String,
      name: Option[String],
      description: Option[String],
      instanceFactory: SpiWorkflowEvaluator.FactoryContext => SpiWorkflowEvaluator,
      provided: Boolean): WorkflowEvaluatorDescriptor =
    new WorkflowEvaluatorDescriptor(
      componentId,
      implementationName,
      name = name,
      description = description,
      bindings = bindings,
      instanceFactory = instanceFactory,
      provided = provided,
      controlId = controlId)
}
