/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.runtime.sdk.spi.SpiDeployedEventingSettings
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SpiSettingsSpec extends AnyWordSpec with Matchers with OptionValues {

  "The SDK settings" should {
    "parse all valid google pub sub modes" in {
      val modes = Seq(
        "automatic" -> SpiDeployedEventingSettings.Automatic,
        "automatic-subscription" -> SpiDeployedEventingSettings.AutomaticSubscription,
        "manual" -> SpiDeployedEventingSettings.Manual)

      val defaults = ConfigFactory.load()

      modes.map { case (name, mode) =>
        val config =
          ConfigFactory
            .parseString(s"""akka.javasdk.eventing.google-pubsub.mode = $name""")
            .withFallback(defaults)
        val pubSubOverrides = SdkRunner
          .extractSpiSettings(config)
          .eventingSettings
          .get
          .overrides
          .collect { case g: SpiDeployedEventingSettings.GooglePubSubOverrides => g }
        pubSubOverrides should have size 1
        pubSubOverrides.head.mode.get shouldBe mode

      }
    }
  }

}
