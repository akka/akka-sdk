/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.time.Duration

import akka.runtime.sdk.spi.SpiDeployedEventingSettings
import akka.runtime.sdk.spi.SpiDevObjectStorageS3BucketConfig
import akka.runtime.sdk.spi.SpiDevObjectStorageS3PathAccessStyle
import akka.runtime.sdk.spi.SpiDevObjectStorageS3VirtualHostAccessStyle
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SpiSettingsSpec extends AnyWordSpec with Matchers with OptionValues {

  private def devS3Bucket(extraSettings: String): SpiDevObjectStorageS3BucketConfig = {
    val config: Config = ConfigFactory
      .parseString(s"""
        akka.javasdk.dev-mode.enabled = true
        akka.javasdk.dev-mode.object-storage.buckets = [{
          name = "datasets", provider = "s3", bucket = "datasets", region = "us-east-1"
          credentials { type = "static", access-key-id = "AKID", secret-access-key = "secret" }
          $extraSettings
        }]
      """)
      .withFallback(ConfigFactory.load())
    val buckets = SdkRunner.extractSpiSettings(config).devMode.value.objectStorageBuckets
    buckets should have size 1
    buckets.head.asInstanceOf[SpiDevObjectStorageS3BucketConfig]
  }

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

    "leave a dev-mode S3 bucket addressing Amazon S3 when no endpoint url is set" in {
      val s3 = devS3Bucket("")
      s3.name shouldBe "datasets"
      s3.region shouldBe "us-east-1"
      s3.endpointUrl shouldBe None
      s3.accessStyle shouldBe None
    }

    "parse an endpoint url on a dev-mode S3 bucket" in {
      val s3 = devS3Bucket("""endpoint-url = "http://localhost:9000"""")
      s3.endpointUrl shouldBe Some("http://localhost:9000")
      // not set here, so the runtime applies path access style because the endpoint url is set
      s3.accessStyle shouldBe None
    }

    "parse an explicit access style on a dev-mode S3 bucket" in {
      val path = devS3Bucket("""
        endpoint-url = "http://localhost:9000"
        access-style = path
      """)
      path.accessStyle shouldBe Some(SpiDevObjectStorageS3PathAccessStyle)

      val virtual = devS3Bucket("""
        endpoint-url = "http://{bucket}.localhost:9000"
        access-style = virtual
      """)
      virtual.accessStyle shouldBe Some(SpiDevObjectStorageS3VirtualHostAccessStyle)
    }

    "fail on an unknown access style for a dev-mode S3 bucket" in {
      val ex = intercept[IllegalArgumentException] {
        devS3Bucket("""access-style = sideways""")
      }
      ex.getMessage should include("Unknown S3 access style")
      ex.getMessage should include("sideways")
      ex.getMessage should include("datasets")
    }

    "use 7 days for cleanup of deleted entities by default" in {
      SdkRunner.extractSpiSettings(ConfigFactory.load()).cleanupDeletedEntityAfter shouldBe Duration.ofDays(7)
    }

    "use the configured cleanup of deleted entities" in {
      val config = ConfigFactory
        .parseString("akka.javasdk.entity.cleanup-deleted-after = 3 days")
        .withFallback(ConfigFactory.load())
      SdkRunner.extractSpiSettings(config).cleanupDeletedEntityAfter shouldBe Duration.ofDays(3)
    }

    "fail on the unsupported event-sourced-entity.cleanup-deleted-after setting" in {
      val config = ConfigFactory
        .parseString("akka.javasdk.event-sourced-entity.cleanup-deleted-after = 3 days")
        .withFallback(ConfigFactory.load())
      val ex = intercept[IllegalArgumentException] {
        SdkRunner.extractSpiSettings(config)
      }
      ex.getMessage should include("akka.javasdk.event-sourced-entity.cleanup-deleted-after")
      ex.getMessage should include("akka.javasdk.entity.cleanup-deleted-after")
    }

    "fail on the unsupported event-sourced-entity.cleanup-deleted-after setting when both settings are defined" in {
      val config = ConfigFactory
        .parseString("""
          akka.javasdk.entity.cleanup-deleted-after = 3 days
          akka.javasdk.event-sourced-entity.cleanup-deleted-after = 3 days
        """)
        .withFallback(ConfigFactory.load())
      val ex = intercept[IllegalArgumentException] {
        SdkRunner.extractSpiSettings(config)
      }
      ex.getMessage should include("akka.javasdk.event-sourced-entity.cleanup-deleted-after")
      ex.getMessage should include("akka.javasdk.entity.cleanup-deleted-after")
    }
  }

}
