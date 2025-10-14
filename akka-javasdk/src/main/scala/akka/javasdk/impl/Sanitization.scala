/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.Sanitizer
import akka.runtime.sdk.spi.SpiDataSanitizer
import akka.runtime.sdk.spi.SpiDataSanitizerSettings
import akka.runtime.sdk.spi.SpiSanitizerEngine
import com.typesafe.config.Config

import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object Sanitization {

  def loadSettings(config: Config): SpiDataSanitizerSettings = {
    val enabledPredefined = config
      .getStringList("akka.javasdk.sanitization.predefined-sanitizers")
      .asScala
      .map(name => new SpiDataSanitizer.SpiPredefinedSanitizer(name))
      .toVector
    val regexConfig = config.getObject("akka.javasdk.sanitization.regex-sanitizers")

    val regexEntries = regexConfig
      .keySet()
      .asScala
      .map { key =>
        val config = regexConfig.toConfig.getConfig(key)
        if (!config.hasPath("pattern"))
          throw new IllegalArgumentException(
            s"Sanitizer config for [akka.javasdk.sanitization.regex-sanitizers.$key] is missing the key 'pattern' (which should contain a Java regular expression)")
        val regex = config.getString("pattern").r

        new SpiDataSanitizer.SpiRegexSanitizer(key, regex)
      }
      .toVector

    new SpiDataSanitizerSettings(enabledPredefined ++ regexEntries)
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class SanitizerImpl(spiSantizier: SpiSanitizerEngine) extends Sanitizer {
  override def sanitize(string: String): String = spiSantizier.deIdentify(string)
}
