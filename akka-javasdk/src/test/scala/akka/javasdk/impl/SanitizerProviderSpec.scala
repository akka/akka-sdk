/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.SanitizerContext
import akka.javasdk.TextSanitizer
import akka.javasdk.agent.Classification
import akka.javasdk.agent.ClassifierClient
import akka.runtime.sdk.spi.SpiDataSanitizer
import akka.runtime.sdk.spi.SpiLogSanitizer
import akka.runtime.sdk.spi.SpiSanitizer
import akka.runtime.sdk.spi.SpiSanitizerClient
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object SanitizerProviderSpec {

  private val config = ConfigFactory
    .parseString(s"""
    akka.javasdk.sanitization.sanitizers {
      "with-context" {
        class = "akka.javasdk.impl.SanitizerProviderSpec$$ContextSanitizer"
        replacement = "[redacted]"
      }
      "no-context" {
        class = "akka.javasdk.impl.SanitizerProviderSpec$$NoContextSanitizer"
      }
      "classifier-backed" {
        class = "akka.javasdk.impl.SanitizerProviderSpec$$ClassifierBackedSanitizer"
      }
      "counted" {
        class = "akka.javasdk.impl.SanitizerProviderSpec$$CountedSanitizer"
      }
      "async-only" {
        class = "akka.javasdk.impl.SanitizerProviderSpec$$AsyncOnlySanitizer"
      }
      "credit-card" {
        predefined = CREDIT_CARD
      }
      "warm-colors" {
        pattern = "(?i)(red|orange|yellow)"
      }
    }
    """)
    .withFallback(ConfigFactory.load())

  private val logsConfig = ConfigFactory
    .parseString(s"""
    akka.javasdk.sanitization.sanitizers {
      "logs-only" {
        pattern = "(secret)"
        apply-at = ["logs"]
      }
      "implemented-logs" {
        class = "akka.javasdk.impl.SanitizerProviderSpec$$NoContextSanitizer"
        apply-at = ["logs"]
      }
      "implemented-default" {
        class = "akka.javasdk.impl.SanitizerProviderSpec$$NoContextSanitizer"
      }
      "agent-scoped" {
        pattern = "(other)"
        agents = ["some-agent"]
      }
      "by-name-only" {
        pattern = "(account)"
        apply-at = ["client"]
      }
    }
    """)
    .withFallback(ConfigFactory.load())

  class ContextSanitizer(context: SanitizerContext) extends TextSanitizer {
    private val replacement = context.config().getString("replacement")
    override def sanitize(text: String): String = text.replace(context.name(), replacement)
  }

  class NoContextSanitizer extends TextSanitizer {
    override def sanitize(text: String): String = text.toUpperCase
  }

  // Masks what a configured classifier labels, composing through the injected client rather than
  // holding a reference to the classifier itself.
  class ClassifierBackedSanitizer(client: ClassifierClient) extends TextSanitizer {
    override def sanitize(text: String): String =
      client.classify("labeller", text).label().orElse("no label")
  }

  val ConstructionCount = new AtomicInteger(0)

  class CountedSanitizer extends TextSanitizer {
    ConstructionCount.incrementAndGet()
    override def sanitize(text: String): String = text
  }

  @volatile var GatedConstructionStarted = new CountDownLatch(1)
  @volatile var GatedConstructionRelease = new CountDownLatch(1)

  // Holds its construction until the test releases it.
  class GatedSanitizer extends TextSanitizer {
    GatedConstructionStarted.countDown()
    GatedConstructionRelease.await(10, TimeUnit.SECONDS)
    override def sanitize(text: String): String = text
  }

  private val constructionConfig = ConfigFactory
    .parseString(s"""
    akka.javasdk.sanitization.sanitizers {
      "gated" { class = "akka.javasdk.impl.SanitizerProviderSpec$$GatedSanitizer" }
      "no-context" { class = "akka.javasdk.impl.SanitizerProviderSpec$$NoContextSanitizer" }
    }
    """)
    .withFallback(ConfigFactory.load())

  // Overrides the async variant instead of the sync one. sanitize throwing proves the SDK only ever
  // invokes sanitizeAsync.
  class AsyncOnlySanitizer extends TextSanitizer {
    override def sanitize(text: String): String =
      throw new UnsupportedOperationException("sync sanitize must not be called when sanitizeAsync is overridden")
    override def sanitizeAsync(text: String): CompletionStage[String] =
      CompletableFuture.supplyAsync(() => s"async:$text")
  }

  class NotASanitizer

  /**
   * Test-only stand-in for the runtime's sanitizer registry: registers every entry by its name, and a log sanitizer
   * only under a name the other entries do not hold, then looks an entry up by name and masks with it. A supplied
   * sanitizer is called in Future(...).flatten so a synchronous throw becomes a failed Future, as the runtime does.
   */
  final class LoopbackSpiSanitizerClient extends SpiSanitizerClient {
    @volatile private var byName: Map[String, String => Future[String]] = Map.empty
    @volatile var lastName: Option[String] = None

    def register(entries: Seq[SpiDataSanitizer], logEntries: Seq[SpiLogSanitizer]): Unit = {
      val configured = entries.map(entry => entry.name -> maskerOf(entry)).toMap
      byName = configured ++ logEntries.collect {
        case entry if !configured.contains(entry.name) => entry.name -> logMaskerOf(entry)
      }
    }

    override def sanitize(name: String, text: String): Future[String] = {
      lastName = Some(name)
      byName.get(name) match {
        case Some(mask) => mask(text)
        case None       => Future.failed(new IllegalArgumentException(s"No sanitizer registered with name [$name]"))
      }
    }

    private def maskerOf(entry: SpiDataSanitizer): String => Future[String] =
      entry match {
        case custom: SpiDataSanitizer.Custom => supplied(custom.instance)
        case regex: SpiDataSanitizer.Regex   => masked(regex.pattern)
        // A predefined group expands to patterns the runtime holds, so this stand-in only records the name.
        case _: SpiDataSanitizer.Predefined => Future.successful
      }

    private def logMaskerOf(entry: SpiLogSanitizer): String => Future[String] =
      entry match {
        case custom: SpiLogSanitizer.Custom => supplied(custom.instance)
        case regex: SpiLogSanitizer.Regex   => masked(regex.pattern)
        case _: SpiLogSanitizer.Predefined  => Future.successful
      }

    private def supplied(instance: SpiSanitizer): String => Future[String] =
      text => Future(instance.sanitize(text))(ExecutionContext.parasitic).flatten

    private def masked(pattern: scala.util.matching.Regex): String => Future[String] =
      text => Future.successful(pattern.replaceAllIn(text, m => "*" * (m.end - m.start)))
  }

  private val classifierClient: ClassifierClient = new ClassifierClient {
    override def classify(name: String, input: String): Classification = Classification.label(s"$name:$input")
    override def classifyAsync(name: String, input: String): CompletionStage[Classification] =
      CompletableFuture.completedFuture(classify(name, input))
  }

  // Simplified stand-in for Sdk.wiredInstance: injects a ClassifierClient or SanitizerContext by constructor
  // shape and unwraps InvocationTargetException so a sanitizer constructor's own exceptions surface as
  // themselves.
  private def wireSanitizer(clz: Class[TextSanitizer], context: SanitizerContext): TextSanitizer =
    try {
      try clz.getConstructor(classOf[ClassifierClient]).newInstance(classifierClient)
      catch {
        case _: NoSuchMethodException =>
          try clz.getConstructor(classOf[SanitizerContext]).newInstance(context)
          catch {
            case _: NoSuchMethodException => clz.getConstructor().newInstance()
          }
      }
    } catch {
      case exc: java.lang.reflect.InvocationTargetException if exc.getCause != null => throw exc.getCause
    }

  /** A provider wired to a fresh loopback runtime client, registered with whatever is configured. */
  private def newProvider(
      system: akka.actor.typed.ActorSystem[_],
      config: Config): (SanitizerProvider, LoopbackSpiSanitizerClient) = {
    val runtimeClient = new LoopbackSpiSanitizerClient
    val provider = new SanitizerProvider(system, config, runtimeClient, wireSanitizer)
    runtimeClient.register(provider.spiSanitizers(_ => Set.empty), provider.spiLogSanitizers)
    (provider, runtimeClient)
  }
}

class SanitizerProviderSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with LogCapturing {
  import SanitizerProviderSpec._

  private def await(stage: CompletionStage[String]): String =
    stage.toCompletableFuture.get(3, TimeUnit.SECONDS)

  "The SanitizerProvider" should {

    "validate" in {
      val (provider, _) = newProvider(system, config)
      provider.validate()
    }

    "construct with and without a SanitizerContext constructor" in {
      val (provider, _) = newProvider(system, config)

      await(provider.client.sanitizeAsync("with-context", "mask with-context here")) shouldEqual
      "mask [redacted] here"
      await(provider.client.sanitizeAsync("no-context", "quiet")) shouldEqual "QUIET"
    }

    "construct with an injected ClassifierClient" in {
      val (provider, _) = newProvider(system, config)

      await(provider.client.sanitizeAsync("classifier-backed", "text")) shouldEqual "labeller:text"
    }

    "support the blocking sanitize(...) alongside sanitizeAsync(...)" in {
      val (provider, _) = newProvider(system, config)

      provider.client.sanitize("no-context", "quiet") shouldEqual "QUIET"
    }

    "invoke sanitizeAsync when the implementation overrides it" in {
      val (provider, _) = newProvider(system, config)

      await(provider.client.sanitizeAsync("async-only", "text")) shouldEqual "async:text"
    }

    "construct each sanitizer once" in {
      ConstructionCount.set(0)
      val (provider, _) = newProvider(system, config)

      provider.validate()
      await(provider.client.sanitizeAsync("counted", "a"))
      await(provider.client.sanitizeAsync("counted", "b"))

      ConstructionCount.get() shouldEqual 1
    }

    "mask with a constructed sanitizer while another one is being constructed" in {
      GatedConstructionStarted = new CountDownLatch(1)
      GatedConstructionRelease = new CountDownLatch(1)
      val (provider, _) = newProvider(system, constructionConfig)
      provider.client.sanitize("no-context", "quiet") shouldEqual "QUIET"

      val constructing = Future(provider.client.sanitize("gated", "x"))(ExecutionContext.global)
      try {
        GatedConstructionStarted.await(3, TimeUnit.SECONDS) shouldBe true
        // Runs on its own thread, because a call that waits for the construction above does not return.
        Await.result(
          Future(provider.client.sanitize("no-context", "still"))(ExecutionContext.global),
          3.seconds) shouldEqual
        "STILL"
      } finally GatedConstructionRelease.countDown()
      Await.result(constructing, 3.seconds) shouldEqual "x"
    }

    "mask with a pattern entry through the runtime" in {
      val (provider, _) = newProvider(system, config)

      provider.client.sanitize("warm-colors", "the red car") shouldEqual "the *** car"
    }

    "reach a predefined entry under its own name" in {
      val (provider, runtimeClient) = newProvider(system, config)

      provider.client.sanitize("credit-card", "some text")

      runtimeClient.lastName shouldEqual Some("credit-card")
    }

    "reach an entry that masks log messages only, and one that masks nothing on its own" in {
      val (provider, _) = newProvider(system, logsConfig)

      provider.client.sanitize("logs-only", "a secret here") shouldEqual "a ****** here"
      provider.client.sanitize("by-name-only", "an account here") shouldEqual "an ******* here"
    }

    "report an unknown sanitizer name, with the configured ones" in {
      val (provider, _) = newProvider(system, config)

      val blocking = intercept[IllegalArgumentException](provider.client.sanitize("does-not-exist", "x"))
      blocking.getMessage should include("No sanitizer configured with name [does-not-exist]")
      blocking.getMessage should include("no-context")

      // the async variant reports it through the returned stage
      val failure = intercept[java.util.concurrent.ExecutionException](
        provider.client.sanitizeAsync("does-not-exist", "x").toCompletableFuture.get(3, TimeUnit.SECONDS))
      failure.getCause shouldBe an[IllegalArgumentException]
      failure.getCause.getMessage should include("No sanitizer configured with name [does-not-exist]")
    }

    "throw from validate when the configured class does not implement TextSanitizer" in {
      val faultyConfig = ConfigFactory
        .parseString(s"""
          akka.javasdk.sanitization.sanitizers {
            "bad" {
              class = "akka.javasdk.impl.SanitizerProviderSpec$$NotASanitizer"
            }
          }
          """)
        .withFallback(ConfigFactory.load())
      val (provider, _) = newProvider(system, faultyConfig)

      intercept[IllegalArgumentException](provider.validate()).getMessage should include(
        "Sanitizer [bad] must implement [akka.javasdk.TextSanitizer]")
    }

    "throw from validate when the configured class is missing" in {
      val faultyConfig = ConfigFactory
        .parseString("""
          akka.javasdk.sanitization.sanitizers {
            "missing" {
              class = "com.example.NoSuchSanitizer"
            }
          }
          """)
        .withFallback(ConfigFactory.load())
      val (provider, _) = newProvider(system, faultyConfig)

      intercept[IllegalArgumentException](provider.validate()).getMessage should include(
        "Sanitizer [missing] implementation class [com.example.NoSuchSanitizer] not found")
    }

    "throw from validate when required config is missing" in {
      val faultyConfig = ConfigFactory
        .parseString(s"""
          akka.javasdk.sanitization.sanitizers {
            "with-context" {
              class = "akka.javasdk.impl.SanitizerProviderSpec$$ContextSanitizer"
            }
          }
          """)
        .withFallback(ConfigFactory.load())
      val (provider, _) = newProvider(system, faultyConfig)

      intercept[com.typesafe.config.ConfigException](provider.validate())
    }
  }

