/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.time.Duration

import scala.concurrent.duration.DurationInt

import akka.runtime.sdk.spi.SpiA2aAuth
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

  // (toolCallIntent, guardrailAllow, sanitizerNoMasking)
  private def ledgerRecords(settings: String): (Boolean, Boolean, Boolean) = {
    val records = SdkRunner
      .extractSpiSettings(ConfigFactory.parseString(settings).withFallback(ConfigFactory.load()))
      .ledgerSettings
    (records.toolCallIntent, records.guardrailAllow, records.sanitizerNoMasking)
  }

  "The SDK settings" should {
    "pass the remote A2A agents of the service on to the runtime, with a token from the environment" in {
      // `${?A2A_TEST_TOKEN}` is the form the docs use, it takes a system property here in place of an env var
      System.setProperty("A2A_TEST_TOKEN", "from-the-environment")
      try {
        ConfigFactory.invalidateCaches()
        val config = ConfigFactory
          .parseString("""
            akka.javasdk.agent.a2a-client.agents {
              claims {
                card-url = "https://claims.example.com/card"
                auth { type = Bearer, token = ${?A2A_TEST_TOKEN} }
                allowed-hosts = ["rpc.example.com", "other.example.com:8443"]
                request-timeout = 7s
              }
              keyed {
                card-url = "http://localhost:8000/card"
                auth { type = header, header-name = "x-api-key", header-value = "k" }
              }
              "odd.name".card-url = "http://localhost:1/card"
            }
            """)
          .withFallback(ConfigFactory.load())
          .resolve()
        val agents = SdkRunner.extractSpiSettings(config).a2aAgentSettings
        agents.keySet shouldBe Set("claims", "keyed", "odd.name")

        val claims = agents("claims")
        claims.cardUrl shouldBe "https://claims.example.com/card"
        claims.auth shouldBe new SpiA2aAuth.Bearer("from-the-environment")
        claims.allowedHosts shouldBe Set("rpc.example.com", "other.example.com:8443")
        claims.requestTimeout shouldBe Some(7.seconds)

        agents("keyed").auth shouldBe new SpiA2aAuth.Header("x-api-key", "k")
        agents("keyed").requestTimeout shouldBe None
        agents("odd.name").auth shouldBe SpiA2aAuth.NoAuth
      } finally {
        System.clearProperty("A2A_TEST_TOKEN")
        ConfigFactory.invalidateCaches()
      }
    }

    "have no remote A2A agents by default" in {
      SdkRunner.extractSpiSettings(ConfigFactory.load()).a2aAgentSettings shouldBe empty
    }

    "treat a remote A2A agent without card-url as not configured" in {
      val config = ConfigFactory
        .parseString("""
          akka.javasdk.agent.a2a-client.agents.claims {
            card-url = ${?A2A_NOT_SET_CARD_URL}
            auth { type = bearer, token = ${?A2A_NOT_SET_TOKEN} }
          }
          """)
        .withFallback(ConfigFactory.load())
        .resolve()
      SdkRunner.extractSpiSettings(config).a2aAgentSettings shouldBe empty
    }

    "fail at start for a remote A2A agent that is not valid, naming the key" in {
      def failure(agent: String): String = {
        val config = ConfigFactory
          .parseString(
            s"akka.javasdk.agent.a2a-client.agents.claims { card-url = \"https://claims.example.com/card\"\n$agent }")
          .withFallback(ConfigFactory.load())
        val e = the[IllegalArgumentException] thrownBy SdkRunner.extractSpiSettings(config)
        e.getMessage
      }

      failure("auth.type = oauth") should (include("[claims]") and include("auth.type") and include("oauth"))
      failure("auth.type = bearer") should (include("[claims]") and include("auth.token"))
      failure("auth { type = header, header-name = x }") should include("auth.header-value")
      failure("auth { type = bearer, token = \"\" }") should include("auth.token")
      failure("allowed-hosts = [\"https://other.example.com/path\"]") should include("allowed-hosts")
      failure("request-timeout = 0s") should include("request-timeout")
      failure("request-timeout = -1s") should include("request-timeout")
    }

    "not show the card URL when it is not valid" in {
      Seq(
        "ftp://claims.example.com/card?key=SECRET-KEY",
        "https://claims.example.com/card?key=SECRET KEY",
        "/card?key=SECRET-KEY")
        .foreach { url =>
          val config = ConfigFactory
            .parseString(s"""akka.javasdk.agent.a2a-client.agents.claims.card-url = "$url" """)
            .withFallback(ConfigFactory.load())
          val e = the[IllegalArgumentException] thrownBy SdkRunner.extractSpiSettings(config)
          e.getMessage should (include("[claims]") and include("card-url"))
          (e.getMessage should not).include("SECRET")
          (e.getMessage should not).include("claims.example.com")
        }
    }

    "fail at start when a remote A2A agent is not a configuration section" in {
      val config = ConfigFactory
        .parseString("akka.javasdk.agent.a2a-client.agents.claims = 5")
        .withFallback(ConfigFactory.load())
      val e = the[IllegalArgumentException] thrownBy SdkRunner.extractSpiSettings(config)
      e.getMessage shouldBe
      "The remote A2A agent [claims] is not valid: [akka.javasdk.agent.a2a-client.agents.claims] must be a configuration section"
    }

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

    "enable the ledger by default" in {
      SdkRunner.extractSpiSettings(ConfigFactory.load()).ledgerSettings.enabled shouldBe true
    }

    "disable the ledger with ledger.enabled = false" in {
      val config = ConfigFactory
        .parseString("akka.javasdk.agent.ledger.enabled = false")
        .withFallback(ConfigFactory.load())
      SdkRunner.extractSpiSettings(config).ledgerSettings.enabled shouldBe false
    }

    "keep every optional ledger record by default" in {
      ledgerRecords("") shouldBe ((true, true, true))
    }

    "turn off only the tool call intent record" in {
      ledgerRecords("akka.javasdk.agent.ledger.records.tool-call-intent = false") shouldBe ((false, true, true))
    }

    "turn off only the guardrail allow record" in {
      ledgerRecords("akka.javasdk.agent.ledger.records.guardrail-allow = false") shouldBe ((true, false, true))
    }

    "turn off only the sanitizer no masking record" in {
      ledgerRecords("akka.javasdk.agent.ledger.records.sanitizer-no-masking = false") shouldBe ((true, true, false))
    }

    "apply the optional ledger records in dev mode, where the ledger is always enabled" in {
      val config = ConfigFactory
        .parseString("""
          akka.javasdk.dev-mode.enabled = true
          akka.javasdk.agent.ledger.enabled = false
          akka.javasdk.agent.ledger.records.guardrail-allow = false
        """)
        .withFallback(ConfigFactory.load())
      val settings = SdkRunner.extractSpiSettings(config)
      settings.ledgerSettings.enabled shouldBe true
      settings.ledgerSettings.guardrailAllow shouldBe false
      settings.ledgerSettings.toolCallIntent shouldBe true
      settings.ledgerSettings.sanitizerNoMasking shouldBe true
    }
  }

}
