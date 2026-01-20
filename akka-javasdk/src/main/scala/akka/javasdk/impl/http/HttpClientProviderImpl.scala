/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import java.net.InetSocketAddress
import java.util.concurrent.Executor

import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.discovery.Discovery
import akka.http.javadsl.model.HttpHeader
import akka.http.javadsl.model.headers.RawHeader
import akka.http.scaladsl.ClientTransport
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.javasdk.http.HttpClient
import akka.javasdk.http.HttpClientProvider
import akka.javasdk.impl.Settings
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.{ Context => OtelContext }
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class HttpClientProviderImpl(
    system: ActorSystem[_],
    telemetryContext: Option[OtelContext],
    remoteIdentificationHeader: Option[RawHeader],
    settings: Settings,
    sdkExecutor: Executor,
    connectionPoolSettings: Option[ConnectionPoolSettings] = None)
    extends HttpClientProvider {

  private val log = LoggerFactory.getLogger(classOf[HttpClientProvider])

  // FIXME(tracing): have context propagators provided by the runtime
  private val otelTraceHeaders: Vector[HttpHeader] = {
    val builder = Vector.newBuilder[HttpHeader]
    telemetryContext.foreach(context =>
      W3CTraceContextPropagator
        .getInstance()
        .inject(
          context,
          null,
          // Note: side-effecting instead of mutable collection
          (_: scala.Any, key: String, value: String) => {
            builder += RawHeader.create(key, value)
          }))
    builder.result()
  }

  private def isServiceName(name: String): Boolean =
    !name.contains('.') && !name.contains(':') && name != "localhost"

  override def httpClientFor(name: String): HttpClient = {
    val nameIsService = isServiceName(name)
    val (baseUrl, connectionPoolSettings) =
      if (nameIsService) {
        if (settings.devModeSettings.isDefined) {
          val devModeResolverTransport = ClientTransport.withCustomResolver((name, port) =>
            // dev mode, other service name, use Akka discovery to find it
            // the runtime has set up a mechanism that finds locally running
            // services. Since in dev mode blocking is probably fine for now.
            Discovery(system.classicSystem).discovery
              .lookup(name, 5.seconds)
              .map { resolved =>
                try {
                  val resolvedTarget = resolved.addresses.head
                  log.debug("Local service resolution found service [{}] at [{}:{}]", name, resolvedTarget.host, port)
                  InetSocketAddress.createUnresolved(resolvedTarget.host, resolvedTarget.port.getOrElse(port))
                } catch {
                  case NonFatal(ex) =>
                    throw new RuntimeException(
                      s"Failed to look up service [$name] in dev-mode, make sure that it is also running " +
                      "with a separate port and service name correctly defined in its application.conf under 'akka.javasdk.dev-mode.service-name' " +
                      "if it differs from the maven project name.",
                      ex)
                }
              }(system.executionContext))

          val connectionSettings = ClientConnectionSettings(system).withTransport(devModeResolverTransport)
          val poolSettings = ConnectionPoolSettings(system).withConnectionSettings(connectionSettings)

          (
            // always http because local
            s"http://$name",
            Some(poolSettings))
        } else {
          // production, request to other service, service mesh manages TLS
          (s"http://$name", None)
        }
      } else {
        // if it isn't a service, we expect it is arbitrary http or https server including the protocol part
        if (!name.startsWith("http://") && !name.startsWith("https://"))
          throw new IllegalArgumentException(
            s"httpClientFor accepts an akka service name or an arbitrary http server prefixed by http:// or https://, got [$name]")
        (name, None)
      }

    // FIXME fail fast on too large request
    // .filter(ExchangeFilterFunctions.limitResponseSize(MaxCrossServiceResponseContentLength))

    val defaultHeaders =
      if (nameIsService)
        // cross service request, include auth
        otelTraceHeaders ++ remoteIdentificationHeader
      else
        // arbitrary http request
        otelTraceHeaders
    new HttpClientImpl(system, baseUrl, defaultHeaders, sdkExecutor, connectionPoolSettings)
  }

  def withTelemetryContext(telemetryContext: OtelContext): HttpClientProvider =
    new HttpClientProviderImpl(
      system,
      Some(telemetryContext),
      remoteIdentificationHeader,
      settings,
      sdkExecutor,
      connectionPoolSettings)

}
