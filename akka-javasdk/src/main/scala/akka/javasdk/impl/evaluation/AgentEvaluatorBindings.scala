/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import java.util

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.impl.ComponentLocator
import akka.javasdk.impl.client.MethodRefResolver
import akka.javasdk.impl.reflection.Reflect
import akka.runtime.sdk.spi.SpiEvaluator
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject

/**
 * INTERNAL API
 *
 * One enabled binding of an evaluator to an agent.
 */
@InternalApi
private[javasdk] final case class AgentEvaluatorBinding(
    evaluatorComponentId: String,
    samplingRatio: Double,
    triggerOnFailure: Boolean)

/**
 * INTERNAL API
 *
 * The evaluators bound to one agent, resolved from the application config the same way the SDK resolves them at
 * startup.
 */
@InternalApi
private[javasdk] object AgentEvaluatorBindings {

  private val EvaluatorsPath = "akka.javasdk.evaluation.evaluators"

  /** The agent class a method reference to an agent command handler belongs to. */
  def agentClass(methodRef: AnyRef): Class[_] =
    MethodRefResolver.resolveMethodRef(methodRef).getDeclaringClass

  /** The component id of the agent class. */
  def agentComponentId(agentClass: Class[_]): String =
    Reflect.readComponentId(agentClass)

  /**
   * The enabled bindings to the agent of every evaluator configured under `akka.javasdk.evaluation.evaluators`, sorted
   * by evaluator component id.
   */
  def bindings(config: Config, agentClass: Class[_]): util.List[AgentEvaluatorBinding] = {
    val agentComponentId = Reflect.readComponentId(agentClass)
    val agentRoles = Map(agentComponentId -> Reflect.readAgentRole(agentClass))
    val evaluatorComponentIds =
      if (config.hasPath(EvaluatorsPath)) config.getObject(EvaluatorsPath).asScala.collect {
        case (id, _: ConfigObject) => id
      }
      else Nil
    evaluatorComponentIds.toSeq.sorted.flatMap { evaluatorComponentId =>
      EvaluatorSettings.agentBindings(config, evaluatorComponentId, agentRoles).collect {
        case binding: SpiEvaluator.AgentBinding if binding.agentComponentId == agentComponentId =>
          AgentEvaluatorBinding(evaluatorComponentId, binding.samplingRatio, binding.triggerOnFailure)
      }
    }.asJava
  }

  /**
   * The component ids of every agent named under `agents` of an entry under `akka.javasdk.evaluation.evaluators`,
   * sorted.
   */
  def boundAgentComponentIds(config: Config): util.List[String] = {
    val evaluators =
      if (config.hasPath(EvaluatorsPath)) config.getObject(EvaluatorsPath).asScala.collect {
        case (id, _: ConfigObject) => id
      }
      else Nil
    evaluators.toSeq
      .flatMap { evaluatorComponentId =>
        val agentsPath = s"$EvaluatorsPath.$evaluatorComponentId.agents"
        if (config.hasPath(agentsPath))
          config.getObject(agentsPath).asScala.collect { case (id, _: ConfigObject) => id }
        else Nil
      }
      .distinct
      .sorted
      .asJava
  }

  /**
   * The component ids of the evaluator components the component descriptors on the class path list, sorted. A component
   * class that cannot be loaded is left out.
   */
  def evaluatorComponentIds(classLoader: ClassLoader): util.List[String] =
    ComponentLocator
      .findAllDescriptorFiles(classLoader)
      .flatMap { descriptor =>
        descriptor.components(ComponentLocator.EvaluatorKey) ++ descriptor.components(
          ComponentLocator.DurableEvaluatorKey)
      }
      .distinct
      .flatMap { className =>
        try Some(Reflect.readComponentId(Class.forName(className, false, classLoader)))
        catch { case _: ClassNotFoundException | _: LinkageError => None }
      }
      .distinct
      .sorted
      .asJava
}
