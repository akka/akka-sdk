/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.annotations.EvaluatesAgent
import akka.javasdk.annotations.EvaluatesAgents
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.SpiEvaluator

/**
 * INTERNAL API
 *
 * Evaluators have a single abstract `evaluate` handler invoked directly by the runtime, so the component descriptor is
 * empty. The agent bindings are read from the [[EvaluatesAgent]] annotations.
 */
@InternalApi
private[impl] object EvaluatorDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(component: Class[_], serializer: Serializer): ComponentDescriptor = {
    ComponentDescriptor(Map.empty)
  }

  /** Read the `@EvaluatesAgent` annotations on the evaluator class into SPI agent bindings. */
  def agentBindings(component: Class[_]): Seq[SpiEvaluator.Binding] = {
    val repeated = component.getAnnotationsByType(classOf[EvaluatesAgent])
    val fromContainer =
      Option(component.getAnnotation(classOf[EvaluatesAgents])).map(_.value()).getOrElse(Array.empty[EvaluatesAgent])
    // getAnnotationsByType already unwraps the container, but be defensive against duplicates
    (repeated ++ fromContainer).distinctBy(_.componentId()).toSeq.map { ann =>
      new SpiEvaluator.AgentBinding(ann.componentId(), SpiEvaluator.AgentBindingEvent.Interaction)
    }
  }
}
