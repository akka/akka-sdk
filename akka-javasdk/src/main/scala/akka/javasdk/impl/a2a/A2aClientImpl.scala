/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.util.concurrent.ConcurrentHashMap

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import akka.javasdk.a2a.A2aClient
import akka.javasdk.a2a.RemoteA2aAgentClient
import com.typesafe.config.Config
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.{ Context => OtelContext }

/**
 * INTERNAL API
 *
 * The state of every remote agent the service calls, shared by the clients of all components so that cards and access
 * tokens are fetched once.
 */
@InternalApi
private[akka] final class A2aRemoteAgents(system: ActorSystem[_], a2aConfig: Config) {
  A2aRemoteAgentSettings.validateDefaults(a2aConfig)

  private val named = new ConcurrentHashMap[String, RemoteA2aAgentState]()

  // Agents given by URL are not limited by the configuration, so only the most recently used are kept
  private val byUrl = new java.util.LinkedHashMap[String, RemoteA2aAgentState](16, 0.75f, true) {
    override def removeEldestEntry(eldest: java.util.Map.Entry[String, RemoteA2aAgentState]): Boolean =
      size() > A2aRemoteAgents.MaxAgentsByUrl
  }

  def stateFor(nameOrCardUrl: String): RemoteA2aAgentState =
    if (A2aRemoteAgentSettings.isCardUrl(nameOrCardUrl))
      byUrl.synchronized {
        Option(byUrl.get(nameOrCardUrl)).getOrElse {
          val state = newState(nameOrCardUrl)
          byUrl.put(nameOrCardUrl, state)
          state
        }
      }
    else named.computeIfAbsent(nameOrCardUrl, newState(_))

  private def newState(nameOrCardUrl: String): RemoteA2aAgentState =
    new RemoteA2aAgentState(system, A2aRemoteAgentSettings.resolve(a2aConfig, nameOrCardUrl))

  private[a2a] def agentsByUrl: Int = byUrl.synchronized(byUrl.size())
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object A2aRemoteAgents {
  val MaxAgentsByUrl = 64
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object A2aClientImpl {
  val CallerHeaderName = "X-Akka-Caller"

  private def traceHeaders(telemetryContext: Option[OtelContext]): Vector[HttpHeader] = {
    val builder = Vector.newBuilder[HttpHeader]
    telemetryContext.foreach(context =>
      W3CTraceContextPropagator
        .getInstance()
        .inject(context, null, (_: scala.Any, key: String, value: String) => builder += RawHeader(key, value)))
    builder.result()
  }

  def apply(agents: A2aRemoteAgents, telemetryContext: Option[OtelContext], caller: Option[String]): A2aClientImpl =
    new A2aClientImpl(agents, traceHeaders(telemetryContext) ++ caller.map(RawHeader(CallerHeaderName, _)))
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class A2aClientImpl(agents: A2aRemoteAgents, headers: Seq[HttpHeader]) extends A2aClient {

  override def forAgent(nameOrCardUrl: String): RemoteA2aAgentClient =
    new RemoteA2aAgentClientImpl(agents.stateFor(nameOrCardUrl), headers)
}
