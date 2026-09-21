/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util.Locale

import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

import akka.annotation.InternalApi
import akka.javasdk.impl.ConfiguredSanitizer.UseFor
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

    checkUniqueRuntimeNames(configuredSanitizers)
    new SanitizerSettings(configuredSanitizers)
  }

  private def enabled(entry: Config): Boolean =
    !entry.hasPath("enabled") || entry.getBoolean("enabled")

  private def checkUniqueRuntimeNames(configuredSanitizers: Seq[ConfiguredSanitizer]): Unit =
    configuredSanitizers.groupBy(_.runtimeName).foreach {
      case (runtimeName, entries) if entries.size > 1 =>
        throw new IllegalArgumentException(
          s"Sanitizers [${entries.map(_.name).sorted.mkString(", ")}] are all handed to the runtime as " +
          s"[$runtimeName]. The runtime identifies a predefined sanitizer by its group name, so a group can be " +
          "configured once and no other entry can take its name.")
      case _ => ()
    }
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
  sealed abstract class UseFor(val configValue: String) {
    override def toString: String = configValue
  }

  object UseFor {
    case object ModelInput extends UseFor("model-input")
    case object ToolResult extends UseFor("tool-result")
    case object Logs extends UseFor("logs")

    val All: Set[UseFor] = Set(ModelInput, ToolResult, Logs)

    /** The points an entry masks at when it cannot mask log messages. */
    val AgentPoints: Set[UseFor] = Set(ModelInput, ToolResult)
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
      useFor = readUseFor(name, config, scoped, implementation),
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

  private def readUseFor(name: String, config: Config, scoped: Boolean, implementation: Boolean): Set[UseFor] = {
    val declared = declaredUseFor(config).map(_.toLowerCase(Locale.ROOT))
    val named: Set[UseFor] = declared.iterator
      .filterNot(_ == "*")
      .map {
        case "model-input" => UseFor.ModelInput
        case "tool-result" => UseFor.ToolResult
        case "logs"        => UseFor.Logs
        case other =>
          throw new IllegalArgumentException(
            s"Sanitizer [$name] has unknown use-for [$other], valid values are " +
            s"[${UseFor.All.toSeq.map(_.configValue).sorted.mkString(", ")}] or [*]")
      }
      .toSet

    if (named.contains(UseFor.Logs)) {
      if (implementation)
        throw new IllegalArgumentException(
          s"Sanitizer [$name] cannot combine [class] with the [logs] application point. The runtime installs the " +
          "log sanitizer from the settings it is given at startup, before the SDK can construct a class, and the " +
          "log layout keeps the first engine it is given.")
      if (scoped)
        throw new IllegalArgumentException(
          s"Sanitizer [$name] cannot combine [agents] or [agent-roles] with the [logs] application point. A log " +
          "line belongs to no agent, so the runtime has no agent to match the scope against.")
    }

    // An entry that names no application point, or names "*", masks wherever the rest of it allows. A
    // class based or agent scoped entry leaves out log messages, for the reasons in the errors above.
    if (declared.isEmpty || declared.contains("*")) {
      if (implementation || scoped) UseFor.AgentPoints else UseFor.All
    } else named
  }

  private def declaredUseFor(config: Config): Seq[String] =
    if (!config.hasPath("use-for")) Nil
    else if (config.getValue("use-for").valueType == ConfigValueType.STRING) Seq(config.getString("use-for"))
    else config.getStringList("use-for").asScala.toSeq

  private def optionalStringSet(config: Config, path: String): Set[String] =
    if (config.hasPath(path)) config.getStringList(path).asScala.toSet else Set.empty
}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final case class ConfiguredSanitizer(
    name: String,
    kind: SanitizerKind,
    useFor: Set[UseFor],
    agents: Set[String],
    agentRoles: Set[String],
    config: Config) {
  require(!name.isBlank, "name must be defined for sanitizer")

  // The runtime resolves the patterns of a predefined entry by the name the entry is registered under, so a
  // predefined entry travels under its group name. SanitizerProvider translates a by-name call to this.
  def runtimeName: String = kind match {
    case SanitizerKind.Predefined(group) => group
    case _                               => name
  }

  def scoped: Boolean = agents.nonEmpty || agentRoles.nonEmpty

  /** Whether the entry applies to the agent with this component id and role. */
  def appliesTo(componentId: String, role: Option[String]): Boolean =
    if (!scoped) true
    else
      agents.contains("*") || agents.contains(componentId) ||
      role.exists(r => agentRoles.contains("*") || agentRoles.contains(r))
}
