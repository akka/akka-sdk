/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object ClassifierSettings {
  def apply(config: Config): ClassifierSettings = {
    val configuredClassifiers =
      config.root.asScala.iterator.map {
        case (key, value: ConfigObject) =>
          ConfiguredClassifier(key, value.toConfig)
        case (key, value) =>
          throw new IllegalArgumentException(s"Classifier [$key] must be a config object, but was [${value.valueType}]")
      }.toSeq
    new ClassifierSettings(configuredClassifiers)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final case class ClassifierSettings(configuredClassifiers: Seq[ConfiguredClassifier]) {}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object ConfiguredClassifier {
  def apply(name: String, config: Config): ConfiguredClassifier = {
    if (!config.hasPath("class"))
      throw new IllegalArgumentException(s"Classifier [$name] must define [class]")
    new ConfiguredClassifier(name = name, implementationClass = config.getString("class"), config = config)
  }
}

/**
 * INTERNAL API
 *
 * Unlike [[ConfiguredGuardrail]], a classifier has no boundary binding (no `use-for`/`agents`) -- it is looked up by
 * name, not dispatched by the runtime.
 */
@InternalApi private[javasdk] final case class ConfiguredClassifier(
    name: String,
    implementationClass: String,
    config: Config) {
  require(!name.isBlank, s"name must be defined for classifier")
  require(!implementationClass.isBlank, s"implementation-class must be defined for classifier [$name]")
}