  "The entries handed to the runtime" should {

    "carry the resolved agents, and an implementation instance for each class" in {
      val (provider, _) = newProvider(system, config)

      val entries = provider.spiSanitizers {
        case sanitizer if sanitizer.name == "warm-colors" => Set("some-agent")
        case _                                            => Set.empty
      }

      entries.collect { case custom: SpiDataSanitizer.Custom => custom.name } should contain("no-context")
      entries.collectFirst { case regex: SpiDataSanitizer.Regex => regex }.get.enabledForComponents shouldEqual Set(
        "some-agent")
      val predefined = entries.collectFirst { case predefined: SpiDataSanitizer.Predefined => predefined }.get
      predefined.name shouldEqual "credit-card"
      predefined.group shouldEqual "CREDIT_CARD"
    }

    "hand over an entry whose scope matches no agent as masking nothing on its own" in {
      val scopedConfig = ConfigFactory
        .parseString("""
          akka.javasdk.sanitization.sanitizers {
            "missing-scope" { pattern = "a", agents = ["no-such-agent"] }
          }
          """)
        .withFallback(ConfigFactory.load())
      val (provider, _) = newProvider(system, scopedConfig)

      // an empty set is every agent to the runtime, which is the opposite of what the entry asks for
      provider.spiSanitizers(_ => Set.empty).map(e => e.applyAt -> e.enabledForComponents) shouldEqual Seq(
        Set(SpiDataSanitizer.ApplyAt.Client) -> Set.empty)
      provider.spiSanitizers(_ => Set("some-agent")).map(e => e.applyAt -> e.enabledForComponents) shouldEqual Seq(
        Set(SpiDataSanitizer.ApplyAt.ModelCall, SpiDataSanitizer.ApplyAt.ToolResult) -> Set("some-agent"))
    }

    "leave out a disabled entry" in {
      val disabledConfig = ConfigFactory
        .parseString(s"""
          akka.javasdk.sanitization.sanitizers {
            "counted" {
              class = "akka.javasdk.impl.SanitizerProviderSpec$$CountedSanitizer"
              enabled = false
            }
          }
          """)
        .withFallback(ConfigFactory.load())
      ConstructionCount.set(0)
      val (provider, _) = newProvider(system, disabledConfig)

      provider.spiSanitizers(_ => Set.empty) shouldBe empty
      provider.validate()
      ConstructionCount.get() shouldEqual 0
    }
  }

