/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util.Locale

import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

import akka.annotation.InternalApi
import akka.javasdk.impl.ConfiguredSanitizer.ApplyAt
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValueType

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object SanitizerSettings {

  def apply(config: Config): SanitizerSettings = {
    val configuredSanitizers =
      config.root.asScala.iterator
        .map {
          case (key, value: ConfigObject) => key -> value.toConfig
          case (key, value) =>
            throw new IllegalArgumentException(
              s"Sanitizer [$key] must be a config object, but was [${value.valueType}]")
        }
        .collect { case (key, entry) if enabled(entry) => ConfiguredSanitizer(key, entry) }
        .toSeq

    new SanitizerSettings(configuredSanitizers)
  }

  private def enabled(entry: Config): Boolean =
    !entry.hasPath("enabled") || entry.getBoolean("enabled")
}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final case class SanitizerSettings(configuredSanitizers: Seq[ConfiguredSanitizer])

/**
 * INTERNAL API
 *
 * What an entry masks with.
 */
@InternalApi private[javasdk] sealed trait SanitizerKind

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object SanitizerKind {
  final case class Pattern(regex: Regex) extends SanitizerKind
  final case class Predefined(group: String) extends SanitizerKind
  final case class Implementation(className: String) extends SanitizerKind
}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object ConfiguredSanitizer {

  /**
   * Where an entry masks. The values are application points, not the boundaries [[ConfiguredGuardrail.UseFor]] holds.
   */
  sealed abstract class ApplyAt(val configValue: String) {
    override def toString: String = configValue
  }

  object ApplyAt {
    case object ModelCall extends ApplyAt("model-call")
    case object ToolResult extends ApplyAt("tool-result")

    /**
     * Log messages and the subject id of an unhandled exception. Unlike the other two, this is not a point of an agent:
     * it decides whether the entry is handed over as a log sanitizer.
     */
    case object Logs extends ApplyAt("logs")

    /**
     * Nothing the runtime masks on its own. Application code calls the entry by name, which it can do with every entry.
     */
    case object Client extends ApplyAt("client")

    val All: Set[ApplyAt] = Set(ModelCall, ToolResult, Logs, Client)

    /** The points of an agent. */
    val AgentPoints: Set[ApplyAt] = Set(ModelCall, ToolResult)

    /** Every point the runtime masks at on its own. */
    val MaskingPoints: Set[ApplyAt] = AgentPoints + Logs
  }

  // Kept in step with the groups the runtime expands a predefined entry into, in
  // runtime/core/src/main/scala/kalix/runtime/sanitizer/SanitizerEngine.scala.
  val PredefinedGroups: Seq[String] = Seq("CREDIT_CARD", "IBAN", "PHONE", "EMAIL", "IP_ADDRESS")

  private val Detectors = Seq("pattern", "predefined", "class")

  def apply(name: String, config: Config): ConfiguredSanitizer = {
    val agents = optionalStringSet(config, "agents")
    val agentRoles = optionalStringSet(config, "agent-roles")
    val kind = readKind(name, config)
    val scoped = agents.nonEmpty || agentRoles.nonEmpty
    val implementation = kind.isInstanceOf[SanitizerKind.Implementation]

    new ConfiguredSanitizer(
      name = name,
      kind = kind,
      applyAt = readApplyAt(name, config, scoped, implementation),
      agents = agents,
      agentRoles = agentRoles,
      config = config)
  }

  private def readKind(name: String, config: Config): SanitizerKind = {
    val defined = Detectors.filter(config.hasPath)
    if (defined.size != 1)
      throw new IllegalArgumentException(
        s"Sanitizer [$name] must define exactly one of [${Detectors.mkString(", ")}], but defines " +
        s"[${defined.mkString(", ")}]")

    defined.head match {
      case "pattern" =>
        val pattern = config.getString("pattern")
        try SanitizerKind.Pattern(pattern.r)
        catch {
          case e: java.util.regex.PatternSyntaxException =>
            throw new IllegalArgumentException(
              s"Sanitizer [$name] has an invalid [pattern = $pattern]: ${e.getMessage}",
              e)
        }

      case "predefined" =>
        val group = config.getString("predefined")
        if (!PredefinedGroups.contains(group))
          throw new IllegalArgumentException(
            s"Sanitizer [$name] has unknown [predefined = $group], known groups are " +
            s"[${PredefinedGroups.mkString(", ")}]")
        SanitizerKind.Predefined(group)

      case _ =>
        val className = config.getString("class")
        if (className.isBlank)
          throw new IllegalArgumentException(s"Sanitizer [$name] must define a non empty [class]")
        SanitizerKind.Implementation(className)
    }
  }

  private def readApplyAt(name: String, config: Config, scoped: Boolean, implementation: Boolean): Set[ApplyAt] = {
    val declared = declaredApplyAt(config).map(_.toLowerCase(Locale.ROOT))
    val named: Set[ApplyAt] = declared.iterator
      .filterNot(_ == "*")
      .map {
        case "model-call"  => ApplyAt.ModelCall
        case "tool-result" => ApplyAt.ToolResult
        case "logs"        => ApplyAt.Logs
        case "client"      => ApplyAt.Client
        case other =>
          throw new IllegalArgumentException(
            s"Sanitizer [$name] has unknown apply-at [$other], valid values are " +
            s"[${ApplyAt.All.toSeq.map(_.configValue).sorted.mkString(", ")}] or [*]")
      }
      .toSet

    if (scoped && named.contains(ApplyAt.Logs))
      throw new IllegalArgumentException(
        s"Sanitizer [$name] cannot combine [agents] or [agent-roles] with the [logs] application point. A log " +
        "line belongs to no agent, so the runtime has no agent to match the scope against.")
    if (scoped && named.contains(ApplyAt.Client))
      throw new IllegalArgumentException(
        s"Sanitizer [$name] cannot combine [agents] or [agent-roles] with the [client] application point. A call " +
        "by name belongs to no agent, so the runtime has no agent to match the scope against. Every sanitizer " +
        "can be called by name, so leave out [client] to mask for the named agents.")

    // An entry that names no application point, or names "*", masks wherever the rest of it allows. An agent
    // scoped entry leaves out log messages, for the reason in the error above. A class leaves them out unless
    // apply-at names logs: the log engine calls it for every log line of the service, the runtime's included,
    // and waits for it on each one.
    if (declared.isEmpty || declared.contains("*")) {
      if (scoped || implementation) ApplyAt.AgentPoints else ApplyAt.MaskingPoints
    } else named
  }

  private def declaredApplyAt(config: Config): Seq[String] =
    if (!config.hasPath("apply-at")) Nil
    else if (config.getValue("apply-at").valueType == ConfigValueType.STRING) Seq(config.getString("apply-at"))
    else config.getStringList("apply-at").asScala.toSeq

  private def optionalStringSet(config: Config, path: String): Set[String] =
    if (config.hasPath(path)) config.getStringList(path).asScala.toSet else Set.empty
}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final case class ConfiguredSanitizer(
    name: String,
    kind: SanitizerKind,
    applyAt: Set[ApplyAt],
    agents: Set[String],
    agentRoles: Set[String],
    config: Config) {
  require(!name.isBlank, "name must be defined for sanitizer")

  def scoped: Boolean = agents.nonEmpty || agentRoles.nonEmpty

  /**
   * Whether the entry masks at an application point of an agent, and so is handed over with the agents it applies to.
   */
  def masksAgentText: Boolean = applyAt.exists(ApplyAt.AgentPoints.contains)

  /** Whether the entry masks log messages, and so is handed over as a log sanitizer. */
  def masksLogs: Boolean = applyAt.contains(ApplyAt.Logs)

  /** Whether the entry applies to the agent with this component id and role. */
  def appliesTo(componentId: String, role: Option[String]): Boolean =
    if (!scoped) true
    else
      agents.contains("*") || agents.contains(componentId) ||
      role.exists(r => agentRoles.contains("*") || agentRoles.contains(r))
}
