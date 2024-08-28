/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.time.Duration

import akka.annotation.InternalApi
import AkkaPlatformSdkSettings.DevModeSettings
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi
object AkkaPlatformSdkSettings {

  object DevModeSettings {

    def apply(config: Config): Option[DevModeSettings] =
      Option.when(config.getBoolean("dev-mode.enabled")) {
        DevModeSettings(
          serviceName = config.getString("dev-mode.service-name"),
          httpPort = config.getInt("dev-mode.http-port"))
      }
  }
  case class DevModeSettings(serviceName: String, httpPort: Int)
}

/**
 * INTERNAL API
 */
@InternalApi
final case class AkkaPlatformSdkSettings(
    snapshotEvery: Int,
    cleanupDeletedEventSourcedEntityAfter: Duration,
    cleanupDeletedValueEntityAfter: Duration,
    devModeSettings: Option[DevModeSettings]) {

  def this(config: Config) = {
    this(
      snapshotEvery = config.getInt("event-sourced-entity.snapshot-every"),
      cleanupDeletedEventSourcedEntityAfter = config.getDuration("event-sourced-entity.cleanup-deleted-after"),
      cleanupDeletedValueEntityAfter = config.getDuration("value-entity.cleanup-deleted-after"),
      devModeSettings = DevModeSettings(config))
  }
}
