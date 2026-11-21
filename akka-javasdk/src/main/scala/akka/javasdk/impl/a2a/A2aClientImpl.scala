/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.javasdk.a2a.A2aAgentCard
import akka.javasdk.a2a.A2aClient
import akka.javasdk.a2a.RemoteA2aAgentClient
import akka.runtime.sdk.spi.SpiSpiffeContext
import com.typesafe.config.Config
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.{ Context => OtelContext }
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 *
 * How the client reaches another Akka service: its base URL, the connection pool settings that resolve its name in dev
 * mode, and the headers that identify this service to it.
 */
@InternalApi
private[akka] final case class A2aServiceRoute(
    baseUrl: String,
    poolSettings: Option[ConnectionPoolSettings],
    headers: Seq[HttpHeader])

/**
 * INTERNAL API
 *
 * The state of every remote agent the service calls, shared by the clients of all components so that cards and access
 * tokens are fetched once.
 *
 * @param serviceRoute
 *   how to reach another Akka service by its name
 */
@InternalApi
private[akka] final class A2aRemoteAgents(
    system: ActorSystem[_],
    a2aConfig: Config,
    serviceRoute: String => A2aServiceRoute = A2aRemoteAgents.noServices) {
  A2aRemoteAgentSettings.validateDefaults(a2aConfig)
  A2aRemoteAgentSettings.unusedTlsWarnings(a2aConfig).foreach(A2aRemoteAgents.log.warn)

  // The configured agents by card URL, so that an agent reached by URL is known by its configured name
  private val namesByCardUrl: Map[String, String] = {
    val remoteAgents = a2aConfig.getConfig("remote-agents")
    remoteAgents
      .root()
      .keySet()
      .asScala
      .toSeq
      .sorted
      .flatMap { name =>
        val key = com.typesafe.config.ConfigUtil.joinPath(name, "card-url")
        if (remoteAgents.hasPath(key)) Some(A2aRemoteAgents.normalized(remoteAgents.getString(key)) -> name)
        else None
      }
      .reverse
      .toMap
  }

  /** The name of the configuration under `remote-agents` with this card URL, if there is one. */
  def configuredName(cardUrl: String): Option[String] = namesByCardUrl.get(A2aRemoteAgents.normalized(cardUrl))

  /**
   * The name that guardrails and traces see for a remote agent: the configured name, also for an agent given by URL or
   * service when a configuration has the same card URL, else the name on its card.
   */
  def agentName(target: A2aRemoteTarget, cardUrl: String, card: A2aAgentCard): String = target match {
    case A2aRemoteTarget.Named(name) => name
    case _ =>
      configuredName(cardUrl).getOrElse(Option(card.name()).filterNot(_.isBlank).getOrElse("remote_agent"))
  }

  private val known = new ConcurrentHashMap[A2aRemoteTarget, RemoteA2aAgentState]()

  // Agents given by URL are not limited by the configuration, so only the most recently used are kept
  private val byUrl = new java.util.LinkedHashMap[String, RemoteA2aAgentState](16, 0.75f, true) {
    override def removeEldestEntry(eldest: java.util.Map.Entry[String, RemoteA2aAgentState]): Boolean =
      size() > A2aRemoteAgents.MaxAgentsByUrl
  }

  def stateFor(target: A2aRemoteTarget): RemoteA2aAgentState =
    target match {
      case A2aRemoteTarget.CardUrl(url) =>
        byUrl.synchronized {
          Option(byUrl.get(url)).getOrElse {
            val state = newState(target)
            byUrl.put(url, state)
            state
          }
        }
      case _ => known.computeIfAbsent(target, newState(_))
    }

  private def newState(target: A2aRemoteTarget): RemoteA2aAgentState = {
    val settings = A2aRemoteAgentSettings.resolve(a2aConfig, target)
    target match {
      case A2aRemoteTarget.Service(serviceName) =>
        val route = serviceRoute(serviceName)
        new RemoteA2aAgentState(
          system,
          settings.copy(cardUrl = route.baseUrl.stripSuffix("/") + A2aRemoteAgentSettings.CardPath),
          Some(route))
      case _ => new RemoteA2aAgentState(system, settings)
    }
  }

  private[a2a] def agentsByUrl: Int = byUrl.synchronized(byUrl.size())
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object A2aRemoteAgents {
  val MaxAgentsByUrl = 64

  val noServices: String => A2aServiceRoute = serviceName =>
    throw new IllegalArgumentException(s"Service [$serviceName] cannot be reached")

  def normalized(cardUrl: String): String = cardUrl.trim.stripSuffix("/")
  private val log = LoggerFactory.getLogger(classOf[A2aRemoteAgents])
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object A2aClientImpl {
  val CallerHeaderName = "X-Akka-Caller"

  def traceHeaders(telemetryContext: Option[OtelContext]): Vector[HttpHeader] = {
    val builder = Vector.newBuilder[HttpHeader]
    telemetryContext.foreach(context =>
      W3CTraceContextPropagator
        .getInstance()
        .inject(context, null, (_: scala.Any, key: String, value: String) => builder += RawHeader(key, value)))
    builder.result()
  }

  /**
   * @param caller
   *   the SPIFFE caller value of the calling component
   * @param tracing
   *   the tracer for a span per call, whose parent is the telemetry context
   * @param governance
   *   the guardrails and sanitizers of the calling component, none on the tool path where the agent loop runs them
   */
  def apply(
      agents: A2aRemoteAgents,
      telemetryContext: Option[OtelContext],
      caller: Option[String],
      remoteTasks: Option[A2aRemoteTasks] = None,
      tracing: Option[() => Tracer] = None,
      governance: Option[A2aCallGovernance] = None): A2aClientImpl =
    new A2aClientImpl(
      agents,
      traceHeaders(telemetryContext) ++ caller.map(RawHeader(CallerHeaderName, _)),
      caller.map(RawHeader(SpiSpiffeContext.CallerSpiffeHeaderName, _)).toSeq,
      remoteTasks,
      for { tracer <- tracing; parent <- telemetryContext } yield A2aOutboundTracing(parent, tracer),
      governance)
}

/**
 * INTERNAL API
 *
 * The parent of the spans of a component's calls to remote agents, and the tracer that starts them.
 */
@InternalApi
private[akka] final case class A2aOutboundTracing(parent: OtelContext, tracer: () => Tracer)

/**
 * INTERNAL API
 *
 * @param headers
 *   headers of every call
 * @param serviceHeaders
 *   headers of calls to another Akka service only, which identify the calling component
 */
@InternalApi
private[akka] final class A2aClientImpl(
    agents: A2aRemoteAgents,
    headers: Seq[HttpHeader],
    serviceHeaders: Seq[HttpHeader] = Nil,
    remoteTasks: Option[A2aRemoteTasks] = None,
    tracing: Option[A2aOutboundTracing] = None,
    governance: Option[A2aCallGovernance] = None)
    extends A2aClient {

  override def forAgent(name: String): RemoteA2aAgentClient = forTarget(A2aRemoteTarget.named(name), None)

  override def forCardUrl(cardUrl: String): RemoteA2aAgentClient = forTarget(A2aRemoteTarget.cardUrl(cardUrl), None)

  override def forService(serviceName: String): RemoteA2aAgentClient =
    forTarget(A2aRemoteTarget.service(serviceName), None)

  def remoteAgents: A2aRemoteAgents = agents

  /** A client whose calls use the given timeout instead of the one configured for the agent. */
  def forTarget(target: A2aRemoteTarget, timeout: Option[FiniteDuration]): RemoteA2aAgentClientImpl = {
    val state = agents.stateFor(target)
    val extra = if (state.route.isDefined) state.serviceHeaders ++ serviceHeaders else Nil
    new RemoteA2aAgentClientImpl(
      state,
      headers ++ extra,
      timeoutOverride = timeout,
      target = Some(target),
      remoteTasks = remoteTasks,
      agentName = card => agents.agentName(target, state.settings.cardUrl, card),
      tracing = tracing,
      governance = governance)
  }
}
