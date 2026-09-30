/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.ConfiguredSanitizer.ApplyAt
import akka.runtime.sdk.spi.SpiDataSanitizer
import akka.runtime.sdk.spi.SpiLogSanitizer
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SanitizationSpec extends AnyWordSpec with Matchers with OptionValues {

  // Stands in for loading the class of an entry: the class name says which sanitizer interfaces it implements.
  private def interfacesOf(name: String, className: String): SanitizerInterfaces = className match {
    case "com.example.LogPiiSanitizer"  => SanitizerInterfaces(text = false, log = true)
    case "com.example.BothPiiSanitizer" => SanitizerInterfaces.Both
    case _                              => SanitizerInterfaces(text = true, log = false)
  }

  private def parse(entries: String): Seq[ConfiguredSanitizer] =
    Sanitization.configuredSanitizers(
      ConfigFactory.load(ConfigFactory.parseString(s"""
      akka.javasdk.sanitization.sanitizers {
        $entries
      }
      """)),
      interfacesOf)

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
          apply-at = ["model-call"]
          agent-roles = ["customer-facing"]
          threshold = 0.8
        }
        """)

      sanitizer.kind shouldEqual SanitizerKind.Implementation("com.example.PiiSanitizer")
      sanitizer.applyAt shouldEqual Set(ApplyAt.ModelCall)
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
        "nothing" { apply-at = ["logs"] }
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

    "fail for an unknown apply-at" in {
      failure("""
        "wrong-point" {
          pattern = "a"
          apply-at = ["model-output"]
        }
        """) should include(
        "Sanitizer [wrong-point] has unknown apply-at [model-output], valid values are " +
        "[client, logs, model-call, tool-result] or [*]")
    }

    "load the same predefined group in more than one entry" in {
      parse("""
        "cc-logs" { predefined = CREDIT_CARD, apply-at = ["logs"] }
        "cc-model" { predefined = CREDIT_CARD, apply-at = ["model-call"], agents = ["billing-agent"] }
        """).map(s => s.name -> s.kind).toMap shouldEqual Map(
        "cc-logs" -> SanitizerKind.Predefined("CREDIT_CARD"),
        "cc-model" -> SanitizerKind.Predefined("CREDIT_CARD"))
    }

    "read the control id of each kind of entry, and keep it in the config" in {
      val sanitizers = parse("""
        "pattern-id" { pattern = "a", control-id = "AI-SAN-01" }
        "predefined-id" { predefined = CREDIT_CARD, control-id = "AI-SAN-02" }
        "class-id" { class = "com.example.PiiSanitizer", control-id = "AI-SAN-03" }
        "no-id" { pattern = "a" }
        """)

      sanitizers.map(s => s.name -> s.controlId).toMap shouldEqual Map(
        "pattern-id" -> Some("AI-SAN-01"),
        "predefined-id" -> Some("AI-SAN-02"),
        "class-id" -> Some("AI-SAN-03"),
        "no-id" -> None)
      sanitizers.find(_.name == "class-id").value.config.getString("control-id") shouldEqual "AI-SAN-03"
    }

    Seq("\"\"", "\" \"").foreach { blank =>
      s"fail for control-id = $blank" in {
        failure(s"""
          "blank-id" { pattern = "a", control-id = $blank }
          """) shouldEqual "Sanitizer [blank-id] must define a non blank [control-id]"
      }
    }

    "fail for a control id that is not a string" in {
      failure("""
        "number-id" { predefined = IBAN, control-id = 42 }
        """) shouldEqual "Sanitizer [number-id] must define [control-id] as a string, but defines [NUMBER]"
    }
  }

  "The apply-at of a sanitizer" should {

    "default to every application point" in {
      parseOne("""
        "everywhere" { pattern = "a" }
        """).applyAt shouldEqual Set(ApplyAt.ModelCall, ApplyAt.ToolResult, ApplyAt.Logs)
    }

    "expand a wildcard to every application point" in {
      parseOne("""
        "everywhere" { pattern = "a", apply-at = ["*"] }
        """).applyAt shouldEqual Set(ApplyAt.ModelCall, ApplyAt.ToolResult, ApplyAt.Logs)
    }

    "hold what it names" in {
      parseOne("""
        "named" { pattern = "a", apply-at = ["model-call", "tool-result"] }
        """).applyAt shouldEqual Set(ApplyAt.ModelCall, ApplyAt.ToolResult)
    }

    "leave out log messages for an agent scoped sanitizer that names no point" in {
      parseOne("""
        "scoped" { pattern = "a", agents = ["some-agent"] }
        """).applyAt shouldEqual Set(ApplyAt.ModelCall, ApplyAt.ToolResult)
    }

    "default to the points of an agent for a TextSanitizer class" in {
      parseOne("""
        "implemented" { class = "com.example.PiiSanitizer" }
        """).applyAt shouldEqual Set(ApplyAt.ModelCall, ApplyAt.ToolResult)
      parseOne("""
        "implemented" { class = "com.example.PiiSanitizer", apply-at = ["*"] }
        """).applyAt shouldEqual Set(ApplyAt.ModelCall, ApplyAt.ToolResult)
    }

    "default to log messages for a LogSanitizer class" in {
      parseOne("""
        "implemented" { class = "com.example.LogPiiSanitizer" }
        """).applyAt shouldEqual Set(ApplyAt.Logs)
    }

    "default to every point for a class that implements both" in {
      parseOne("""
        "implemented" { class = "com.example.BothPiiSanitizer" }
        """).applyAt shouldEqual Set(ApplyAt.ModelCall, ApplyAt.ToolResult, ApplyAt.Logs)
    }

    "leave out log messages for a class that implements both and is scoped to an agent" in {
      parseOne("""
        "implemented" { class = "com.example.BothPiiSanitizer", agents = ["some-agent"] }
        """).applyAt shouldEqual Set(ApplyAt.ModelCall, ApplyAt.ToolResult)
    }

    "hold what a class names within what its interfaces allow" in {
      parseOne("""
        "implemented" { class = "com.example.BothPiiSanitizer", apply-at = ["model-call", "logs"] }
        """).applyAt shouldEqual Set(ApplyAt.ModelCall, ApplyAt.Logs)
    }

    "add the points named beside the wildcard" in {
      parseOne("""
        "everywhere" { pattern = "a", apply-at = ["*", "client"] }
        """).applyAt shouldEqual Set(ApplyAt.ModelCall, ApplyAt.ToolResult, ApplyAt.Logs, ApplyAt.Client)
    }

    "fail when a TextSanitizer class names logs" in {
      failure("""
        "text-in-logs" { class = "com.example.PiiSanitizer", apply-at = ["logs"] }
        """) should include(
        "Sanitizer [text-in-logs] cannot mask at [logs], because [com.example.PiiSanitizer] does not implement " +
        "[akka.javasdk.LogSanitizer].")
    }

    "fail when a LogSanitizer class names a point of an agent" in {
      failure("""
        "log-in-model" { class = "com.example.LogPiiSanitizer", apply-at = ["model-call"] }
        """) should include(
        "Sanitizer [log-in-model] cannot mask at [model-call], because [com.example.LogPiiSanitizer] does not " +
        "implement [akka.javasdk.TextSanitizer].")
    }

    "fail when a LogSanitizer class is scoped to an agent" in {
      failure("""
        "log-scoped" { class = "com.example.LogPiiSanitizer", agents = ["some-agent"] }
        """) should include(
        "Sanitizer [log-scoped] cannot define [agents] or [agent-roles], because [com.example.LogPiiSanitizer] " +
        "implements only [akka.javasdk.LogSanitizer]")
    }

    "hold client for a sanitizer that masks nothing on its own" in {
      parseOne("""
        "by-name-only" { pattern = "a", apply-at = ["client"] }
        """).applyAt shouldEqual Set(ApplyAt.Client)
    }

    "fail when client is combined with agents" in {
      failure("""
        "agent-client" {
          pattern = "a"
          agents = ["some-agent"]
          apply-at = ["client"]
        }
        """) should include(
        "Sanitizer [agent-client] cannot combine [agents] or [agent-roles] with the [client] application point")
    }

    "fail when logs is combined with agents" in {
      failure("""
        "agent-logs" {
          pattern = "a"
          agents = ["some-agent"]
          apply-at = ["logs"]
        }
        """) should include(
        "Sanitizer [agent-logs] cannot combine [agents] or [agent-roles] with the [logs] application point")
    }

    "fail when logs is combined with agent-roles" in {
      failure("""
        "role-logs" {
          pattern = "a"
          agent-roles = ["customer-facing"]
          apply-at = ["logs"]
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
          "warm-colors" { pattern = "(?i)(red|orange|yellow)", apply-at = ["logs"] }
          "account-ids" { pattern = "ACC-[0-9]+", apply-at = ["client"] }
          "credit-card" { predefined = CREDIT_CARD }
          "pii-detector" { class = "com.example.PiiSanitizer" }
          "off" { pattern = "a", enabled = false }
        }
        """)))

      // The runtime reads no application point as every point. Log messages are not a point of an agent, so an
      // entry that only masks them says here that it masks nothing on its own. It masks log messages as a log
      // sanitizer of the setup carrier.
      settings.sanitizers.map(entry => entry.name -> (entry.getClass, entry.applyAt)).toMap shouldEqual Map(
        "warm-colors" -> (classOf[SpiDataSanitizer.Regex], Set(SpiDataSanitizer.ApplyAt.Client)),
        "account-ids" -> (classOf[SpiDataSanitizer.Regex], Set(SpiDataSanitizer.ApplyAt.Client)),
        "credit-card" -> (classOf[SpiDataSanitizer.Predefined],
        Set(SpiDataSanitizer.ApplyAt.ModelCall, SpiDataSanitizer.ApplyAt.ToolResult)))

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

    "carry the pattern and predefined entries that mask log messages as log sanitizers, and no class" in {
      val settings = SdkRunner
        .extractSpiSettings(
          ConfigFactory
            .parseString("""
            akka.javasdk.sanitization.sanitizers {
              "warm-colors" { pattern = "(?i)(red|orange|yellow)", apply-at = ["logs"] }
              "credit-card" { predefined = CREDIT_CARD }
              "model-only" { pattern = "a", apply-at = ["model-call"] }
              "account-ids" { pattern = "ACC-[0-9]+", apply-at = ["client"] }
              "pii-in-logs" { class = "com.example.PiiSanitizer", apply-at = ["logs"] }
            }
            """)
            .withFallback(ConfigFactory.load()))
        .sanitizerSettings
        .value

      settings.logSanitizers.map {
        case regex: SpiLogSanitizer.Regex           => regex.name -> regex.pattern.regex
        case predefined: SpiLogSanitizer.Predefined => predefined.name -> predefined.group
        case custom: SpiLogSanitizer.Custom         => custom.name -> custom.implementationClass
      }.toMap shouldEqual Map("warm-colors" -> "(?i)(red|orange|yellow)", "credit-card" -> "CREDIT_CARD")
    }

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
              "agent-logs" { pattern = "a", agents = ["some-agent"], apply-at = ["logs"] }
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
        Sanitization.configuredSanitizers(
          ConfigFactory.load(ConfigFactory.parseString("""
          akka.javasdk.sanitization.regex-sanitizers {
            "warm-colors" = { pattern = "(?i)(red|orange|yellow)" }
          }
          """)),
          (_, _) => SanitizerInterfaces.Both))

      exc.getMessage shouldEqual
      "Configuration [akka.javasdk.sanitization.regex-sanitizers] is not used. Configure each sanitizer as a " +
      "named entry of [akka.javasdk.sanitization.sanitizers] with a [pattern] key."
    }

    "fail for the predefined list" in {
      val exc = intercept[IllegalArgumentException](
        Sanitization.configuredSanitizers(
          ConfigFactory.load(ConfigFactory.parseString("""
          akka.javasdk.sanitization.predefined-sanitizers = ["CREDIT_CARD"]
          """)),
          (_, _) => SanitizerInterfaces.Both))

      exc.getMessage should include("[akka.javasdk.sanitization.predefined-sanitizers] is not used")
      exc.getMessage should include("[predefined] key")
    }
  }
}
