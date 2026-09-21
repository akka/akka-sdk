/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

import scala.collection.mutable
import scala.concurrent.Future
import scala.jdk.FutureConverters._

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.SanitizerClient
import akka.javasdk.SanitizerContext
import akka.javasdk.TextSanitizer
import akka.runtime.sdk.spi.SpiDataSanitizer
import akka.runtime.sdk.spi.SpiSanitizer
import akka.runtime.sdk.spi.SpiSanitizerClient
import com.typesafe.config.Config

/**
 * INTERNAL API
 *
 * Parses the sanitizer entries, answers which of them apply to an agent, constructs the ones the service implements,
 * and masks through them by name.
 *
 * Invocation delegates through the runtime's [[SpiSanitizerClient]], which resolves the sanitizer registered under the
 * name and masks with it. The runtime invokes a sanitizer the SDK supplied on the SDK dispatcher, so this provider
 * calls `sanitizeAsync` and never blocks on user code.
 */
@InternalApi private[javasdk] final class SanitizerProvider(
    system: ActorSystem[_],
    applicationConfig: Config,
    runtimeSanitizerClient: SpiSanitizerClient,
    wireSanitizer: (Class[TextSanitizer], SanitizerContext) => TextSanitizer) {

  lazy val configuredSanitizers: Seq[ConfiguredSanitizer] = Sanitization.configuredSanitizers(applicationConfig)

  private lazy val byName: Map[String, ConfiguredSanitizer] =
    configuredSanitizers.map(s => s.name -> s).toMap

  // Guarded by this provider's monitor. Each sanitizer is constructed once (at validate() time, or lazily on
  // first invocation via the SPI adapter) and memoized.
  private val cache = mutable.Map.empty[String, TextSanitizer]

  val client: SanitizerClient = new SanitizerClient {
    override def sanitizeAsync(name: String, text: String): CompletionStage[String] = {
      requireConfigured(name)
      runtimeSanitizerClient.sanitize(byName(name).runtimeName, text).asJava
    }

    override def sanitize(name: String, text: String): String =
      try sanitizeAsync(name, text).toCompletableFuture.join()
      catch {
        case e: CompletionException => throw ErrorHandling.unwrapCompletionException(e)
      }
  }

  private def requireConfigured(name: String): Unit =
    if (!byName.contains(name))
      throw new IllegalArgumentException(
        s"No sanitizer configured with name [$name]. Configured sanitizers: [${byName.keys.mkString(", ")}]")

  /** The entries that apply to the agent with this component id and role. */
  def agentSanitizers(componentId: String, role: Option[String]): Seq[ConfiguredSanitizer] =
    configuredSanitizers.filter(_.appliesTo(componentId, role))

  /**
   * The entries to hand to the runtime, each with the agents it resolved to. A pattern or predefined entry is also in
   * [[akka.runtime.sdk.spi.SpiSettings]], which the runtime reads before this service has any classes; the runtime
   * joins the two by name.
   *
   * Each implementation is resolved lazily, on first `sanitize(...)` call, rather than here: this runs while assembling
   * `SpiComponents` (required synchronously for the runtime handshake), before `preStart` runs `validate()` and before
   * a user `DependencyProvider` exists.
   */
  def spiSanitizers(enabledForComponents: ConfiguredSanitizer => Set[String]): Seq[SpiDataSanitizer] =
    configuredSanitizers.map { s =>
      val components = enabledForComponents(s)
      s.kind match {
        case SanitizerKind.Implementation(className) =>
          new SpiDataSanitizer.Custom(
            name = s.name,
            implementationClass = className,
            instance = new SanitizerProvider.SpiSanitizerAdapter(() => getOrCreate(s.name)),
            useFor = s.useFor.map(_.configValue),
            enabledForComponents = components,
            config = s.config)
        case _ =>
          Sanitization
            .declarativeSpiSanitizer(s, components)
            .getOrElse(throw new IllegalStateException(s"Sanitizer [${s.name}] has no runtime entry"))
      }
    }

  /** Eagerly constructs every sanitizer the service implements, so bad config and classes fail at startup. */
  def validate(): Unit =
    configuredSanitizers.foreach { s =>
      if (s.kind.isInstanceOf[SanitizerKind.Implementation]) getOrCreate(s.name)
    }

  private def getOrCreate(name: String): TextSanitizer = synchronized {
    cache.get(name) match {
      case Some(instance) => instance
      case None =>
        requireConfigured(name)
        val instance = createSanitizer(byName(name))
        cache(name) = instance
        instance
    }
  }

  private def createSanitizer(s: ConfiguredSanitizer): TextSanitizer = {
    val className = s.kind match {
      case SanitizerKind.Implementation(className) => className
      case other =>
        throw new IllegalArgumentException(s"Sanitizer [${s.name}] is not implemented by a class, but by [$other]")
    }
    val clz =
      try system.dynamicAccess.classLoader.loadClass(className)
      catch {
        case _: ClassNotFoundException =>
          throw new IllegalArgumentException(s"Sanitizer [${s.name}] implementation class [$className] not found")
      }
    if (!classOf[TextSanitizer].isAssignableFrom(clz))
      throw new IllegalArgumentException(
        s"Sanitizer [${s.name}] must implement [${classOf[TextSanitizer].getName}], but [$className] does not")

    val context = new SanitizerContextImpl(s.name, s.config)
    wireSanitizer(clz.asInstanceOf[Class[TextSanitizer]], context)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object SanitizerProvider {

  /**
   * Wraps a user sanitizer so the runtime can invoke it once registered. `resolve` runs on every invocation rather than
   * at construction, so registration can happen before the sanitizer is safe to construct; `getOrCreate` memoizes, so
   * it is cheap after the first resolution.
   */
  private final class SpiSanitizerAdapter(resolve: () => TextSanitizer) extends SpiSanitizer {
    override def sanitize(text: String): Future[String] =
      resolve().sanitizeAsync(text).asScala
  }
}
