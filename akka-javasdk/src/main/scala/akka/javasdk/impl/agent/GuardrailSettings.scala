/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
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

    // Placeholder for a "*" declaration. It expands to all four values above.
    final case object Wildcard extends UseFor {
      override def toString: String = "*"
    }
  }

  def apply(name: String, config: Config): ConfiguredGuardrail = {
    val useFor: Set[UseFor] = config
      .getOptionalStringSet("use-for")
      .map(_.toLowerCase(Locale.ROOT))
      .map {
        case "model-request"     => UseFor.ModelRequest
        case "model-response"    => UseFor.ModelResponse
        case "mcp-tool-request"  => UseFor.McpToolRequest
        case "mcp-tool-response" => UseFor.McpToolResponse
        case "*"                 => UseFor.Wildcard
        case other =>
          throw new IllegalArgumentException(
            s"Unknown use-for [$other] in guardrail configuration [$name]. use-for applies only to the " +
            "deprecated TextGuardrail. ToolCallGuardrail, ModelCallGuardrail and AgentResponseGuardrail " +
            "bind to their boundary by type and take no use-for.")
      }

    new ConfiguredGuardrail(
      name = name,
      implementationClass = config.getString("class"),
      agents = config.getOptionalStringSet("agents"),
      agentRoles = config.getOptionalStringSet("agent-roles"),
      category = config.getString("category"),
      reportOnly = config.getOptionalBoolean("report-only"),
      useFor = useFor,
      // Optional tool-name filter for a ToolCallGuardrail; empty means "all tools on the agent".
      tools = config.getOptionalStringSet("tools"),
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
    tools: Set[String],
    config: Config) {
  require(!name.isBlank, s"name must be defined for guardrail")
  require(!implementationClass.isBlank, s"implementation-class must be defined for guardrail [$name]")
}
