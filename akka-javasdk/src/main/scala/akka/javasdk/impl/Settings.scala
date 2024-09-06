/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.time.Duration
import akka.annotation.InternalApi
import Settings.DevModeSettings
import akka.actor.typed.ActorSystem

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object Settings {

  def apply(system: ActorSystem[_]): Settings = {
    // note: some config is for the runtime and some for the sdk only, with two different config namespaces
    val sdkConfig = system.settings.config.getConfig("akka.javasdk")

    Settings(
      snapshotEvery = sdkConfig.getInt("event-sourced-entity.snapshot-every"),
      cleanupDeletedEventSourcedEntityAfter = sdkConfig.getDuration("event-sourced-entity.cleanup-deleted-after"),
      cleanupDeletedValueEntityAfter = sdkConfig.getDuration("value-entity.cleanup-deleted-after"),
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
    cleanupDeletedValueEntityAfter: Duration,
    devModeSettings: Option[DevModeSettings])
