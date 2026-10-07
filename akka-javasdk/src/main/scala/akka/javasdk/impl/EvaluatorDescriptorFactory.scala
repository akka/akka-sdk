/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.impl.serialization.Serializer

/**
 * INTERNAL API
 *
 * Evaluators have a single abstract `evaluate` handler invoked directly by the runtime, so the component descriptor is
 * empty. The agent bindings are read from configuration (see [[akka.javasdk.impl.evaluation.EvaluatorSettings]]).
 */
@InternalApi
private[impl] object EvaluatorDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(component: Class[_], serializer: Serializer): ComponentDescriptor = {
    ComponentDescriptor(Map.empty)
  }
}
