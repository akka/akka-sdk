/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

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
import akka.runtime.sdk.spi.SpiClassifier
import akka.runtime.sdk.spi.SpiClassifierClient
import akka.runtime.sdk.spi.SpiConfiguredClassifier
import com.typesafe.config.Config
import io.opentelemetry.context.{ Context => TelemetryContext }

/**
 * INTERNAL API
 *
 * Classifiers are looked up by name (never dispatched by the runtime), so unlike [[GuardrailProvider]] there's no
 * per-agent/per-boundary selection here -- just construction, validation, and a [[ClassifierClient]] handing out
 * runtime-backed handles by name.
 *
 * Construction (via `wireClassifier`, routed through the SDK's general `wiredInstance` DI mechanism so any component
 * dependency -- not just [[ClassifierContext]] -- can be injected; the class must have a single public constructor, per
 * the component convention `wiredInstance` enforces) is lazy and memoized, guarded against cycles: a classifier's
 * constructor may pull in another configured classifier (through
 * [[akka.javasdk.agent.ClassifierContext.classifierClient]]) to build an ensemble, and resolving such a dependency
 * chain that loops back on itself fails with a clear error rather than a `StackOverflowError`.
 *
 * Invocation is different from construction: every `classify(...)` call -- direct, from a guardrail, or from ensemble
 * composition -- delegates through the runtime's [[SpiClassifierClient]], not a local call. The runtime is the single
 * choke point for observability, context propagation, and ledger recording; a thrown exception surfaces as a failed
 * `CompletionStage` because the runtime's own dispatch already normalizes synchronous throws into a failed `Future`
 * -- this class deliberately does not duplicate that translation.
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

  // Guarded by this provider's monitor. Construction happens once per name (at validate() time,
  // or lazily on first lookup), so contention is not a concern; the monitor is reentrant, which is
  // what allows a classifier's own construction to recursively resolve another one by name.
  private val cache = mutable.Map.empty[String, Classifier]
  private val inProgress = mutable.LinkedHashSet.empty[String]

  /**
   * The client handed out at construction time -- to a guardrail's `GuardrailContext`, to another classifier's
   * `ClassifierContext` for ensemble composition, and via the testkit -- where no per-call `telemetryContext` is
   * available yet. Known v1 gap: those classifications ride a root OTel context rather than the caller's (#5383).
   */
  val client: ClassifierClient = clientFor(TelemetryContext.root())

  /** A `ClassifierClient` whose invocations carry `telemetryContext` -- one per component injection. */
  def clientFor(telemetryContext: TelemetryContext): ClassifierClient = new ClassifierClient {
    private val handles = mutable.Map.empty[String, Classifier]

    override def classifier(name: String): Classifier = {
      // Construct (validating existence and detecting cycles eagerly) before taking this client's
      // monitor: construction can recurse into another client's classifier(...) (ensemble
      // constructors), which nests provider-monitor -> client-monitor -- so this lookup must not
      // nest them in the opposite order.
      getOrCreate(name)
      synchronized {
        handles.getOrElseUpdate(
          name,
          new Classifier {
            override def classifyAsync(input: String): CompletionStage[Classification] =
              runtimeClassifierClient
                .classify(name, new SpiClassifier.TextContent(input, telemetryContext))
                .map(ClassifierProvider.toClassification)(ExecutionContext.parasitic)
                .asJava
          })
      }
    }
  }

  private def getOrCreate(name: String): Classifier = synchronized {
    cache.get(name) match {
      case Some(instance) => instance
      case None =>
        if (inProgress.contains(name))
          throw new IllegalStateException(
            s"Cyclic classifier dependency detected: ${(inProgress.toSeq :+ name).mkString(" -> ")}")

        val configured = byName.getOrElse(
          name,
          throw new IllegalArgumentException(
            s"No classifier configured with name [$name]. Configured classifiers: [${byName.keys.mkString(", ")}]"))

        inProgress += name
        try {
          val instance = createClassifier(configured)
          cache(name) = instance
          instance
        } finally {
          inProgress -= name
        }
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

    val context = new ClassifierContextImpl(c.name, c.config, client)
    wireClassifier(clz.asInstanceOf[Class[Classifier]], context)
  }

  /** Eagerly constructs every configured classifier, so bad config/classes fail at startup. */
  def validate(): Unit = configuredClassifiers.foreach(c => getOrCreate(c.name))

  /**
   * The classifiers to register with the runtime, so [[SpiClassifierClient]] calls have something to invoke.
   *
   * Deliberately does not call `getOrCreate` here: this is built while assembling `SpiComponents`, which the runtime
   * handshake requires synchronously, before `preStart` runs `validate()` (see `SdkRunner`'s `validateClassifiers`)
   * -- i.e. before a user `DependencyProvider` may exist yet. Each adapter instead resolves its classifier lazily, on
   * first `classify(...)` call, which is guaranteed to be after `preStart` completes (nothing can call `classify()`
   * before the service has started); `validate()` still forces and caches every instance eagerly at that point, so bad
   * config/classes still fail at startup, just from `preStart` rather than from this constructor.
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

  private def toClassification(r: SpiClassifier.Result): Classification =
    new Classification(
      r.score.map(Double.box).toJava,
      r.label.toJava,
      r.confidence.map(Double.box).toJava,
      r.attributes.asJava)

  private def fromClassification(c: Classification): SpiClassifier.Result =
    new SpiClassifier.Result(
      c.score.toScala.map(Double.unbox),
      c.label.toScala,
      c.confidence.toScala.map(Double.unbox),
      c.attributes.asScala.toMap)

  /**
   * Wraps a user classifier so the runtime can invoke it once registered. `resolve` is called on every invocation
   * rather than once at adapter-construction time, so registration (`spiConfiguredClassifiers`) can happen before the
   * classifier itself is safe to construct (see the scaladoc there); `getOrCreate` memoizes, so this is cheap after the
   * first real resolution. Only [[SpiClassifier.TextContent]] exists in v1; the public `classify(String)` has no
   * per-call context parameter, so the content's `telemetryContext` isn't surfaced to the user's classifier.
   */
  private final class SpiClassifierAdapter(resolve: () => Classifier) extends SpiClassifier {
    override def classify(content: SpiClassifier.Content): Future[SpiClassifier.Result] =
      content match {
        case text: SpiClassifier.TextContent =>
          resolve().classifyAsync(text.text).asScala.map(fromClassification)(ExecutionContext.parasitic)
      }
  }
}
