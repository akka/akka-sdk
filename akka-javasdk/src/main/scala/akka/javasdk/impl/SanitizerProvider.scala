/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.Future
import scala.jdk.FutureConverters._
import scala.util.control.NonFatal

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.LogSanitizer
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

  lazy val configuredSanitizers: Seq[ConfiguredSanitizer] =
    Sanitization.configuredSanitizers(applicationConfig, interfacesOf)

  private lazy val byName: Map[String, ConfiguredSanitizer] =
    configuredSanitizers.map(s => s.name -> s).toMap

  // Each sanitizer is constructed once (at validate() time, or lazily on first invocation via the SPI adapter) and
  // memoized. A call to one that exists reads the map without a lock. An instance is a TextSanitizer, a
  // LogSanitizer or both.
  private val cache = new ConcurrentHashMap[String, AnyRef]()

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
        case SanitizerKind.Implementation(className) =>
          new SpiLogSanitizer.Custom(
            s.name,
            className,
            new SanitizerProvider.SpiLogSanitizerAdapter(() => getOrCreate(s.name)),
            s.config)
        case _ =>
          Sanitization
            .declarativeSpiLogSanitizer(s)
            .getOrElse(throw new IllegalStateException(s"Sanitizer [${s.name}] has no runtime log entry"))
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

  private def getOrCreate(name: String): AnyRef = {
    val cached = cache.get(name)
    if (cached ne null) cached
    else {
      requireConfigured(name)
      // Only on a miss: computeIfAbsent can lock the bin even when the key is present. The mapping function runs at
      // most once per name and holds that lock while it constructs, which validate() does before any traffic.
      cache.computeIfAbsent(name, _ => createSanitizer(byName(name)))
    }
  }

  private def createSanitizer(s: ConfiguredSanitizer): AnyRef = {
    val className = s.kind match {
      case SanitizerKind.Implementation(className) => className
      case other =>
        throw new IllegalArgumentException(s"Sanitizer [${s.name}] is not implemented by a class, but by [$other]")
    }
    val clz = loadClass(s.name, className)
    val context = new SanitizerContextImpl(s.name, s.config)
    if (classOf[LogSanitizer].isAssignableFrom(clz)) constructLogSanitizer(s.name, clz, context)
    else wireSanitizer(clz.asInstanceOf[Class[TextSanitizer]], context)
  }

  // A log sanitizer runs for every log line of the service, so its constructor takes nothing but its
  // SanitizerContext. The general injection would also offer the application config and the user's
  // DependencyProvider.
  private def constructLogSanitizer(name: String, clz: Class[_], context: SanitizerContext): AnyRef = {
    val constructor = clz.getDeclaredConstructors match {
      case Array(c) if c.getParameterCount == 0                                         => c
      case Array(c) if c.getParameterTypes.sameElements(Seq(classOf[SanitizerContext])) => c
      case _ =>
        throw new IllegalArgumentException(
          s"Sanitizer [$name] implements [${classOf[LogSanitizer].getName}], so [${clz.getName}] must have one " +
          s"public constructor, which takes no parameter or a [${classOf[SanitizerContext].getName}]")
    }
    try if (constructor.getParameterCount == 0) constructor.newInstance().asInstanceOf[AnyRef]
    else constructor.newInstance(context).asInstanceOf[AnyRef]
    catch {
      case exc: InvocationTargetException if exc.getCause != null => throw exc.getCause
    }
  }

  // Loads the class of an entry to see which sanitizer interfaces it implements, which decides where it can mask.
  private def interfacesOf(name: String, className: String): SanitizerInterfaces = {
    val clz = loadClass(name, className)
    val interfaces = SanitizerInterfaces(
      text = classOf[TextSanitizer].isAssignableFrom(clz),
      log = classOf[LogSanitizer].isAssignableFrom(clz))
    if (!interfaces.text && !interfaces.log)
      throw new IllegalArgumentException(
        s"Sanitizer [$name] must implement [${classOf[TextSanitizer].getName}] or " +
        s"[${classOf[LogSanitizer].getName}], but [$className] implements neither")
    interfaces
  }

  private def loadClass(name: String, className: String): Class[_] =
    try system.dynamicAccess.classLoader.loadClass(className)
    catch {
      case _: ClassNotFoundException =>
        throw new IllegalArgumentException(s"Sanitizer [$name] implementation class [$className] not found")
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object SanitizerProvider {

  /**
   * Wraps a user sanitizer so the runtime can invoke it once registered. `resolve` runs on every invocation rather than
   * at construction, so registration can happen before the sanitizer is safe to construct; `getOrCreate` memoizes, so
   * it is cheap after the first resolution. A failed construction, an exception from the user code and a null stage all
   * return a failed Future rather than throw.
   */
  private final class SpiSanitizerAdapter(resolve: () => AnyRef) extends SpiSanitizer {
    override def sanitize(text: String): Future[String] =
      try {
        resolve() match {
          case sanitizer: TextSanitizer =>
            val stage = sanitizer.sanitizeAsync(text)
            if (stage eq null) Future.failed(new NullPointerException("TextSanitizer.sanitizeAsync returned null"))
            else stage.asScala
          // an entry of a class that only masks log messages, called by name
          case sanitizer: LogSanitizer => logSanitize(sanitizer, text)
          case other =>
            Future.failed(new IllegalStateException(s"[${other.getClass.getName}] is not a sanitizer"))
        }
      } catch {
        case NonFatal(e) => Future.failed(e)
      }
  }

  /** As [[SpiSanitizerAdapter]], for the log sanitizer entry of a class that implements [[LogSanitizer]]. */
  private final class SpiLogSanitizerAdapter(resolve: () => AnyRef) extends SpiSanitizer {
    override def sanitize(text: String): Future[String] =
      try {
        resolve() match {
          case sanitizer: LogSanitizer => logSanitize(sanitizer, text)
          case other =>
            Future.failed(new IllegalStateException(s"[${other.getClass.getName}] is not a LogSanitizer"))
        }
      } catch {
        case NonFatal(e) => Future.failed(e)
      }
  }

  private def logSanitize(sanitizer: LogSanitizer, text: String): Future[String] = {
    val masked = sanitizer.sanitize(text)
    if (masked eq null) Future.failed(new NullPointerException("LogSanitizer.sanitize returned null"))
    else Future.successful(masked)
  }
}
