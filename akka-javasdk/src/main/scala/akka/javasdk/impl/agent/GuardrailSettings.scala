/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.Locale

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.agent.ToolCallGuardrail.ToolOrigin
import akka.javasdk.impl.ControlId
import akka.javasdk.impl.agent.ConfiguredGuardrail.UseFor
import akka.runtime.sdk.spi.SpiGuardrail
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

  private val failModesMap: Map[String, SpiGuardrail.FailMode] =
    Map("fail-closed" -> SpiGuardrail.FailMode.FailClosed, "fail-open" -> SpiGuardrail.FailMode.FailOpen)

  private def readFailMode(name: String, config: Config): SpiGuardrail.FailMode =
    if (!config.hasPath("fail-mode")) SpiGuardrail.FailMode.FailClosed
    else {
      val value = config.getString("fail-mode")
      failModesMap.getOrElse(
        value.toLowerCase(Locale.ROOT),
        throw new IllegalArgumentException(
          s"Unknown fail-mode [$value] in guardrail configuration [$name]. " +
          s"Use one of [${failModesMap.keys.mkString(", ")}]."))
    }

  sealed abstract class UseFor(val configName: String)
  final object UseFor {
    final case object ModelRequest extends UseFor("model-request")
    final case object ModelResponse extends UseFor("model-response")
    final case object McpToolRequest extends UseFor("mcp-tool-request")
    final case object McpToolResponse extends UseFor("mcp-tool-response")

    // Placeholder for a "*" declaration. It expands to all four values above.
    final case object Wildcard extends UseFor("*") {
      override def toString: String = "*"
    }

    val values: Seq[UseFor] = Seq(ModelRequest, ModelResponse, McpToolRequest, McpToolResponse, Wildcard)
  }

  /** The values of `tool-origins`, by the origin they select. */
  val ToolOriginNames: Seq[String] = Seq("function-tool", "remote-mcp", "remote-a2a")

  def toolOriginName(origin: ToolOrigin): String = origin match {
    case _: ToolOrigin.FunctionTool => "function-tool"
    case _: ToolOrigin.RemoteMcp    => "remote-mcp"
    case _: ToolOrigin.RemoteA2a    => "remote-a2a"
    case _: ToolOrigin.Unknown      => "unknown"
  }

  private def readToolOrigins(name: String, config: Config): Set[String] =
    config.getOptionalStringSet("tool-origins").map(_.toLowerCase(Locale.ROOT)).map { value =>
      if (ToolOriginNames.contains(value)) value
      else
        throw new IllegalArgumentException(
          s"Unknown tool-origins value [$value] in guardrail configuration [$name]. " +
          s"Use one or more of [${ToolOriginNames.mkString(", ")}].")
    }

  private def readRemoteAgents(name: String, config: Config, toolOrigins: Set[String]): Set[String] = {
    val remoteAgents = config.getOptionalStringSet("remote-agents").map(_.trim)
    if (remoteAgents.exists(_.isEmpty))
      throw new IllegalArgumentException(s"Guardrail [$name] has an empty value in remote-agents.")
    if (remoteAgents.nonEmpty && toolOrigins.nonEmpty && !toolOrigins.contains("remote-a2a"))
      throw new IllegalArgumentException(
        s"Guardrail [$name] sets remote-agents, which select calls to remote A2A agents, but its tool-origins " +
        s"[${toolOrigins.toSeq.sorted.mkString(", ")}] leave out remote-a2a.")
    remoteAgents
  }

  def normalizedUrl(url: String): String = url.trim.stripSuffix("/")

  def apply(name: String, config: Config): ConfiguredGuardrail = {
    val useFor: Set[UseFor] = config
      .getOptionalStringSet("use-for")
      .map(_.toLowerCase(Locale.ROOT))
      .map { value =>
        UseFor.values.find(_.configName == value).getOrElse {
          throw new IllegalArgumentException(
            s"Unknown use-for [$value] in guardrail configuration [$name]. use-for applies only to the " +
            "deprecated TextGuardrail. ToolCallGuardrail, ModelCallGuardrail and AgentResponseGuardrail " +
            "bind to their boundary by type and take no use-for.")
        }
      }

    val toolOrigins = readToolOrigins(name, config)

    new ConfiguredGuardrail(
      name = name,
      implementationClass = config.getString("class"),
      agents = config.getOptionalStringSet("agents"),
      agentRoles = config.getOptionalStringSet("agent-roles"),
      category = config.getString("category"),
      reportOnly = config.getOptionalBoolean("report-only"),
      failMode = readFailMode(name, config),
      useFor = useFor,
      // Optional tool-name filter for a ToolCallGuardrail; empty means "all tools on the agent".
      tools = config.getOptionalStringSet("tools"),
      toolOrigins = toolOrigins,
      remoteAgents = readRemoteAgents(name, config, toolOrigins),
      config = config,
      controlId = ControlId.read(config, s"Guardrail [$name]"))
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
    failMode: SpiGuardrail.FailMode,
    useFor: Set[UseFor],
    tools: Set[String],
    config: Config,
    controlId: Option[String],
    toolOrigins: Set[String] = Set.empty,
    remoteAgents: Set[String] = Set.empty) {
  require(!name.isBlank, s"name must be defined for guardrail")
  require(!implementationClass.isBlank, s"implementation-class must be defined for guardrail [$name]")

  /**
   * Whether `tools`, `tool-origins` and `remote-agents` select a call of the tool from the origin. An empty setting
   * selects every call. `remote-agents` selects only calls to remote A2A agents, by name or by card URL.
   */
  def selectsToolCall(toolName: String, origin: ToolOrigin): Boolean =
    (tools.isEmpty || tools.contains(toolName)) &&
    (toolOrigins.isEmpty || toolOrigins.contains(ConfiguredGuardrail.toolOriginName(origin))) &&
    (remoteAgents.isEmpty || (origin match {
      case a2a: ToolOrigin.RemoteA2a =>
        val cardUrl = ConfiguredGuardrail.normalizedUrl(a2a.cardUrl)
        remoteAgents.exists(agent => agent == a2a.agentName || ConfiguredGuardrail.normalizedUrl(agent) == cardUrl)
      case _ => false
    }))

  /** Whether the entry sets `tool-origins` or `remote-agents`, which only a ToolCallGuardrail takes. */
  def scopesToolOrigins: Boolean = config.hasPath("tool-origins") || config.hasPath("remote-agents")
}
