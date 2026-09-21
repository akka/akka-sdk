/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.annotation.nowarn

import akka.annotation.InternalApi
import akka.javasdk.Sanitizer
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
   * The declarative entries, handed over before this service has any classes. The runtime builds the log sanitizer and
   * the service wide sanitizer from them.
   */
  def loadSettings(config: Config): SpiDataSanitizerSettings =
    new SpiDataSanitizerSettings(configuredSanitizers(config).flatMap(declarativeSpiSanitizer(_, Set.empty)))

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
      enabledForComponents: Set[String]): Option[SpiDataSanitizer] = {
    val useFor = sanitizer.useFor.map(_.configValue)
    sanitizer.kind match {
      case SanitizerKind.Pattern(regex) =>
        Some(new SpiDataSanitizer.Regex(sanitizer.name, regex, useFor, enabledForComponents, sanitizer.config))
      case SanitizerKind.Predefined(group) =>
        Some(new SpiDataSanitizer.Predefined(group, useFor, enabledForComponents, sanitizer.config))
      case _: SanitizerKind.Implementation => None
    }
  }

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
