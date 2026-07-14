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

  // Composes another configured classifier by calling it through the client, never holding a
  // reference to the classifier itself.
  class EnsembleClassifier(context: ClassifierContext) extends Classifier {
    private val client = context.classifierClient()
    override def classifyAsync(input: String): CompletionStage[Classification] =
      client.classifyAsync("toxicity", input)
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

  // Test-only stand-in for the runtime's classifier dispatch: looks a registered classifier up by
  // name and invokes it, wrapping the call in Future(...).flatten so a synchronous throw becomes a
  // failed Future, as the runtime does.
  final class LoopbackSpiClassifierClient extends SpiClassifierClient {
    @volatile private var byName: Map[String, SpiConfiguredClassifier] = Map.empty

    def register(configured: Seq[SpiConfiguredClassifier]): Unit = byName = configured.map(c => c.name -> c).toMap

    override def classify(name: String, content: SpiClassifier.Content): Future[SpiClassifier.Classification] =
      byName.get(name) match {
        case Some(c) => Future(c.instance.classify(content))(ExecutionContext.parasitic).flatten
        case None    => Future.failed(new IllegalArgumentException(s"No classifier registered with name [$name]"))
      }
  }

  // Simplified stand-in for Sdk.wiredInstance: unwraps InvocationTargetException so a classifier
  // constructor's own exceptions (e.g. missing config) surface as themselves. Does not do the
  // dependency injection or single-public-constructor enforcement that production wiring adds.
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

      val result = provider.client.classifyAsync("toxicity", "some text").toCompletableFuture.get(3, TimeUnit.SECONDS)
      result.score() shouldBe java.util.Optional.of(0.8)
      result.label() shouldBe java.util.Optional.of("classified:some text")

      val result2 = provider.client.classifyAsync("no-context", "anything").toCompletableFuture.get(3, TimeUnit.SECONDS)
      result2.label() shouldBe java.util.Optional.of("ok")
    }

    "support the blocking classify(...) alongside classifyAsync(...)" in {
      val provider = newProvider(system, config)
      val result = provider.client.classify("toxicity", "some text")
      result.label() shouldBe java.util.Optional.of("classified:some text")
    }

    "throw a descriptive IllegalArgumentException for an unknown classifier name" in {
      val provider = newProvider(system, config)
      val ex = intercept[IllegalArgumentException] {
        provider.client.classifyAsync("does-not-exist", "x")
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
      val stage = provider.client.classifyAsync("throwing", "anything")
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
      val result = provider.client.classifyAsync("ensemble", "hi").toCompletableFuture.get(3, TimeUnit.SECONDS)
      result.label() shouldBe java.util.Optional.of("classified:hi")
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
              provider.client.classifyAsync("concurrent", "x").toCompletableFuture.get(3, TimeUnit.SECONDS))
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
        classifierProvider.client.classifyAsync("dual", "x").toCompletableFuture.get(3, TimeUnit.SECONDS)
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
