/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import com.typesafe.config.Config

import Settings.DevModeSettings

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object Settings {

  def apply(sdkConfig: Config): Settings = {
    Settings(devModeSettings = Option.when(sdkConfig.getBoolean("dev-mode.enabled"))(
      DevModeSettings(
        serviceName = sdkConfig.getString("dev-mode.service-name"),
        httpPort = sdkConfig.getInt("dev-mode.http-port"))))
  }

  final case class DevModeSettings(serviceName: String, httpPort: Int)
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class Settings(devModeSettings: Option[DevModeSettings])
