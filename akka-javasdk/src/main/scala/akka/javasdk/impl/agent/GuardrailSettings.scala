/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.Locale

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.impl.agent.ConfiguredGuardrail.UseFor
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object GuardrailSettings {
  def apply(config: Config): GuardrailSettings = {
    val configuredGuardrails =
      config.root.asScala.iterator.collect { case (key, value: ConfigObject) =>
        ConfiguredGuardrail(key, value.toConfig)
      }.toSeq
    new GuardrailSettings(configuredGuardrails)
  }

}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final case class GuardrailSettings(configuredGuardrails: Seq[ConfiguredGuardrail]) {}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object ConfiguredGuardrail {
  implicit class ConfigOps(val config: Config) extends AnyVal {
    def getOptionalStringSet(path: String): Set[String] = {
      if (config.hasPath(path))
        config.getStringList(path).asScala.toSet
      else
        Set.empty
    }

    def getOptionalBoolean(path: String): Boolean = {
      if (config.hasPath(path))
        config.getBoolean(path)
      else
        false
    }
  }

  sealed trait UseFor
  final object UseFor {
    final case object ModelRequest extends UseFor
    final case object ModelResponse extends UseFor
    final case object McpToolRequest extends UseFor
    final case object McpToolResponse extends UseFor

    val all: Seq[UseFor] = ModelRequest :: ModelResponse :: McpToolRequest :: McpToolResponse :: Nil
  }

  def apply(name: String, config: Config): ConfiguredGuardrail = {
    val useFor = config
      .getStringList("use-for")
      .iterator
      .asScala
      .map(_.toLowerCase(Locale.ROOT))
      .flatMap {
        case "model-request"     => UseFor.ModelRequest :: Nil
        case "model-response"    => UseFor.ModelResponse :: Nil
        case "mcp-tool-request"  => UseFor.McpToolRequest :: Nil
        case "mcp-tool-response" => UseFor.McpToolResponse :: Nil
        case "*"                 => UseFor.all
        case other =>
          throw new IllegalArgumentException(s"Unknown use-for [$other] in guardrail configuration [$name]")
      }
      .toSet

    new ConfiguredGuardrail(
      name = name,
      implementationClass = config.getString("class"),
      agents = config.getOptionalStringSet("agents"),
      agentRoles = config.getOptionalStringSet("agent-roles"),
      category = config.getString("category"),
      reportOnly = config.getOptionalBoolean("report-only"),
      useFor = useFor,
      config = config)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final case class ConfiguredGuardrail(
    name: String,
    implementationClass: String,
    agents: Set[String],
    agentRoles: Set[String],
    category: String,
    reportOnly: Boolean,
    useFor: Set[UseFor],
    config: Config) {
  require(!name.isBlank, s"name must be defined for guardrail")
  require(!implementationClass.isBlank, s"implementation-class must be defined for guardrail [$name]")
}
