/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.ConfiguredSanitizer.UseFor
import akka.runtime.sdk.spi.SpiDataSanitizer
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SanitizationSpec extends AnyWordSpec with Matchers with OptionValues {

  private def parse(entries: String): Seq[ConfiguredSanitizer] =
    Sanitization.configuredSanitizers(ConfigFactory.load(ConfigFactory.parseString(s"""
      akka.javasdk.sanitization.sanitizers {
        $entries
      }
      """)))

  private def parseOne(entry: String): ConfiguredSanitizer = {
    val all = parse(entry)
    all should have size 1
    all.head
  }

  private def failure(entries: String): String =
    intercept[IllegalArgumentException](parse(entries)).getMessage

  "Loading the sanitizer settings from config" should {

    "load a pattern sanitizer" in {
      val sanitizer = parseOne("""
        "internal-account-ids" { pattern = "(?i)\\bACC-[0-9]{8}\\b" }
        """)

      sanitizer.name shouldEqual "internal-account-ids"
      sanitizer.kind match {
        case SanitizerKind.Pattern(regex) => regex.regex shouldEqual """(?i)\bACC-[0-9]{8}\b"""
        case other                        => fail(s"unexpected kind [$other]")
      }
    }

    "load a predefined sanitizer" in {
      val sanitizer = parseOne("""
        "credit-card" { predefined = CREDIT_CARD }
        """)

      sanitizer.kind shouldEqual SanitizerKind.Predefined("CREDIT_CARD")
    }

    "load an implementation sanitizer with its own settings" in {
      val sanitizer = parseOne("""
        "pii-detector" {
          class = "com.example.PiiSanitizer"
          use-for = ["model-input"]
          agent-roles = ["customer-facing"]
          threshold = 0.8
        }
        """)

      sanitizer.kind shouldEqual SanitizerKind.Implementation("com.example.PiiSanitizer")
      sanitizer.useFor shouldEqual Set(UseFor.ModelInput)
      sanitizer.agentRoles shouldEqual Set("customer-facing")
      sanitizer.config.getDouble("threshold") shouldEqual 0.8
    }

    "drop a disabled sanitizer without looking at the rest of it" in {
      parse("""
        "off" {
          enabled = false
          pattern = "([unclosed"
          class = "com.example.AlsoWrong"
        }
        """) shouldBe empty
    }

    "fail when no detector is defined" in {
      failure("""
        "nothing" { use-for = ["logs"] }
        """) should include(
        "Sanitizer [nothing] must define exactly one of [pattern, predefined, class], but defines []")
    }

    "fail when more than one detector is defined" in {
      failure("""
        "both" {
          pattern = "a"
          class = "com.example.PiiSanitizer"
        }
        """) should include(
        "Sanitizer [both] must define exactly one of [pattern, predefined, class], but defines [pattern, class]")
    }

    "fail for an unknown predefined group" in {
      failure("""
        "passport" { predefined = PASSPORT }
        """) should include(
        "Sanitizer [passport] has unknown [predefined = PASSPORT], known groups are " +
        "[CREDIT_CARD, IBAN, PHONE, EMAIL, IP_ADDRESS]")
    }

    "fail for a pattern that does not compile" in {
      failure("""
        "broken" { pattern = "([unclosed" }
        """) should include("Sanitizer [broken] has an invalid [pattern = ([unclosed]")
    }

    "fail for an unknown use-for" in {
      failure("""
        "wrong-point" {
          pattern = "a"
          use-for = ["model-output"]
        }
        """) should include(
        "Sanitizer [wrong-point] has unknown use-for [model-output], valid values are " +
        "[client, logs, model-input, tool-result] or [*]")
    }

    "load the same predefined group in more than one entry" in {
      parse("""
        "cc-logs" { predefined = CREDIT_CARD, use-for = ["logs"] }
        "cc-model" { predefined = CREDIT_CARD, use-for = ["model-input"], agents = ["billing-agent"] }
        """).map(s => s.name -> s.kind).toMap shouldEqual Map(
        "cc-logs" -> SanitizerKind.Predefined("CREDIT_CARD"),
        "cc-model" -> SanitizerKind.Predefined("CREDIT_CARD"))
    }
  }

  "The use-for of a sanitizer" should {

    "default to every application point" in {
      parseOne("""
        "everywhere" { pattern = "a" }
        """).useFor shouldEqual Set(UseFor.ModelInput, UseFor.ToolResult, UseFor.Logs)
    }

    "expand a wildcard to every application point" in {
      parseOne("""
        "everywhere" { pattern = "a", use-for = ["*"] }
        """).useFor shouldEqual Set(UseFor.ModelInput, UseFor.ToolResult, UseFor.Logs)
    }

    "hold what it names" in {
      parseOne("""
        "named" { pattern = "a", use-for = ["model-input", "tool-result"] }
        """).useFor shouldEqual Set(UseFor.ModelInput, UseFor.ToolResult)
    }

    "leave out log messages for an agent scoped sanitizer that names no point" in {
      parseOne("""
        "scoped" { pattern = "a", agents = ["some-agent"] }
        """).useFor shouldEqual Set(UseFor.ModelInput, UseFor.ToolResult)
    }

    "include log messages for an implementation that names no point" in {
      parseOne("""
        "implemented" { class = "com.example.PiiSanitizer" }
        """).useFor shouldEqual Set(UseFor.ModelInput, UseFor.ToolResult, UseFor.Logs)
    }

    "hold logs for an implementation that names it" in {
      parseOne("""
        "implemented-logs" {
          class = "com.example.PiiSanitizer"
          use-for = ["logs"]
        }
        """).useFor shouldEqual Set(UseFor.Logs)
    }

    "hold client for a sanitizer that masks nothing on its own" in {
      parseOne("""
        "by-name-only" { pattern = "a", use-for = ["client"] }
        """).useFor shouldEqual Set(UseFor.Client)
    }

    "fail when client is combined with agents" in {
      failure("""
        "agent-client" {
          pattern = "a"
          agents = ["some-agent"]
          use-for = ["client"]
        }
        """) should include(
        "Sanitizer [agent-client] cannot combine [agents] or [agent-roles] with the [client] application point")
    }

    "fail when logs is combined with agents" in {
      failure("""
        "agent-logs" {
          pattern = "a"
          agents = ["some-agent"]
          use-for = ["logs"]
        }
        """) should include(
        "Sanitizer [agent-logs] cannot combine [agents] or [agent-roles] with the [logs] application point")
    }

    "fail when logs is combined with agent-roles" in {
      failure("""
        "role-logs" {
          pattern = "a"
          agent-roles = ["customer-facing"]
          use-for = ["logs"]
        }
        """) should include(
        "Sanitizer [role-logs] cannot combine [agents] or [agent-roles] with the [logs] application point")
    }
  }

  "The agents of a sanitizer" should {

    val sanitizers = parse("""
      "unscoped" { pattern = "a" }
      "by-id" { pattern = "a", agents = ["billing-agent"] }
      "by-role" { pattern = "a", agent-roles = ["customer-facing"] }
      "all-agents" { pattern = "a", agents = ["*"] }
      "all-roles" { pattern = "a", agent-roles = ["*"] }
      "either" { pattern = "a", agents = ["billing-agent"], agent-roles = ["internal"] }
      """).map(s => s.name -> s).toMap

    "apply to every agent when neither agents nor agent-roles is defined" in {
      sanitizers("unscoped").appliesTo("billing-agent", Some("customer-facing")) shouldBe true
      sanitizers("unscoped").appliesTo("other-agent", None) shouldBe true
    }

    "apply to the named component ids only" in {
      sanitizers("by-id").appliesTo("billing-agent", None) shouldBe true
      sanitizers("by-id").appliesTo("other-agent", None) shouldBe false
    }

    "apply to the named roles only" in {
      sanitizers("by-role").appliesTo("other-agent", Some("customer-facing")) shouldBe true
      sanitizers("by-role").appliesTo("other-agent", Some("internal")) shouldBe false
      sanitizers("by-role").appliesTo("other-agent", None) shouldBe false
    }

    "apply to every agent for an agents wildcard" in {
      sanitizers("all-agents").appliesTo("any-agent", None) shouldBe true
    }

    "apply to every agent that has a role for an agent-roles wildcard" in {
      sanitizers("all-roles").appliesTo("any-agent", Some("any-role")) shouldBe true
      sanitizers("all-roles").appliesTo("any-agent", None) shouldBe false
    }

    "apply when either agents or agent-roles matches" in {
      sanitizers("either").appliesTo("billing-agent", None) shouldBe true
      sanitizers("either").appliesTo("other-agent", Some("internal")) shouldBe true
      sanitizers("either").appliesTo("other-agent", Some("customer-facing")) shouldBe false
    }
  }

  "The settings handed over before startup" should {

    "carry the pattern and predefined entries with their application points" in {
      val settings = Sanitization.loadSettings(ConfigFactory.load(ConfigFactory.parseString("""
        akka.javasdk.sanitization.sanitizers {
          "warm-colors" { pattern = "(?i)(red|orange|yellow)", use-for = ["logs"] }
          "account-ids" { pattern = "ACC-[0-9]+", use-for = ["client"] }
          "credit-card" { predefined = CREDIT_CARD }
          "pii-detector" { class = "com.example.PiiSanitizer" }
          "off" { pattern = "a", enabled = false }
        }
        """)))

      // The runtime reads no application point as every point. Log messages are not a point of an agent, so an
      // entry that only masks them says here that it masks nothing on its own. It masks log messages as a log
      // sanitizer of the setup carrier.
      settings.sanitizers.map(entry => entry.name -> (entry.getClass, entry.useFor)).toMap shouldEqual Map(
        "warm-colors" -> (classOf[SpiDataSanitizer.Regex], Set(SpiDataSanitizer.UseFor.Client)),
        "account-ids" -> (classOf[SpiDataSanitizer.Regex], Set(SpiDataSanitizer.UseFor.Client)),
        "credit-card" -> (classOf[SpiDataSanitizer.Predefined],
        Set(SpiDataSanitizer.UseFor.ModelInput, SpiDataSanitizer.UseFor.ToolResult)))

      settings.sanitizers.map(_.enabledForComponents).toSet shouldEqual Set(Set.empty)
      settings.sanitizers.collectFirst { case predefined: SpiDataSanitizer.Predefined =>
        predefined.group
      }.value shouldEqual "CREDIT_CARD"
      settings.sanitizers.collectFirst {
        case regex: SpiDataSanitizer.Regex if regex.name == "warm-colors" => regex.pattern.regex
      }.value shouldEqual "(?i)(red|orange|yellow)"
    }
  }

  "The settings the runtime reads at startup" should {

    "fail for an entry the service cannot start with" in {
      val exc = intercept[IllegalArgumentException](
        SdkRunner.extractSpiSettings(
          ConfigFactory
            .parseString("""
            akka.javasdk.sanitization.sanitizers {
              "broken" { pattern = "([unclosed" }
            }
            """)
            .withFallback(ConfigFactory.load())))

      exc.getMessage should include("Sanitizer [broken] has an invalid [pattern = ([unclosed]")
    }

    "fail for an entry that combines an agent scope with the logs application point" in {
      val exc = intercept[IllegalArgumentException](
        SdkRunner.extractSpiSettings(
          ConfigFactory
            .parseString("""
            akka.javasdk.sanitization.sanitizers {
              "agent-logs" { pattern = "a", agents = ["some-agent"], use-for = ["logs"] }
            }
            """)
            .withFallback(ConfigFactory.load())))

      exc.getMessage should include(
        "Sanitizer [agent-logs] cannot combine [agents] or [agent-roles] with the [logs] application point")
    }
  }

  "A removed sanitization key" should {

    "fail at startup" in {
      val exc = intercept[IllegalArgumentException](
        SdkRunner.extractSpiSettings(
          ConfigFactory
            .parseString("""akka.javasdk.sanitization.predefined-sanitizers = ["CREDIT_CARD"]""")
            .withFallback(ConfigFactory.load())))

      exc.getMessage should include("[akka.javasdk.sanitization.predefined-sanitizers] is not used")
    }

    "fail with the form that replaced it" in {
      val exc = intercept[IllegalArgumentException](
        Sanitization.configuredSanitizers(ConfigFactory.load(ConfigFactory.parseString("""
          akka.javasdk.sanitization.regex-sanitizers {
            "warm-colors" = { pattern = "(?i)(red|orange|yellow)" }
          }
          """))))

      exc.getMessage shouldEqual
      "Configuration [akka.javasdk.sanitization.regex-sanitizers] is not used. Configure each sanitizer as a " +
      "named entry of [akka.javasdk.sanitization.sanitizers] with a [pattern] key."
    }

    "fail for the predefined list" in {
      val exc = intercept[IllegalArgumentException](
        Sanitization.configuredSanitizers(ConfigFactory.load(ConfigFactory.parseString("""
          akka.javasdk.sanitization.predefined-sanitizers = ["CREDIT_CARD"]
          """))))

      exc.getMessage should include("[akka.javasdk.sanitization.predefined-sanitizers] is not used")
      exc.getMessage should include("[predefined] key")
    }
  }
}
