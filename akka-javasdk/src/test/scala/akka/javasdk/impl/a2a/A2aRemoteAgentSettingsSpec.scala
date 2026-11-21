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
        A2aRemoteTarget.Named("claims"))
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
        A2aRemoteTarget.Named("claims"))
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
        A2aRemoteTarget.Named("claims"))
      settings.auth shouldBe A2aAuth.Header("X-Api-Key", "abc")
    }

    "treat an empty value, which an unset environment variable can leave, as absent" in {
      val settings = A2aRemoteAgentSettings.resolve(
        a2a("""remote-agents.claims { card-url = "https://x.example.com/card.json", ca-bundle = "" }"""),
        A2aRemoteTarget.Named("claims"))
      settings.caBundle shouldBe None
    }

    "accept an agent by card URL, with the defaults and no credentials" in {
      val settings = A2aRemoteAgentSettings.resolve(
        a2a(""),
        A2aRemoteTarget.CardUrl("http://localhost:8080/.well-known/agent-card.json"))
      settings.cardUrl shouldBe "http://localhost:8080/.well-known/agent-card.json"
      settings.auth shouldBe A2aAuth.NoAuth
      settings.timeout shouldBe 60.seconds
    }

    "reject an unknown name and list the configured names" in {
      val e = intercept[IllegalArgumentException](
        A2aRemoteAgentSettings
          .resolve(
            a2a("""remote-agents.claims.card-url = "https://x.example.com/c.json" """),
            A2aRemoteTarget.Named("other")))
      e.getMessage should include("[other]")
      e.getMessage should include("claims")
    }

    "reject a missing card URL" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("remote-agents.claims.timeout = 1s"),
          A2aRemoteTarget.Named("claims"))).getMessage should include("remote-agents.claims.card-url must be set")
    }

    "reject a card URL that is not http or https" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims.card-url = "ftp://x/c.json" """),
          A2aRemoteTarget.Named("claims"))).getMessage should include("http:// or https://")
    }

    "reject a token URL that is plain http to another host" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "https://x.example.com/c.json"
                 auth { type = oauth2-client-credentials, token-url = "http://t.example.com/t", client-id = a, client-secret = b } }"""),
          A2aRemoteTarget.Named("claims"))).getMessage should include("auth.token-url must be an https:// URL")
    }

    "reject a plain http card URL to another host when credentials are configured" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "http://x.example.com/c.json"
                 auth { type = header, header-name = X, header-value = v } }"""),
          A2aRemoteTarget.Named("claims"))).getMessage should include("card-url must be an https:// URL")
    }

    "accept a plain http card URL to another host without credentials" in {
      A2aRemoteAgentSettings
        .resolve(
          a2a("""remote-agents.claims.card-url = "http://x.example.com/c.json" """),
          A2aRemoteTarget.Named("claims"))
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

    "reject credentials in the defaults" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.validateDefaults(
          a2a("""defaults.auth { type = header, header-name = X, header-value = v }"""))).getMessage should include(
        "akka.javasdk.a2a.defaults.auth.type must be none")
      A2aRemoteAgentSettings.validateDefaults(a2a(""))
    }

    "give an agent reached by URL no credentials and no custom TLS" in {
      val settings = A2aRemoteAgentSettings.resolve(
        a2a("""defaults { auth { type = header, header-name = X, header-value = v }, ca-bundle = ca }"""),
        A2aRemoteTarget.CardUrl("https://x.example.com/c.json"))
      settings.auth shouldBe A2aAuth.NoAuth
      settings.caBundle shouldBe None
    }

    "warn about TLS settings of an agent with an http card URL" in {
      val warnings = A2aRemoteAgentSettings.unusedTlsWarnings(a2a("""remote-agents {
          plain { card-url = "http://a.example.com/c.json", ca-bundle = ca }
          mtls { card-url = "http://b.example.com/c.json", client-certificate = c, client-key = k }
          tls { card-url = "https://c.example.com/c.json", ca-bundle = ca }
          none { card-url = "http://d.example.com/c.json" }
        }"""))
      warnings.size shouldBe 2
      warnings.head should include("[mtls]")
      warnings(1) should include("[plain]")
      warnings.head should include("not used")
    }

    "reject header authentication without a value" in {
      val e = intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "https://x.example.com/c.json", auth { type = header, header-name = X } }"""),
          A2aRemoteTarget.Named("claims")))
      e.getMessage should include("auth.header-value must be set")
    }

    "reject OAuth authentication without a client secret, and not echo the settings" in {
      val e = intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "https://x.example.com/c.json"
                 auth { type = oauth2-client-credentials, token-url = "https://t.example.com/t", client-id = abc } }"""),
          A2aRemoteTarget.Named("claims")))
      e.getMessage should include("auth.client-secret must be set")
    }

    "use client_secret_basic unless client_secret_post is configured" in {
      def method(extra: String) =
        A2aRemoteAgentSettings
          .resolve(
            a2a(s"""remote-agents.claims { card-url = "https://x.example.com/c.json"
                 auth { type = oauth2-client-credentials, token-url = "https://t.example.com/t", client-id = a, client-secret = b $extra } }"""),
            A2aRemoteTarget.Named("claims"))
          .auth
          .asInstanceOf[A2aAuth.OAuth2ClientCredentials]
          .authMethod
      method("") shouldBe A2aAuth.ClientSecretBasic
      method(", token-endpoint-auth-method = client_secret_post") shouldBe A2aAuth.ClientSecretPost
      intercept[IllegalArgumentException](method(", token-endpoint-auth-method = private_key_jwt")).getMessage should
      include("must be client_secret_basic or client_secret_post")
    }

    "reject an unknown authentication type" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "https://x.example.com/c.json", auth.type = basic }"""),
          A2aRemoteTarget.Named("claims"))).getMessage should include(
        "must be one of none, header, oauth2-client-credentials")
    }

    "reject a client certificate without a key" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "https://x.example.com/c.json", client-certificate = abc }"""),
          A2aRemoteTarget.Named("claims"))).getMessage should include("must be set together")
    }

    "keep secrets out of toString" in {
      val settings = A2aRemoteAgentSettings.resolve(
        a2a("""remote-agents.claims {
          card-url = "https://x.example.com/c.json"
          auth { type = oauth2-client-credentials, token-url = "https://t.example.com/t", client-id = abc, client-secret = topsecret }
          client-certificate = c, client-key = privatekey
        }"""),
        A2aRemoteTarget.Named("claims"))
      (settings.toString should not).include("topsecret")
      (settings.toString should not).include("privatekey")
    }

    "read PEM text from a setting, or from the file that its -file setting names" in {
      val file = Files.createTempFile("a2a-pem", ".pem")
      try {
        Files.writeString(file, "from file")
        val settings = A2aRemoteAgentSettings.resolve(
          a2a(s"""remote-agents.claims {
            card-url = "https://x.example.com/c.json"
            ca-bundle = "-----BEGIN CERTIFICATE----- abc"
            client-certificate-file = "${file.toString.replace("\\", "/")}"
            client-key = "key"
          }"""),
          A2aRemoteTarget.Named("claims"))
        settings.caBundle shouldBe Some("-----BEGIN CERTIFICATE----- abc")
        settings.clientCertificate shouldBe Some("from file")
      } finally Files.delete(file)
    }

    "refuse both the text and the -file form of a PEM setting" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims {
            card-url = "https://x.example.com/c.json", ca-bundle = "pem", ca-bundle-file = "/x.pem" }"""),
          A2aRemoteTarget.Named("claims"))).getMessage should include("Set only one of")
    }

    "not print the value of a -file setting that names no readable file" in {
      val headerlessKey = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7"
      val e = intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a(s"""remote-agents.claims {
            card-url = "https://x.example.com/c.json", client-certificate = c, client-key-file = "$headerlessKey" }"""),
          A2aRemoteTarget.Named("claims")))
      e.getMessage should include("client-key-file")
      (e.getMessage should not).include(headerlessKey)
      e.getCause shouldBe null
    }

    "require a key or a key set URL for a signed card" in {
      intercept[IllegalArgumentException](
        A2aRemoteAgentSettings.resolve(
          a2a("""remote-agents.claims { card-url = "https://x.example.com/c.json", require-signed-card = true }"""),
          A2aRemoteTarget.Named("claims"))).getMessage should include("card-signing-key")
      val settings = A2aRemoteAgentSettings.resolve(
        a2a("""remote-agents.claims {
          card-url = "https://x.example.com/c.json"
          require-signed-card = true
          card-jwks-url = "https://x.example.com/jwks.json" }"""),
        A2aRemoteTarget.Named("claims"))
      settings.cardVerification shouldBe Some(A2aCardVerification(None, Some("https://x.example.com/jwks.json")))
    }

    "apply require-signed-card of the defaults to agents given by URL or service, with the key of the defaults" in {
      val withKey =
        a2a("""defaults { require-signed-card = true, card-jwks-url = "https://keys.example.com/jwks.json" }""")
      A2aRemoteAgentSettings
        .resolve(withKey, A2aRemoteTarget.CardUrl("https://x.example.com/c.json"))
        .cardVerification shouldBe Some(A2aCardVerification(None, Some("https://keys.example.com/jwks.json")))
      A2aRemoteAgentSettings
        .resolve(withKey, A2aRemoteTarget.Service("claims-service"))
        .cardVerification shouldBe Some(A2aCardVerification(None, Some("https://keys.example.com/jwks.json")))
    }

    "refuse every card of an agent given by URL when the defaults require a signed card and trust no key" in {
      A2aRemoteAgentSettings
        .resolve(a2a("defaults.require-signed-card = true"), A2aRemoteTarget.CardUrl("https://x.example.com/c.json"))
        .cardVerification shouldBe Some(A2aCardVerification(None, None))
      A2aRemoteAgentSettings
        .resolve(a2a(""), A2aRemoteTarget.CardUrl("https://x.example.com/c.json"))
        .cardVerification shouldBe None
    }

    "give an agent reached as a service the defaults and no credentials" in {
      val settings =
        A2aRemoteAgentSettings.resolve(a2a("""defaults { timeout = 7s }"""), A2aRemoteTarget.Service("claims-service"))
      settings.cardUrl shouldBe "http://claims-service/.well-known/agent-card.json"
      settings.timeout shouldBe 7.seconds
      settings.auth shouldBe A2aAuth.NoAuth
    }
  }
}
