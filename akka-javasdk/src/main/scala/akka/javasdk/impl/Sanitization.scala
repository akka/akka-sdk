/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.annotation.nowarn

import akka.annotation.InternalApi
import akka.javasdk.Sanitizer
import akka.javasdk.impl.ConfiguredSanitizer.ApplyAt
import akka.runtime.sdk.spi.SpiDataSanitizer
import akka.runtime.sdk.spi.SpiDataSanitizerSettings
import akka.runtime.sdk.spi.SpiSanitizerEngine
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object Sanitization {

  val SanitizationPath = "akka.javasdk.sanitization"
  val SanitizersPath = s"$SanitizationPath.sanitizers"

  private val RemovedKeys =
    Map(s"$SanitizationPath.regex-sanitizers" -> "pattern", s"$SanitizationPath.predefined-sanitizers" -> "predefined")

  /**
   * The declarative entries, handed over before this service has any classes. The runtime builds the service wide
   * sanitizer from them. It builds the engine that masks log messages from the log sanitizers of
   * [[akka.runtime.sdk.spi.SpiSanitizerSetup]] instead, once this service is handed over, so that a sanitizer this
   * service implements can mask log messages too.
   */
  def loadSettings(config: Config): SpiDataSanitizerSettings =
    new SpiDataSanitizerSettings(configuredSanitizers(config).flatMap(sanitizer =>
      declarativeSpiSanitizer(sanitizer, spiApplyAt(sanitizer), Set.empty)))

  def configuredSanitizers(config: Config): Seq[ConfiguredSanitizer] = {
    checkRemovedKeys(config)
    SanitizerSettings(config.getConfig(SanitizersPath)).configuredSanitizers
  }

  /**
   * The runtime entry of a pattern or predefined sanitizer. A sanitizer the SDK implements has none, it is carried with
   * its instance on [[akka.runtime.sdk.spi.SpiSanitizerSetup]].
   */
  def declarativeSpiSanitizer(
      sanitizer: ConfiguredSanitizer,
      applyAt: Set[SpiDataSanitizer.ApplyAt],
      enabledForComponents: Set[String]): Option[SpiDataSanitizer] =
    sanitizer.kind match {
      case SanitizerKind.Pattern(regex) =>
        Some(new SpiDataSanitizer.Regex(sanitizer.name, regex, applyAt, enabledForComponents, sanitizer.config))
      case SanitizerKind.Predefined(group) =>
        Some(new SpiDataSanitizer.Predefined(sanitizer.name, group, applyAt, enabledForComponents, sanitizer.config))
      case _: SanitizerKind.Implementation => None
    }

  /**
   * What the entry is used for at the points of an agent. Log messages are not one of them: an entry that masks them is
   * also handed over as a [[akka.runtime.sdk.spi.SpiLogSanitizer]].
   */
  def spiApplyAt(sanitizer: ConfiguredSanitizer): Set[SpiDataSanitizer.ApplyAt] = {
    val applyAt: Set[SpiDataSanitizer.ApplyAt] = sanitizer.applyAt.collect {
      case ApplyAt.ModelCall  => SpiDataSanitizer.ApplyAt.ModelCall
      case ApplyAt.ToolResult => SpiDataSanitizer.ApplyAt.ToolResult
      case ApplyAt.Client     => SpiDataSanitizer.ApplyAt.Client
    }
    // The runtime reads no value as every point, so an entry that masks log messages only masks nothing on its own
    // at the points of an agent.
    if (applyAt.isEmpty) MasksNothingOnItsOwn else applyAt
  }

  /** How the runtime is told that an entry masks nothing at the points of an agent. */
  val MasksNothingOnItsOwn: Set[SpiDataSanitizer.ApplyAt] = Set(SpiDataSanitizer.ApplyAt.Client)

  private def checkRemovedKeys(config: Config): Unit =
    RemovedKeys.foreach { case (path, key) =>
      if (config.hasPath(path))
        throw new IllegalArgumentException(
          s"Configuration [$path] is not used. Configure each sanitizer as a named entry of [$SanitizersPath] " +
          s"with a [$key] key.")
    }
}

/**
 * INTERNAL API
 */
@InternalApi
@nowarn("msg=deprecated")
private[impl] final case class SanitizerImpl(spiSantizier: SpiSanitizerEngine) extends Sanitizer {
  override def sanitize(string: String): String = spiSantizier.sanitize(string)
}
