/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util.concurrent.CompletableFuture
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
import akka.runtime.sdk.spi.SpiLogSanitizer
import akka.runtime.sdk.spi.SpiSanitizer
import akka.runtime.sdk.spi.SpiSanitizerClient
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

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

  private val log = LoggerFactory.getLogger(classOf[SanitizerProvider])

  lazy val configuredSanitizers: Seq[ConfiguredSanitizer] = Sanitization.configuredSanitizers(applicationConfig)

  private lazy val byName: Map[String, ConfiguredSanitizer] =
    configuredSanitizers.map(s => s.name -> s).toMap

  // Guarded by this provider's monitor. Each sanitizer is constructed once (at validate() time, or lazily on
  // first invocation via the SPI adapter) and memoized.
  private val cache = mutable.Map.empty[String, TextSanitizer]

  val client: SanitizerClient = new SanitizerClient {
    override def sanitizeAsync(name: String, text: String): CompletionStage[String] =
      byName.get(name) match {
        case Some(_) => runtimeSanitizerClient.sanitize(name, text).asJava
        case None    => CompletableFuture.failedFuture(notConfigured(name))
      }

    override def sanitize(name: String, text: String): String = {
      requireConfigured(name)
      try sanitizeAsync(name, text).toCompletableFuture.join()
      catch {
        case e: CompletionException => throw ErrorHandling.unwrapCompletionException(e)
      }
    }
  }

  private def notConfigured(name: String): IllegalArgumentException =
    new IllegalArgumentException(
      s"No sanitizer configured with name [$name]. Configured sanitizers: [${byName.keys.mkString(", ")}]")

  private def requireConfigured(name: String): Unit =
    if (!byName.contains(name)) throw notConfigured(name)

  /** The entries that mask agent text and apply to the agent with this component id and role. */
  def agentSanitizers(componentId: String, role: Option[String]): Seq[ConfiguredSanitizer] =
    configuredSanitizers.filter(s => s.masksAgentText && s.appliesTo(componentId, role))

  /**
   * The entries to hand to the runtime, each with the agents it resolved to. A pattern or predefined entry is also in
   * [[akka.runtime.sdk.spi.SpiSettings]], which the runtime reads before this service has any classes; the runtime
   * joins the two by name. An entry that masks nothing at the points of an agent is here as well, saying so.
   *
   * Each implementation is resolved lazily, on first `sanitize(...)` call, rather than here: this runs while assembling
   * `SpiComponents` (required synchronously for the runtime handshake), before `preStart` runs `validate()` and before
   * a user `DependencyProvider` exists.
   */
  def spiSanitizers(enabledForComponents: ConfiguredSanitizer => Set[String]): Seq[SpiDataSanitizer] =
    configuredSanitizers.map { s =>
      val (applyAt, components) = agentBinding(s, enabledForComponents(s))
      s.kind match {
        case SanitizerKind.Implementation(className) =>
          new SpiDataSanitizer.Custom(
            name = s.name,
            implementationClass = className,
            instance = new SanitizerProvider.SpiSanitizerAdapter(() => getOrCreate(s.name)),
            applyAt = applyAt,
            enabledForComponents = components,
            config = s.config)
        case _ =>
          Sanitization
            .declarativeSpiSanitizer(s, applyAt, components)
            .getOrElse(throw new IllegalStateException(s"Sanitizer [${s.name}] has no runtime entry"))
      }
    }

  /**
   * The entries that mask log messages. A log line belongs to no agent, so these carry neither application points nor
   * components. The runtime builds the log engine from them once this service is handed over, and calls a sanitizer of
   * this list while it writes a log event.
   */
  def spiLogSanitizers: Seq[SpiLogSanitizer] =
    configuredSanitizers.filter(_.masksLogs).map { s =>
      s.kind match {
        case SanitizerKind.Pattern(regex) =>
          new SpiLogSanitizer.Regex(s.name, regex, s.config)
        case SanitizerKind.Predefined(group) =>
          new SpiLogSanitizer.Predefined(s.name, group, s.config)
        case SanitizerKind.Implementation(className) =>
          new SpiLogSanitizer.Custom(
            s.name,
            className,
            new SanitizerProvider.SpiSanitizerAdapter(() => getOrCreate(s.name)),
            s.config)
      }
    }

  // The runtime reads an empty component set as every agent, so an entry whose agents and agent roles match no
  // agent of this service is handed over as masking nothing on its own. It is reported, because a scope that
  // matches nothing is masking a deployment asked for and does not get.
  private def agentBinding(
      sanitizer: ConfiguredSanitizer,
      resolved: Set[String]): (Set[SpiDataSanitizer.ApplyAt], Set[String]) =
    if (resolved.nonEmpty || !sanitizer.scoped) (Sanitization.spiApplyAt(sanitizer), resolved)
    else {
      log.warn(
        "Sanitizer [{}] masks for no agent. It names agents [{}] and agent roles [{}], and this service has no " +
        "agent that matches. It is still reachable by name.",
        sanitizer.name,
        sanitizer.agents.toSeq.sorted.mkString(", "),
        sanitizer.agentRoles.toSeq.sorted.mkString(", "))
      (Sanitization.MasksNothingOnItsOwn, Set.empty)
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
