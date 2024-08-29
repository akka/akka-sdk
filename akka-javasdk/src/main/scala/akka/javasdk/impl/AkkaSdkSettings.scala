/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.time.Duration
import akka.annotation.InternalApi
import AkkaSdkSettings.DevModeSettings
import akka.actor.typed.ActorSystem

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object AkkaSdkSettings {

  def apply(system: ActorSystem[_]): AkkaSdkSettings = {
    // note: some config is for the runtime and some for the sdk only, with two different config namespaces
    val sdkConfig = system.settings.config.getConfig("akka.javasdk")
    val runtimeConfig = system.settings.config.getConfig("akka.runtime")

    AkkaSdkSettings(
      snapshotEvery = sdkConfig.getInt("event-sourced-entity.snapshot-every"),
      cleanupDeletedEventSourcedEntityAfter = sdkConfig.getDuration("event-sourced-entity.cleanup-deleted-after"),
      cleanupDeletedValueEntityAfter = sdkConfig.getDuration("value-entity.cleanup-deleted-after"),
      devModeSettings = Option.when(runtimeConfig.getBoolean("dev-mode.enabled"))(
        DevModeSettings(
          serviceName = sdkConfig.getString("dev-mode.service-name"),
          httpPort = runtimeConfig.getInt("dev-mode.http-port"))))
  }

  final case class DevModeSettings(serviceName: String, httpPort: Int)
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class AkkaSdkSettings(
    snapshotEvery: Int,
    cleanupDeletedEventSourcedEntityAfter: Duration,
    cleanupDeletedValueEntityAfter: Duration,
    devModeSettings: Option[DevModeSettings])
