/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.time.Duration

import akka.annotation.InternalApi
import Settings.DevModeSettings
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object Settings {

  def apply(sdkConfig: Config): Settings = {
    Settings(
      snapshotEvery = sdkConfig.getInt("event-sourced-entity.snapshot-every"),
      cleanupDeletedEventSourcedEntityAfter = sdkConfig.getDuration("event-sourced-entity.cleanup-deleted-after"),
      cleanupDeletedKeyValueEntityAfter = sdkConfig.getDuration("key-value-entity.cleanup-deleted-after"),
      devModeSettings = Option.when(sdkConfig.getBoolean("dev-mode.enabled"))(
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
private[impl] final case class Settings(
    snapshotEvery: Int,
    cleanupDeletedEventSourcedEntityAfter: Duration,
    cleanupDeletedKeyValueEntityAfter: Duration,
    devModeSettings: Option[DevModeSettings])
