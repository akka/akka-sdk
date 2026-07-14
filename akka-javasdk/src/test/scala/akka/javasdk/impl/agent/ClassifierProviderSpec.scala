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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.agent.Classification
import akka.javasdk.agent.Classifier
import akka.javasdk.agent.ClassifierContext
import akka.javasdk.agent.Decision
import akka.javasdk.agent.ModelGuardrail
import akka.runtime.sdk.spi.SpiClassifier
import akka.runtime.sdk.spi.SpiClassifierClient
import akka.runtime.sdk.spi.SpiConfiguredClassifier
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
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
    override def classifyAsync(input: String): CompletionStage[Classification] =
      CompletableFuture.completedFuture(Classification.of(threshold, s"classified:$input"))
  }

  class NoContextClassifier extends Classifier {
    override def classifyAsync(input: String): CompletionStage[Classification] =
      CompletableFuture.completedFuture(Classification.label("ok"))
  }

  class ThrowingClassifier extends Classifier {
    override def classifyAsync(input: String): CompletionStage[Classification] =
      throw new IllegalStateException("kaboom")
  }

  class WrongClassifier

  // Resolves another named classifier from its own constructor, to compose an ensemble.
  class EnsembleClassifier(context: ClassifierContext) extends Classifier {
    private val delegate = context.classifierClient().classifier("toxicity")
    override def classifyAsync(input: String): CompletionStage[Classification] = delegate.classifyAsync(input)
  }

  class CyclicA(context: ClassifierContext) extends Classifier {
    context.classifierClient().classifier("cyclic-b")
    override def classifyAsync(input: String): CompletionStage[Classification] =
      CompletableFuture.completedFuture(Classification.label("a"))
  }

  class CyclicB(context: ClassifierContext) extends Classifier {
    context.classifierClient().classifier("cyclic-a")
    override def classifyAsync(input: String): CompletionStage[Classification] =
      CompletableFuture.completedFuture(Classification.label("b"))
  }

  val ConcurrentCallCount = new AtomicInteger(0)

  // Tracks how many calls are in flight concurrently, to check the shared singleton instance
  // doesn't corrupt state across parallel classify() calls.
  class ConcurrentClassifier extends Classifier {
    override def classifyAsync(input: String): CompletionStage[Classification] = {
      val inFlight = ConcurrentCallCount.incrementAndGet()
      try CompletableFuture.completedFuture(Classification.score(inFlight.toDouble))
      finally ConcurrentCallCount.decrementAndGet()
    }
  }

  // Implements both ModelGuardrail and Classifier -- allowed, since a classifier is looked up by
  // name rather than dispatched, so there's no ambiguity with the guardrail's boundary dispatch.
  // Zero-arg constructor is the only shape a dual-purpose class can have: classifier construction
  // goes through the SDK's wiredInstance (which requires a single public constructor), while
  // guardrail construction only matches (GuardrailContext) or (), and neither path matches the
  // other's context type.
  class DualPurpose extends ModelGuardrail with Classifier {
    override def decide(ctx: ModelGuardrail.CallContext): Decision = new Decision.Allow()
    override def classifyAsync(input: String): CompletionStage[Classification] =
      CompletableFuture.completedFuture(Classification.label(s"dual:$input"))
  }

  // Test-only stand-in for the runtime's classifier dispatch (akka-runtime's
  // RuntimeClassifierClient + Classifier wrapper + EmbeddedSdkDispatch.wrapClassifier): looks a
  // registered classifier up by name and invokes it, wrapping the call in Future(...).flatten so a
  // synchronous throw becomes a failed Future -- exactly the normalization the real runtime
  // provides. The SDK side itself does not double-translate.
  final class LoopbackSpiClassifierClient extends SpiClassifierClient {
    @volatile private var byName: Map[String, SpiConfiguredClassifier] = Map.empty

    def register(configured: Seq[SpiConfiguredClassifier]): Unit = byName = configured.map(c => c.name -> c).toMap

    override def classify(name: String, content: SpiClassifier.Content): Future[SpiClassifier.Classification] =
      byName.get(name) match {
        case Some(c) => Future(c.instance.classify(content))(ExecutionContext.parasitic).flatten
        case None    => Future.failed(new IllegalArgumentException(s"No classifier registered with name [$name]"))
      }
  }

  // Mirrors Sdk.wiredInstance's unwrapping of InvocationTargetException, so a classifier
  // constructor's own exceptions (e.g. missing config, cyclic dependency) surface as themselves
  // rather than wrapped -- matching production wiring. Does NOT mirror wiredInstance's
  // single-public-constructor requirement or general dependency injection; those live in
  // Sdk.wireClassifier and are out of this spec's reach.
  private def wireClassifier(clz: Class[Classifier], context: ClassifierContext): Classifier =
    try {
      try clz.getConstructor(classOf[ClassifierContext]).newInstance(context)
      catch {
        case _: NoSuchMethodException => clz.getConstructor().newInstance()
      }
    } catch {
      case exc: java.lang.reflect.InvocationTargetException if exc.getCause != null => throw exc.getCause
    }

  /** Builds a provider wired to a fresh loopback runtime client, registered with whatever's configured. */
  private def newProvider(system: akka.actor.typed.ActorSystem[_], config: Config): ClassifierProvider = {
    val runtimeClient = new LoopbackSpiClassifierClient
    val provider = new ClassifierProvider(system, config, runtimeClient, wireClassifier)
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
      val provider = new ClassifierProvider(system, faultyConfig, new LoopbackSpiClassifierClient, wireClassifier)
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
      val provider = new ClassifierProvider(system, faultyConfig, new LoopbackSpiClassifierClient, wireClassifier)
      intercept[ConfigException] {
        provider.validate()
      }
    }

    "construct with and without a ClassifierContext constructor" in {
      val provider = newProvider(system, config)

      val toxicity = provider.client.classifier("toxicity")
      val result = toxicity.classifyAsync("some text").toCompletableFuture.get(3, TimeUnit.SECONDS)
      result.score() shouldBe java.util.Optional.of(0.8)
      result.label() shouldBe java.util.Optional.of("classified:some text")

      val noContext = provider.client.classifier("no-context")
      val result2 = noContext.classifyAsync("anything").toCompletableFuture.get(3, TimeUnit.SECONDS)
      result2.label() shouldBe java.util.Optional.of("ok")
    }

    "support the blocking classify(...) alongside classifyAsync(...)" in {
      val provider = newProvider(system, config)
      val result = provider.client.classifier("toxicity").classify("some text")
      result.label() shouldBe java.util.Optional.of("classified:some text")
    }

    "throw a descriptive IllegalArgumentException for an unknown classifier name" in {
      val provider = newProvider(system, config)
      val ex = intercept[IllegalArgumentException] {
        provider.client.classifier("does-not-exist")
      }
      ex.getMessage should include("No classifier configured with name [does-not-exist]")
      ex.getMessage should include("toxicity")
    }

    "return the same instance across repeated lookups" in {
      val provider = newProvider(system, config)
      (provider.client.classifier("toxicity") should be).theSameInstanceAs(provider.client.classifier("toxicity"))
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
      val classifier = provider.client.classifier("throwing")

      // must not throw synchronously
      val stage = classifier.classifyAsync("anything")
      val failure = intercept[java.util.concurrent.ExecutionException] {
        stage.toCompletableFuture.get(3, TimeUnit.SECONDS)
      }
      failure.getCause shouldBe a[IllegalStateException]
      failure.getCause.getMessage shouldBe "kaboom"
    }

    "let a classifier compose another configured classifier via ClassifierContext.classifierClient" in {
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
      val ensemble = provider.client.classifier("ensemble")
      val result = ensemble.classifyAsync("hi").toCompletableFuture.get(3, TimeUnit.SECONDS)
      result.label() shouldBe java.util.Optional.of("classified:hi")
    }

    "throw a descriptive error instead of overflowing the stack on a cyclic classifier dependency" in {
      val cfg = ConfigFactory.parseString(s"""
        akka.javasdk.agent.classifiers {
          "cyclic-a" {
            class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$CyclicA"
          }
          "cyclic-b" {
            class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$CyclicB"
          }
        }
        """)
      // Not newProvider: that eagerly forces spiConfiguredClassifiers for registration, which would
      // hit the same cycle outside this intercept block.
      val provider = new ClassifierProvider(system, cfg, new LoopbackSpiClassifierClient, wireClassifier)
      val ex = intercept[IllegalStateException] {
        provider.client.classifier("cyclic-a")
      }
      ex.getMessage should include("Cyclic classifier dependency detected")
      ex.getMessage should include("cyclic-a")
      ex.getMessage should include("cyclic-b")
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
      val classifier = provider.client.classifier("concurrent")

      val pool = Executors.newFixedThreadPool(8)
      try {
        val latch = new CountDownLatch(50)
        val results = (1 to 50).map { _ =>
          val f = pool.submit(() => classifier.classifyAsync("x").toCompletableFuture.get(3, TimeUnit.SECONDS))
          latch.countDown()
          f
        }
        latch.await(5, TimeUnit.SECONDS) shouldBe true
        results.foreach(_.get(3, TimeUnit.SECONDS))
        ConcurrentCallCount.get() shouldBe 0
      } finally pool.shutdown()
    }

    "accept a class implementing both ModelGuardrail and Classifier" in {
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
        classifierProvider.client.classifier("dual").classifyAsync("x").toCompletableFuture.get(3, TimeUnit.SECONDS)
      result.label() shouldBe java.util.Optional.of("dual:x")

      val guardrailCfg = ConfigFactory.parseString(s"""
        akka.javasdk.agent.guardrails {
          "dual" {
            class = "akka.javasdk.impl.agent.ClassifierProviderSpec$$DualPurpose"
            agents = ["some-agent"]
            category = TOXIC
            use-for = ["model-response"]
          }
        }
        """)
      val guardrailProvider = new GuardrailProvider(system, guardrailCfg, testTracerFactory)
      guardrailProvider.validate()
      guardrailProvider.agentGuardrails("some-agent", role = None).modelResponseGuardrails.size shouldBe 1
    }
  }
}
