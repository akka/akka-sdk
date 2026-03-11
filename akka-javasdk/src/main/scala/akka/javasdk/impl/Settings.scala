/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.runtime.sdk.spi.SpiBackofficeSettings
import akka.runtime.sdk.spi.SpiSettings
import com.typesafe.config.Config

import Settings.DevModeSettings

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object Settings {

  def apply(sdkConfig: Config, spiSettings: SpiSettings): Settings = {
    val showProvidedComponents = {
      val key = "dev-mode.show-hidden-components"
      // hidden config, not in reference.conf
      if (sdkConfig.hasPath(key)) sdkConfig.getBoolean(key) else false
    }

    Settings(devModeSettings = Option.when(sdkConfig.getBoolean("dev-mode.enabled"))(
      DevModeSettings(
        serviceName = sdkConfig.getString("dev-mode.service-name"),
        httpPort = sdkConfig.getInt("dev-mode.http-port"),
        showProvidedComponents = showProvidedComponents,
        backoffice = spiSettings.devMode.map(_.backoffice).getOrElse(SpiBackofficeSettings.empty))))
  }

  final case class DevModeSettings(
      serviceName: String,
      httpPort: Int,
      showProvidedComponents: Boolean,
      backoffice: SpiBackofficeSettings)
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class Settings(devModeSettings: Option[DevModeSettings])
