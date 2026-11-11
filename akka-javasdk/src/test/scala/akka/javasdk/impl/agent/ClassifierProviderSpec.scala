/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.agent.AgentResponseGuardrail
import akka.javasdk.agent.Classification
import akka.javasdk.agent.Classifier
import akka.javasdk.agent.ClassifierClient
import akka.javasdk.agent.ClassifierContext
import akka.javasdk.agent.Decision
import akka.runtime.sdk.spi.SpiClassifier
import akka.runtime.sdk.spi.SpiClassifierClient
import akka.runtime.sdk.spi.SpiConfiguredClassifier
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.{ Context => TelemetryContext }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object ClassifierProviderSpec {
  private val testTracerFactory: () => Tracer = () => OpenTelemetry.noop().getTracer("test")

  private val config = ConfigFactory.parseString(s"""
    akka.javasdk.agent.classifiers {
      "toxicity" {
        class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$ToxicityClassifier"
        threshold = 0.8
      }
      "no-context" {
        class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$NoContextClassifier"
      }
    }
    """)

  class ToxicityClassifier(context: ClassifierContext) extends Classifier {
    private val threshold = context.config().getDouble("threshold")
    override def classify(input: String): Classification =
      Classification.of(threshold, s"classified:$input")
  }

  class NoContextClassifier extends Classifier {
    override def classify(input: String): Classification =
      Classification.label("ok")
  }

  class ThrowingClassifier extends Classifier {
    override def classify(input: String): Classification =
      throw new IllegalStateException("kaboom")
  }

  class WrongClassifier

  class NullStageClassifier extends Classifier {
    override def classify(input: String): Classification = Classification.label("unused")
    override def classifyAsync(input: String): CompletionStage[Classification] = null
  }

  // A stage of supplyAsync fails with a CompletionException around the exception of the supplier.
  class SupplyAsyncFailedStageClassifier extends Classifier {
    override def classify(input: String): Classification = Classification.label("unused")
    override def classifyAsync(input: String): CompletionStage[Classification] =
      CompletableFuture.supplyAsync[Classification](() => throw new IllegalStateException("supplier blew up"))
  }

  private val failingConfig = ConfigFactory.parseString(s"""
    akka.javasdk.agent.classifiers {
      "throwing" { class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$ThrowingClassifier" }
      "null-stage" { class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$NullStageClassifier" }
      "supply-async" { class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$SupplyAsyncFailedStageClassifier" }
      "missing" { class = "com.example.NoSuchClassifier" }
    }
    """)

  // Composes another configured classifier by calling it through the injected client, never holding
  // a reference to the classifier itself.
  class EnsembleClassifier(client: ClassifierClient) extends Classifier {
    override def classify(input: String): Classification =
      client.classifyWithResult("toxicity", input).classification()
  }

  // Composes the classifiers named in its `members` config, like the documented ensemble.
  class MembersEnsembleClassifier(context: ClassifierContext, client: ClassifierClient) extends Classifier {
    private val members = context.config().getStringList("members").asScala.toList
    override def classify(input: String): Classification = {
      members.foreach(name => client.classify(name, input))
      Classification.label(s"${context.name()}:$input")
    }
  }

  private val cycleConfig = ConfigFactory.parseString(s"""
    akka.javasdk.agent.classifiers {
      "ensemble-a" {
        class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$MembersEnsembleClassifier"
        members = ["ensemble-b"]
      }
      "ensemble-b" {
        class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$MembersEnsembleClassifier"
        members = ["ensemble-a"]
      }
      "self" {
        class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$MembersEnsembleClassifier"
        members = ["self"]
      }
      "diamond-top" {
        class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$MembersEnsembleClassifier"
        members = ["diamond-left", "diamond-right"]
      }
      "diamond-left" {
        class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$MembersEnsembleClassifier"
        members = ["no-context"]
      }
      "diamond-right" {
        class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$MembersEnsembleClassifier"
        members = ["no-context"]
      }
      "no-context" { class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$NoContextClassifier" }
    }
    """)

  // Calls the `next` classifier in a continuation of a member call that completes on another thread.
  class ChainedClassifier(context: ClassifierContext, client: ClassifierClient) extends Classifier {
    private val next = context.config().getString("next")
    override def classify(input: String): Classification = Classification.label("unused")
    override def classifyAsync(input: String): CompletionStage[Classification] =
      client
        .classifyAsync("delayed", input)
        .thenCompose[Classification](_ => client.classifyAsync(next, input))
  }

  class DelayedClassifier extends Classifier {
    override def classify(input: String): Classification = Classification.label("unused")
    override def classifyAsync(input: String): CompletionStage[Classification] =
      CompletableFuture.supplyAsync[Classification](
        () => Classification.label("delayed"),
        CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS))
  }

  private val continuationCycleConfig = ConfigFactory.parseString(s"""
    akka.javasdk.agent.classifiers {
      "chained-a" {
        class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$ChainedClassifier"
        next = "chained-b"
      }
      "chained-b" {
        class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$ChainedClassifier"
        next = "chained-a"
      }
      "delayed" { class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$DelayedClassifier" }
    }
    """)

  val CallerKey: ContextKey[String] = ContextKey.named("caller")
  @volatile var CallerSeenByClassifier: String = "unset"

  // Records the caller value of the context that is current while the classifier runs.
  class ContextRecordingClassifier extends Classifier {
    override def classify(input: String): Classification = {
      CallerSeenByClassifier = TelemetryContext.current().get(CallerKey)
      Classification.label("recorded")
    }
  }

  // Makes its own context current and does not close the scope.
  class LeakyClassifier extends Classifier {
    override def classify(input: String): Classification = {
      TelemetryContext.current().`with`(CallerKey, "leaked").makeCurrent()
      Classification.label("leaky")
    }
  }

  private val contextConfig = ConfigFactory.parseString(s"""
    akka.javasdk.agent.classifiers {
      "recording" { class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$ContextRecordingClassifier" }
      "leaky" { class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$LeakyClassifier" }
      "leaky-ensemble" {
        class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$MembersEnsembleClassifier"
        members = ["leaky"]
      }
    }
    """)

  // Overrides the async variant instead of the sync one -- the poweruser shape. classify throwing
  // proves the SDK only ever invokes classifyAsync.
  class AsyncOnlyClassifier extends Classifier {
    override def classify(input: String): Classification =
      throw new UnsupportedOperationException("sync classify must not be called when classifyAsync is overridden")
    override def classifyAsync(input: String): CompletionStage[Classification] =
      CompletableFuture.supplyAsync(() => Classification.label(s"async:$input"))
  }

  @volatile var GatedConstructionStarted = new CountDownLatch(1)
  @volatile var GatedConstructionRelease = new CountDownLatch(1)

  // Holds its construction until the test releases it.
  class GatedClassifier extends Classifier {
    GatedConstructionStarted.countDown()
    GatedConstructionRelease.await(10, TimeUnit.SECONDS)
    override def classify(input: String): Classification = Classification.label("gated")
  }

  private val constructionConfig = ConfigFactory.parseString(s"""
    akka.javasdk.agent.classifiers {
      "gated" { class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$GatedClassifier" }
      "no-context" { class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$NoContextClassifier" }
    }
    """)

  val ConcurrentCallCount = new AtomicInteger(0)

  // Tracks how many calls are in flight concurrently, to check the shared singleton instance
  // doesn't corrupt state across parallel classify() calls.
  class ConcurrentClassifier extends Classifier {
    override def classify(input: String): Classification = {
      val inFlight = ConcurrentCallCount.incrementAndGet()
      try Classification.score(inFlight.toDouble)
      finally ConcurrentCallCount.decrementAndGet()
    }
  }

  // Implements both AgentResponseGuardrail and Classifier -- allowed, since a classifier is looked up by
  // name rather than dispatched, so there's no ambiguity with the guardrail's boundary dispatch.
  // Zero-arg constructor is the only shape a dual-purpose class can have: classifier construction
  // goes through the SDK's wiredInstance (which requires a single public constructor), while
  // guardrail construction only matches (GuardrailContext) or (), and neither path matches the
  // other's context type.
  class DualPurpose extends AgentResponseGuardrail with Classifier {
    override def decide(ctx: AgentResponseGuardrail.CallContext): Decision =
      new Decision.Allow()
    override def classify(input: String): Classification =
      Classification.label(s"dual:$input")
  }

  // Test-only stand-in for the runtime's classifier dispatch: looks a registered classifier up by
  // name and invokes it, wrapping the call in Future(...).flatten so a synchronous throw becomes a
  // failed Future, as the runtime does.
  val ClassificationId = "classification-1"

  final class LoopbackSpiClassifierClient extends SpiClassifierClient {
    @volatile private var byName: Map[String, SpiConfiguredClassifier] = Map.empty

    def register(configured: Seq[SpiConfiguredClassifier]): Unit = byName = configured.map(c => c.name -> c).toMap

    override def classifyWithResult(
        name: String,
        content: SpiClassifier.Content): Future[SpiClassifier.ClassificationResult] =
      byName.get(name) match {
        case Some(c) =>
          Future(c.instance.classify(content))(ExecutionContext.parasitic).flatten
            .map(new SpiClassifier.ClassificationResult(Some(ClassificationId), _))(ExecutionContext.parasitic)
        case None => Future.failed(new IllegalArgumentException(s"No classifier registered with name [$name]"))
      }
  }

  // Simplified stand-in for Sdk.wiredInstance: injects a ClassifierClient or ClassifierContext by
  // constructor shape and unwraps InvocationTargetException so a classifier constructor's own
  // exceptions (e.g. missing config) surface as themselves. Does not do the full dependency
  // injection or single-public-constructor enforcement that production wiring adds.
  private def wireClassifier(
      client: => ClassifierClient)(clz: Class[Classifier], context: ClassifierContext): Classifier =
    try {
      try clz.getConstructor(classOf[ClassifierContext], classOf[ClassifierClient]).newInstance(context, client)
      catch {
        case _: NoSuchMethodException =>
          try clz.getConstructor(classOf[ClassifierClient]).newInstance(client)
          catch {
            case _: NoSuchMethodException =>
              try clz.getConstructor(classOf[ClassifierContext]).newInstance(context)
              catch {
                case _: NoSuchMethodException => clz.getConstructor().newInstance()
              }
          }
      }
    } catch {
      case exc: java.lang.reflect.InvocationTargetException if exc.getCause != null => throw exc.getCause
    }

  /** Builds a provider wired to a fresh loopback runtime client, registered with whatever's configured. */
  private def newProvider(system: akka.actor.typed.ActorSystem[_], config: Config): ClassifierProvider = {
    val runtimeClient = new LoopbackSpiClassifierClient
    var provider: ClassifierProvider = null
    provider = new ClassifierProvider(system, config, runtimeClient, wireClassifier(provider.client))
    runtimeClient.register(provider.spiConfiguredClassifiers)
    provider
  }
}

class ClassifierProviderSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with LogCapturing {
  import ClassifierProviderSpec._

  "The ClassifierProvider" should {
    "validate" in {
      val provider = newProvider(system, config)
      provider.validate()
    }

    "throw from validate when the configured class doesn't implement Classifier" in {
      val faultyConfig =
        ConfigFactory
          .parseString(s"""
          akka.javasdk.agent.classifiers {
            "bad" {
              class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$WrongClassifier"
            }
          }
          """)
          .withFallback(config)
      val provider = new ClassifierProvider(
        system,
        faultyConfig,
        new LoopbackSpiClassifierClient,
        wireClassifier(sys.error("no ClassifierClient in this test")))
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage should include("must implement [akka.javasdk.agent.Classifier]")
    }

    "throw from validate when required config is missing" in {
      val faultyConfig =
        ConfigFactory
          .parseString(s"""
          akka.javasdk.agent.classifiers {
            "toxicity" {
              class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$ToxicityClassifier"
            }
          }
          """)
      val provider = new ClassifierProvider(
        system,
        faultyConfig,
        new LoopbackSpiClassifierClient,
        wireClassifier(sys.error("no ClassifierClient in this test")))
      intercept[ConfigException] {
        provider.validate()
      }
    }

    "construct with and without a ClassifierContext constructor" in {
      val provider = newProvider(system, config)

      val result = provider.client
        .classifyWithResultAsync("toxicity", "some text")
        .toCompletableFuture
        .get(3, TimeUnit.SECONDS)
        .classification()
      result.score() shouldBe java.util.Optional.of(0.8)
      result.label() shouldBe java.util.Optional.of("classified:some text")

      val result2 = provider.client
        .classifyWithResultAsync("no-context", "anything")
        .toCompletableFuture
        .get(3, TimeUnit.SECONDS)
        .classification()
      result2.label() shouldBe java.util.Optional.of("ok")
    }

    "support the blocking classify(...) alongside classifyAsync(...)" in {
      val provider = newProvider(system, config)
      val result = provider.client.classifyWithResult("toxicity", "some text").classification()
      result.label() shouldBe java.util.Optional.of("classified:some text")
    }

    "carry the classification id that the runtime reports" in {
      val provider = newProvider(system, config)
      val result = provider.client.classifyWithResult("toxicity", "some text")
      result.classificationId() shouldBe java.util.Optional.of(ClassificationId)
      result.classification().label() shouldBe java.util.Optional.of("classified:some text")

      val asyncResult =
        provider.client.classifyWithResultAsync("toxicity", "some text").toCompletableFuture.get(3, TimeUnit.SECONDS)
      asyncResult.classificationId() shouldBe java.util.Optional.of(ClassificationId)
    }

    "return a failed Future from the runtime entry rather than throw" in {
      val provider = newProvider(system, failingConfig)
      val instances = provider.spiConfiguredClassifiers.map(c => c.name -> c.instance).toMap

      def failure(name: String): Throwable = {
        // throws here if the adapter throws
        val classified = instances(name).classify(new SpiClassifier.TextContent("text", TelemetryContext.root()))
        intercept[Exception](Await.result(classified, 3.seconds))
      }

      failure("throwing") shouldBe an[IllegalStateException]
      failure("null-stage").getMessage shouldEqual "Classifier.classifyAsync returned null"
      // the exception of the supplier, not the CompletionException of its stage
      failure("supply-async") shouldBe an[IllegalStateException]
      failure("missing").getMessage should include("Classifier [missing] implementation class")
    }

    "throw a descriptive IllegalArgumentException for an unknown classifier name" in {
      val provider = newProvider(system, config)
      val ex = intercept[IllegalArgumentException] {
        provider.client.classifyWithResultAsync("does-not-exist", "x")
      }
      ex.getMessage should include("No classifier configured with name [does-not-exist]")
      ex.getMessage should include("toxicity")
    }

    "convert a thrown exception from classify(...) into a failed CompletionStage rather than propagating it" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.classifiers {
            "throwing" {
              class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$ThrowingClassifier"
            }
          }
          """)
      val provider = newProvider(system, cfg)

      // must not throw synchronously
      val stage = provider.client.classifyWithResultAsync("throwing", "anything")
      val failure = intercept[java.util.concurrent.ExecutionException] {
        stage.toCompletableFuture.get(3, TimeUnit.SECONDS)
      }
      failure.getCause shouldBe a[IllegalStateException]
      failure.getCause.getMessage shouldBe "kaboom"
    }

    "let a classifier compose another configured classifier through the client" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.classifiers {
            "ensemble" {
              class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$EnsembleClassifier"
            }
          }
          """)
        .withFallback(config)
      val provider = newProvider(system, cfg)
      val result = provider.client
        .classifyWithResultAsync("ensemble", "hi")
        .toCompletableFuture
        .get(3, TimeUnit.SECONDS)
        .classification()
      result.label() shouldBe java.util.Optional.of("classified:hi")
    }

    "fail a call that cycles through two classifiers with the chain in the message" in {
      val provider = newProvider(system, cycleConfig)
      intercept[IllegalStateException] {
        provider.client.classify("ensemble-a", "x")
      }.getMessage shouldBe "Classifier cycle detected: ensemble-a -> ensemble-b -> ensemble-a"

      val failure = intercept[java.util.concurrent.ExecutionException] {
        provider.client.classifyAsync("ensemble-b", "x").toCompletableFuture.get(3, TimeUnit.SECONDS)
      }
      failure.getCause shouldBe an[IllegalStateException]
      failure.getCause.getMessage shouldBe "Classifier cycle detected: ensemble-b -> ensemble-a -> ensemble-b"
    }

    "fail a classifier that lists itself as a member" in {
      val provider = newProvider(system, cycleConfig)
      intercept[IllegalStateException] {
        provider.client.classify("self", "x")
      }.getMessage shouldBe "Classifier cycle detected: self -> self"
    }

    "not report a cycle for a classifier reached twice on separate paths" in {
      val provider = newProvider(system, cycleConfig)
      provider.client.classify("diamond-top", "x").label() shouldBe java.util.Optional.of("diamond-top:x")
      // the chain is not left behind on the calling thread
      TelemetryContext.current() shouldBe TelemetryContext.root()
    }

    "fail a cycle through a continuation of a member call" in {
      val provider = newProvider(system, continuationCycleConfig)
      val failure = intercept[java.util.concurrent.ExecutionException] {
        provider.client.classifyAsync("chained-a", "x").toCompletableFuture.get(3, TimeUnit.SECONDS)
      }
      failure.getCause shouldBe an[IllegalStateException]
      failure.getCause.getMessage shouldBe "Classifier cycle detected: chained-a -> chained-b -> chained-a"
    }

    "run a classifier without the context of the caller" in {
      val provider = newProvider(system, contextConfig)
      val recording = provider.spiConfiguredClassifiers.find(_.name == "recording").get.instance
      val callerContext = TelemetryContext.root().`with`(CallerKey, "caller")
      Await.result(recording.classify(new SpiClassifier.TextContent("x", callerContext)), 3.seconds)
      CallerSeenByClassifier shouldBe null
    }

    "restore the context of the calling thread when a classifier leaves its own context current" in {
      val provider = newProvider(system, contextConfig)
      provider.client.classify("leaky-ensemble", "x").label() shouldBe java.util.Optional.of("leaky-ensemble:x")
      TelemetryContext.current() shouldBe TelemetryContext.root()
      provider.client.classify("leaky-ensemble", "x").label() shouldBe java.util.Optional.of("leaky-ensemble:x")
    }

    "invoke the overridden classifyAsync(...) and never the sync classify(...)" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.classifiers {
            "async-only" {
              class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$AsyncOnlyClassifier"
            }
          }
          """)
      val provider = newProvider(system, cfg)

      // the async override is used, both through the async and the blocking client method
      val result = provider.client
        .classifyWithResultAsync("async-only", "x")
        .toCompletableFuture
        .get(3, TimeUnit.SECONDS)
        .classification()
      result.label() shouldBe java.util.Optional.of("async:x")
      provider.client.classifyWithResult("async-only", "y").classification().label() shouldBe java.util.Optional.of(
        "async:y")
    }

    "classify with a constructed classifier while another one is being constructed" in {
      GatedConstructionStarted = new CountDownLatch(1)
      GatedConstructionRelease = new CountDownLatch(1)
      val provider = newProvider(system, constructionConfig)
      provider.client.classifyWithResult("no-context", "x").classification().label().get() shouldEqual "ok"

      val constructing =
        Future(provider.client.classifyWithResult("gated", "x").classification())(ExecutionContext.global)
      try {
        GatedConstructionStarted.await(3, TimeUnit.SECONDS) shouldBe true
        // Runs on its own thread, because a call that waits for the construction above does not return.
        Await
          .result(
            Future(provider.client.classifyWithResult("no-context", "x").classification())(ExecutionContext.global),
            3.seconds)
          .label()
          .get() shouldEqual "ok"
      } finally GatedConstructionRelease.countDown()
      Await.result(constructing, 3.seconds).label().get() shouldEqual "gated"
    }

    "handle concurrent classify(...) calls against the shared singleton instance safely" in {
      val cfg = ConfigFactory.parseString(s"""
        akka.javasdk.agent.classifiers {
          "concurrent" {
            class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$ConcurrentClassifier"
          }
        }
        """)
      val provider = newProvider(system, cfg)

      val pool = Executors.newFixedThreadPool(8)
      try {
        val latch = new CountDownLatch(50)
        val results = (1 to 50).map { _ =>
          val f =
            pool.submit(() =>
              provider.client
                .classifyWithResultAsync("concurrent", "x")
                .toCompletableFuture
                .get(3, TimeUnit.SECONDS)
                .classification())
          latch.countDown()
          f
        }
        latch.await(5, TimeUnit.SECONDS) shouldBe true
        results.foreach(_.get(3, TimeUnit.SECONDS))
        ConcurrentCallCount.get() shouldBe 0
      } finally pool.shutdown()
    }

    "accept a class implementing both AgentResponseGuardrail and Classifier" in {
      val classifierCfg = ConfigFactory.parseString(s"""
        akka.javasdk.agent.classifiers {
          "dual" {
            class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$DualPurpose"
          }
        }
        """)
      val classifierProvider = newProvider(system, classifierCfg)
      classifierProvider.validate()
      val result =
        classifierProvider.client
          .classifyWithResultAsync("dual", "x")
          .toCompletableFuture
          .get(3, TimeUnit.SECONDS)
          .classification()
      result.label() shouldBe java.util.Optional.of("dual:x")

      val guardrailCfg = ConfigFactory.parseString(s"""
        akka.javasdk.agent.guardrails {
          "dual" {
            class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$DualPurpose"
            agents = ["some-agent"]
            category = TOXIC
          }
        }
        """)
      val guardrailProvider = new GuardrailProvider(system, guardrailCfg, testTracerFactory)
      guardrailProvider.validate()
      guardrailProvider.agentGuardrails("some-agent", role = None).guardrails.agentResponseGuardrails.size shouldBe 1
    }
  }
}
