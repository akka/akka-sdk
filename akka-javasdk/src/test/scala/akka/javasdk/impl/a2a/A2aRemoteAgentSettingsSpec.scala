/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.nio.file.Files

import scala.concurrent.duration._

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class A2aRemoteAgentSettingsSpec extends AnyWordSpec with Matchers {

  private val a2aReference: Config = ConfigFactory.load().getConfig("akka.javasdk.a2a")

  private def a2a(hocon: String): Config =
    ConfigFactory.parseString(hocon).withFallback(a2aReference)

  "The remote agent settings" should {

    "read a minimal named agent and take the defaults" in {
      val settings = A2aRemoteAgentSettings.resolve(
        a2a("""remote-agents.claims.card-url = "https://claims.example.com/.well-known/agent-card.json" """),
        "claims")
      settings.cardUrl shouldBe "https://claims.example.com/.well-known/agent-card.json"
      settings.timeout shouldBe 60.seconds
      settings.pollInterval shouldBe 30.seconds
      settings.cardRefresh shouldBe 10.minutes
      settings.auth shouldBe A2aAuth.NoAuth
      settings.caBundle shouldBe None
    }

    "read every setting of an agent" in {
      val settings = A2aRemoteAgentSettings.resolve(
        a2a("""
          remote-agents.claims {
            card-url = "https://claims.example.com/card.json"
            timeout = 5s
            poll-interval = 2s
            card-refresh = 1m
            auth {
              type = oauth2-client-credentials
              token-url = "https://login.example.com/token"
              client-id = me
              client-secret = s3cret
              scope = "a b"
            }
            ca-bundle = "-----BEGIN CERTIFICATE-----"
            client-certificate = "cert"
            client-key = "key"
          }"""),
        "claims")
      settings.timeout shouldBe 5.seconds
      settings.pollInterval shouldBe 2.seconds
      settings.cardRefresh shouldBe 1.minute
      settings.auth shouldBe A2aAuth.OAuth2ClientCredentials(
        "https://login.example.com/token",
        "me",
        "s3cret",
        Some("a b"))
      settings.caBundle shouldBe Some("-----BEGIN CERTIFICATE-----")
      settings.clientCertificate shouldBe Some("cert")
    }

    "read a header credential" in {
      val settings = A2aRemoteAgentSettings.resolve(
        a2a("""remote-agents.claims {
          card-url = "https://claims.example.com/card.json"
          auth { type = header, header-name = X-Api-Key, header-value = abc }
        }"""),
        "claims")
      settings.auth shouldBe A2aAuth.Header("X-Api-Key", "abc")
    }

    "treat an empty value, which an unset environment variable can leave, as absent" in {
      val settings = A2aRemoteAgentSettings.resolve(
        a2a("""remote-agents.claims { card-url = "https://x.example.com/card.json", ca-bundle = "" }"""),
        "claims")
      settings.caBundle shouldBe None
    }

    "accept an agent by card URL, with the defaults and no credentials" in {
      val settings = A2aRemoteAgentSettings.resolve(a2a(""), "http://localhost:8080/.well-known/agent-card.json")
      settings.cardUrl shouldBe "http://localhost:8080/.well-known/agent-card.json"
      settings.auth shouldBe A2aAuth.NoAuth
      settings.timeout shouldBe 60.seconds
    }

    "reject an unknown name and list the configured names" in {
      val e = intercept[IllegalArgumentException](
        A2aRemoteAgentSettings
          .resolve(a2a("""remote-agents.claims.card-url = "https://x.example.com/c.json" """), "other"))
      e.getMessage should include("[other]")
      e.getMessage should include("claims")
    }

    "reject a missing card URL" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(a2a("remote-agents.claims.timeout = 1s"), "claims")).getMessage should include(
        "remote-agents.claims.card-url must be set")
    }

    "reject a card URL that is not http or https" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims.card-url = "ftp://x/c.json" """),
          "claims")).getMessage should include("http:// or https://")
    }

    "reject a token URL that is plain http to another host" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "https://x.example.com/c.json"
                 auth { type = oauth2-client-credentials, token-url = "http://t.example.com/t", client-id = a, client-secret = b } }"""),
          "claims")).getMessage should include("auth.token-url must be an https:// URL")
    }

    "reject a plain http card URL to another host when credentials are configured" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "http://x.example.com/c.json"
                 auth { type = header, header-name = X, header-value = v } }"""),
          "claims")).getMessage should include("card-url must be an https:// URL")
    }

    "accept a plain http card URL to another host without credentials" in {
      A2aRemoteAgentSettings
        .resolve(a2a("""remote-agents.claims.card-url = "http://x.example.com/c.json" """), "claims")
        .cardUrl shouldBe "http://x.example.com/c.json"
    }

    "send credentials over https, or over http to localhost only" in {
      import A2aRemoteAgentSettings.safeForCredentials
      safeForCredentials("https://x.example.com/t") shouldBe true
      safeForCredentials("http://localhost:9000/t") shouldBe true
      safeForCredentials("http://127.0.0.1:9000/t") shouldBe true
      safeForCredentials("http://[::1]:9000/t") shouldBe true
      safeForCredentials("http://x.example.com/t") shouldBe false
      safeForCredentials("http://127.0.0.1.example.com/t") shouldBe false
      safeForCredentials("http://localhost.example.com/t") shouldBe false
      safeForCredentials("ftp://localhost/t") shouldBe false
    }

    "reject header authentication without a value" in {
      val e = intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "https://x.example.com/c.json", auth { type = header, header-name = X } }"""),
          "claims"))
      e.getMessage should include("auth.header-value must be set")
    }

    "reject OAuth authentication without a client secret, and not echo the settings" in {
      val e = intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "https://x.example.com/c.json"
                 auth { type = oauth2-client-credentials, token-url = "https://t.example.com/t", client-id = abc } }"""),
          "claims"))
      e.getMessage should include("auth.client-secret must be set")
    }

    "reject an unknown authentication type" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "https://x.example.com/c.json", auth.type = basic }"""),
          "claims")).getMessage should include("must be one of none, header, oauth2-client-credentials")
    }

    "reject a client certificate without a key" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "https://x.example.com/c.json", client-certificate = abc }"""),
          "claims")).getMessage should include("must be set together")
    }

    "keep secrets out of toString" in {
      val settings = A2aRemoteAgentSettings.resolve(
        a2a("""remote-agents.claims {
          card-url = "https://x.example.com/c.json"
          auth { type = oauth2-client-credentials, token-url = "https://t.example.com/t", client-id = abc, client-secret = topsecret }
          client-certificate = c, client-key = privatekey
        }"""),
        "claims")
      (settings.toString should not).include("topsecret")
      (settings.toString should not).include("privatekey")
    }

    "read PEM text as it is and a file by its path" in {
      A2aRemoteAgentSettings.pemText(
        "-----BEGIN CERTIFICATE-----\nabc",
        "ca") shouldBe "-----BEGIN CERTIFICATE-----\nabc"
      val file = Files.createTempFile("a2a-pem", ".pem")
      try {
        Files.writeString(file, "from file")
        A2aRemoteAgentSettings.pemText(file.toString, "ca") shouldBe "from file"
      } finally Files.delete(file)
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.pemText("/no/such/file.pem", "ca")).getMessage should include("PEM text or the path")
    }

    "not print a value that is neither PEM text nor a readable file" in {
      val headerlessKey = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7"
      val e = intercept[IllegalArgumentException](A2aRemoteAgentSettings.pemText(headerlessKey, "x.client-key"))
      e.getMessage should include("x.client-key")
      (e.getMessage should not).include(headerlessKey)
      e.getCause shouldBe null
    }
  }
}