  "The log sanitizers handed to the runtime" should {

    "carry every pattern and predefined entry that names no point, under its own name" in {
      val (provider, _) = newProvider(system, config)

      // the class entries of this config name no point either, and a class masks log messages only when it says so
      provider.spiLogSanitizers.map(_.name).toSet shouldEqual Set("credit-card", "warm-colors")
    }

    "carry a class entry that names logs, and not one that names no point" in {
      val (provider, _) = newProvider(system, logsConfig)

      val logSanitizers = provider.spiLogSanitizers.map(_.name).toSet
      logSanitizers should contain("implemented-logs")
      logSanitizers should not contain "implemented-default"
    }

    "hand over a declarative entry on both carriers under the same name, and a class entry on the setup carrier only" in {
      def describe(entry: SpiLogSanitizer): (String, String) = entry match {
        case regex: SpiLogSanitizer.Regex           => regex.name -> regex.pattern.regex
        case predefined: SpiLogSanitizer.Predefined => predefined.name -> predefined.group
        case custom: SpiLogSanitizer.Custom         => custom.name -> custom.implementationClass
      }

      val (provider, _) = newProvider(system, logsConfig)
      Sanitization.loadSettings(logsConfig).logSanitizers.map(describe) shouldEqual Seq("logs-only" -> "(secret)")
      provider.spiLogSanitizers.map(describe).toSet shouldEqual Set(
        "logs-only" -> "(secret)",
        "implemented-logs" -> classOf[NoContextSanitizer].getName)

      // a pattern and a predefined group, both unscoped
      val (unscoped, _) = newProvider(system, config)
      Sanitization.loadSettings(config).logSanitizers.map(describe).toSet shouldEqual
      unscoped.spiLogSanitizers.map(describe).toSet
      unscoped.spiLogSanitizers.map(describe).toSet should contain("credit-card" -> "CREDIT_CARD")
    }

    "mask with the instance of a class based entry" in {
      val (provider, _) = newProvider(system, logsConfig)

      val custom = provider.spiLogSanitizers.collectFirst {
        case custom: SpiLogSanitizer.Custom if custom.name == "implemented-logs" => custom
      }.get

      custom.implementationClass shouldEqual classOf[NoContextSanitizer].getName
      Await.result(custom.instance.sanitize("quiet"), 3.seconds) shouldEqual "QUIET"
    }

    "leave out an entry bound to an agent, and one that masks nothing on its own" in {
      val (provider, _) = newProvider(system, logsConfig)

      provider.spiLogSanitizers.map(_.name).toSet shouldEqual Set("logs-only", "implemented-logs")
    }

    "hand over an entry that masks log messages only as masking nothing at the points of an agent" in {
      val (provider, _) = newProvider(system, logsConfig)

      val entries = provider.spiSanitizers {
        case sanitizer if sanitizer.name == "agent-scoped" => Set("some-agent")
        case _                                             => Set.empty
      }

      val nothingOnItsOwn = Set[SpiDataSanitizer.ApplyAt](SpiDataSanitizer.ApplyAt.Client)
      entries.map(e => e.name -> (e.applyAt, e.enabledForComponents)).toMap shouldEqual Map(
        "logs-only" -> (nothingOnItsOwn, Set.empty),
        "implemented-logs" -> (nothingOnItsOwn, Set.empty),
        "by-name-only" -> (nothingOnItsOwn, Set.empty),
        "implemented-default" -> (Set(
          SpiDataSanitizer.ApplyAt.ModelCall,
          SpiDataSanitizer.ApplyAt.ToolResult), Set.empty),
        "agent-scoped" -> (Set(SpiDataSanitizer.ApplyAt.ModelCall, SpiDataSanitizer.ApplyAt.ToolResult),
        Set("some-agent")))
    }
  }
}
