/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.agent.Classification
import akka.javasdk.agent.ClassificationResult
import akka.javasdk.agent.Classifier
import akka.javasdk.agent.ClassifierClient
import akka.javasdk.agent.ClassifierContext
import akka.javasdk.impl.ErrorHandling
import akka.runtime.sdk.spi.SpiClassifier
import akka.runtime.sdk.spi.SpiClassifierClient
import akka.runtime.sdk.spi.SpiConfiguredClassifier
import com.typesafe.config.Config
import io.opentelemetry.context.ContextKey
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

  // Each classifier is constructed once (at validate() time, or lazily on first invocation via the SPI adapter) and
  // memoized. A call to one that exists reads the map without a lock.
  private val cache = new ConcurrentHashMap[String, Classifier]()

  /**
   * The client for call sites with no per-call `telemetryContext` -- a guardrail's `GuardrailContext`, another
   * classifier's `ClassifierContext`, and the testkit. Its invocations carry a root OTel context that holds the call
   * chain of the OTel context current at the call. [[SpiClassifierAdapter]] uses that chain to detect cycles.
   */
  val client: ClassifierClient =
    newClient(() => ClassifierProvider.chainContext(ClassifierProvider.callChain(TelemetryContext.current())))

  /** A `ClassifierClient` whose invocations carry `telemetryContext` -- one per component injection. */
  def clientFor(telemetryContext: TelemetryContext): ClassifierClient = newClient(() => telemetryContext)

  private def newClient(telemetryContext: () => TelemetryContext): ClassifierClient = new ClassifierClient {
    override def classifyWithResultAsync(name: String, input: String): CompletionStage[ClassificationResult] = {
      requireConfigured(name)
      val context = telemetryContext()
      val result = new CompletableFuture[ClassificationResult]()
      runtimeClassifierClient
        .classifyWithResult(name, new SpiClassifier.TextContent(input, context))
        .map(ClassifierProvider.toClassificationResult)(ExecutionContext.parasitic)
        .onComplete { outcome =>
          def complete(): Unit = outcome match {
            case Success(classificationResult) => result.complete(classificationResult)
            case Failure(exc)                  => result.completeExceptionally(exc)
          }
          // A continuation that the caller registered on the stage runs inside complete(). With the call chain
          // current, a classifier call from that continuation carries the chain.
          if (ClassifierProvider.callChain(context).isEmpty) complete()
          else {
            val scope = context.makeCurrent()
            try complete()
            finally scope.close()
          }
        }(ExecutionContext.parasitic)
      result
    }

    override def classifyWithResult(name: String, input: String): ClassificationResult =
      try classifyWithResultAsync(name, input).toCompletableFuture.join()
      catch {
        case e: CompletionException => throw ErrorHandling.unwrapCompletionException(e)
      }

    override def classifyAsync(name: String, input: String): CompletionStage[Classification] =
      classifyWithResultAsync(name, input).thenApply(_.classification())

    override def classify(name: String, input: String): Classification =
      classifyWithResult(name, input).classification()
  }

  private def requireConfigured(name: String): Unit =
    if (!byName.contains(name))
      throw new IllegalArgumentException(
        s"No classifier configured with name [$name]. Configured classifiers: [${byName.keys.mkString(", ")}]")

  private def getOrCreate(name: String): Classifier = {
    val cached = cache.get(name)
    if (cached ne null) cached
    else {
      requireConfigured(name)
      // Only on a miss: computeIfAbsent can lock the bin even when the key is present. The mapping function runs at
      // most once per name and holds that lock while it constructs, which validate() does before any traffic.
      cache.computeIfAbsent(name, _ => createClassifier(byName(name)))
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
        instance = new ClassifierProvider.SpiClassifierAdapter(c.name, () => getOrCreate(c.name)))
    }
}

@InternalApi private[javasdk] object ClassifierProvider {

  // The names of the classifiers on the current call path, outermost first. Carried in the OTel context, which
  // travels through the runtime inside SpiClassifier.TextContent, so it survives the runtime's dispatch.
  private val CallChainKey: ContextKey[List[String]] = ContextKey.named("akka-classifier-call-chain")

  private def callChain(context: TelemetryContext): List[String] = {
    val chain = context.get(CallChainKey)
    if (chain eq null) Nil else chain
  }

  private def chainContext(chain: List[String]): TelemetryContext =
    if (chain.isEmpty) TelemetryContext.root() else TelemetryContext.root().`with`(CallChainKey, chain)

  private def toClassificationResult(result: SpiClassifier.ClassificationResult): ClassificationResult =
    new ClassificationResult(result.classificationId.toJava, toClassification(result.classification))

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
   * construct; `getOrCreate` memoizes, so it is cheap after the first resolution. A failed construction, an exception
   * from the user code and a null stage all return a failed Future rather than throw.
   *
   * The user classifier runs with a root OTel context that holds the call chain extended by `name`. A nested call
   * through the shared [[ClassifierProvider.client]] carries that chain, also from a continuation of a stage that the
   * client returned. An invocation whose chain already holds `name` fails instead of recursing. The chain is lost when
   * a classifier calls the client from a thread without the OTel context, such as its own executor, so a cycle through
   * such a call is not detected.
   */
  private final class SpiClassifierAdapter(name: String, resolve: () => Classifier) extends SpiClassifier {
    override def classify(content: SpiClassifier.Content): Future[SpiClassifier.Classification] =
      content match {
        case text: SpiClassifier.TextContent =>
          val chain = callChain(text.telemetryContext)
          if (chain.contains(name))
            Future.failed(new IllegalStateException(s"Classifier cycle detected: ${(chain :+ name).mkString(" -> ")}"))
          else
            try {
              val previous = TelemetryContext.current()
              val scope = chainContext(chain :+ name).makeCurrent()
              val stage =
                try resolve().classifyAsync(text.text)
                finally {
                  scope.close()
                  // close() does nothing when the user code left another context current
                  if (TelemetryContext.current() ne previous) previous.makeCurrent()
                }
              if (stage eq null) Future.failed(new NullPointerException("Classifier.classifyAsync returned null"))
              else ErrorHandling.asScalaUnwrapped(stage).map(fromClassification)(ExecutionContext.parasitic)
            } catch {
              case NonFatal(e) => Future.failed(e)
            }
      }
  }
}
