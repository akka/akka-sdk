/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.agent.Classification
import akka.javasdk.agent.Classifier
import akka.javasdk.agent.ClassifierClient
import akka.javasdk.agent.ClassifierContext
import akka.javasdk.impl.ErrorHandling
import akka.runtime.sdk.spi.SpiClassifier
import akka.runtime.sdk.spi.SpiClassifierClient
import akka.runtime.sdk.spi.SpiConfiguredClassifier
import com.typesafe.config.Config
import io.opentelemetry.context.{ Context => TelemetryContext }

/**
 * INTERNAL API
 *
 * Classifiers are looked up by name (never dispatched by the runtime), so unlike [[GuardrailProvider]] there's no
 * per-agent/per-boundary selection here -- just construction, validation, and a [[ClassifierClient]] that classifies
 * through them by name.
 *
 * The [[ClassifierClient]] exposes `classify(name, input)` rather than returning a [[Classifier]] instance. An ensemble
 * composes by having a [[ClassifierClient]] injected into its constructor and calling other classifiers by name.
 * Construction does not resolve sibling instances, so it cannot form a dependency graph: each classifier is built once
 * and memoized.
 *
 * Invocation delegates through the runtime's [[SpiClassifierClient]], the single choke point for observability, context
 * propagation, and ledger recording. A synchronous throw from a classifier surfaces as a failed `CompletionStage`,
 * since the runtime's dispatch normalizes it into a failed `Future`.
 */
@InternalApi private[javasdk] final class ClassifierProvider(
    system: ActorSystem[_],
    applicationConfig: Config,
    runtimeClassifierClient: SpiClassifierClient,
    wireClassifier: (Class[Classifier], ClassifierContext) => Classifier) {

  lazy val configuredClassifiers: Seq[ConfiguredClassifier] = {
    ClassifierSettings(applicationConfig.getConfig("akka.javasdk.agent.classifiers")).configuredClassifiers
  }

  private lazy val byName: Map[String, ConfiguredClassifier] =
    configuredClassifiers.map(c => c.name -> c).toMap

  // Guarded by this provider's monitor. Each classifier is constructed once (at validate() time, or
  // lazily on first invocation via the SPI adapter) and memoized.
  private val cache = mutable.Map.empty[String, Classifier]

  /**
   * The client for call sites with no per-call `telemetryContext` -- a guardrail's `GuardrailContext`, another
   * classifier's `ClassifierContext`, and the testkit. Its invocations carry a root OTel context.
   */
  val client: ClassifierClient = clientFor(TelemetryContext.root())

  /** A `ClassifierClient` whose invocations carry `telemetryContext` -- one per component injection. */
  def clientFor(telemetryContext: TelemetryContext): ClassifierClient = new ClassifierClient {
    override def classifyAsync(name: String, input: String): CompletionStage[Classification] = {
      requireConfigured(name)
      runtimeClassifierClient
        .classify(name, new SpiClassifier.TextContent(input, telemetryContext))
        .map(ClassifierProvider.toClassification)(ExecutionContext.parasitic)
        .asJava
    }

    override def classify(name: String, input: String): Classification =
      try classifyAsync(name, input).toCompletableFuture.join()
      catch {
        case e: CompletionException => throw ErrorHandling.unwrapCompletionException(e)
      }
  }

  private def requireConfigured(name: String): Unit =
    if (!byName.contains(name))
      throw new IllegalArgumentException(
        s"No classifier configured with name [$name]. Configured classifiers: [${byName.keys.mkString(", ")}]")

  private def getOrCreate(name: String): Classifier = synchronized {
    cache.get(name) match {
      case Some(instance) => instance
      case None =>
        requireConfigured(name)
        val instance = createClassifier(byName(name))
        cache(name) = instance
        instance
    }
  }

  private def createClassifier(c: ConfiguredClassifier): Classifier = {
    val clz =
      try system.dynamicAccess.classLoader.loadClass(c.implementationClass)
      catch {
        case _: ClassNotFoundException =>
          throw new IllegalArgumentException(
            s"Classifier [${c.name}] implementation class " +
            s"[${c.implementationClass}] not found")
      }
    if (!classOf[Classifier].isAssignableFrom(clz))
      throw new IllegalArgumentException(
        s"Classifier [${c.name}] must implement [${classOf[Classifier].getName}], " +
        s"but [${c.implementationClass}] does not")

    val context = new ClassifierContextImpl(c.name, c.config)
    wireClassifier(clz.asInstanceOf[Class[Classifier]], context)
  }

  /** Eagerly constructs every configured classifier, so bad config/classes fail at startup. */
  def validate(): Unit = configuredClassifiers.foreach(c => getOrCreate(c.name))

  /**
   * The classifiers to register with the runtime, so [[SpiClassifierClient]] calls have something to invoke.
   *
   * Each adapter resolves its classifier lazily, on first `classify(...)` call, rather than here: this runs while
   * assembling `SpiComponents` (required synchronously for the runtime handshake), before `preStart` runs `validate()`
   * and before a user `DependencyProvider` exists. `validate()` forces and caches every instance at startup, so bad
   * config or classes fail there.
   */
  def spiConfiguredClassifiers: Seq[SpiConfiguredClassifier] =
    configuredClassifiers.map { c =>
      new SpiConfiguredClassifier(
        name = c.name,
        implementationClass = c.implementationClass,
        config = c.config,
        instance = new ClassifierProvider.SpiClassifierAdapter(() => getOrCreate(c.name)))
    }
}

@InternalApi private[javasdk] object ClassifierProvider {

  private def toClassification(r: SpiClassifier.Classification): Classification =
    new Classification(
      r.score.map(Double.box).toJava,
      r.label.toJava,
      r.confidence.map(Double.box).toJava,
      r.attributes.asJava)

  private def fromClassification(c: Classification): SpiClassifier.Classification =
    new SpiClassifier.Classification(
      c.score.toScala.map(Double.unbox),
      c.label.toScala,
      c.confidence.toScala.map(Double.unbox),
      c.attributes.asScala.toMap)

  /**
   * Wraps a user classifier so the runtime can invoke it once registered. `resolve` runs on every invocation rather
   * than at construction, so registration (`spiConfiguredClassifiers`) can happen before the classifier is safe to
   * construct; `getOrCreate` memoizes, so it is cheap after the first resolution.
   */
  private final class SpiClassifierAdapter(resolve: () => Classifier) extends SpiClassifier {
    override def classify(content: SpiClassifier.Content): Future[SpiClassifier.Classification] =
      content match {
        case text: SpiClassifier.TextContent =>
          resolve().classifyAsync(text.text).asScala.map(fromClassification)(ExecutionContext.parasitic)
      }
  }
}
