/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import akka.actor.typed.ActorSystem
import akka.actor.typed.Extension
import akka.actor.typed.ExtensionId
import akka.annotation.InternalApi
import akka.discovery.Discovery
import akka.http.javadsl.model.HttpHeader
import akka.http.javadsl.model.headers.RawHeader
import akka.javasdk.http.HttpClient
import akka.javasdk.http.HttpClientProvider
import akka.javasdk.impl.AkkaSdkSettings
import akka.javasdk.impl.HostAndPort
import akka.javasdk.impl.ProxyInfoHolder
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.{ Context => OtelContext }

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object HttpClientProviderImpl extends ExtensionId[HttpClientProviderImpl] {
  override def createExtension(system: ActorSystem[_]): HttpClientProviderImpl =
    new HttpClientProviderImpl(system, None, ProxyInfoHolder(system), AkkaSdkSettings(system))

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class HttpClientProviderImpl(
    system: ActorSystem[_],
    traceContext: Option[OtelContext],
    proxyInfoHolder: ProxyInfoHolder,
    settings: AkkaSdkSettings)
    extends Extension
    with HttpClientProvider {

  /*  private val MaxCrossServiceResponseContentLength =
    system.settings.config.getBytes("kalix.javasdk.max-content-length").toInt */

  override def httpClientFor(host: String): HttpClient = {
    val (actualHost, actualPort) =
      if (settings.devModeSettings.isDefined && !host.contains('.') && !host.contains(':') && host != "localhost") {
        // dev mode, other service name, use Akka discovery to find it
        // the runtime has set up a mechanism that finds locally running
        // services. Since in dev mode blocking is probably fine for now.
        try {
          val result = Await.result(Discovery(system).discovery.lookup(host, 5.seconds), 5.seconds)
          val address = result.addresses.head
          (address.host, address.port.get) // port is always set
        } catch {
          case NonFatal(ex) =>
            throw new RuntimeException(
              s"Failed to look up service [$host] in dev-mode, make sure that it is also running " +
              "with a separate port and service name correctly defined in its application.conf under 'akka.javasdk.dev-mode.service-name'",
              ex)
        }
      } else {
        HostAndPort.extract(host)
      }

    // FIXME always http? what about https?
    val client: HttpClient = new HttpClient(system, s"http://$actualHost:$actualPort")

    // FIXME fail fast on too large request
    // .filter(ExchangeFilterFunctions.limitResponseSize(MaxCrossServiceResponseContentLength))

    if (defaultHeaders.isEmpty) client
    else client.withDefaultHeaders(defaultHeaders)
  }

  def withTraceContext(traceContext: OtelContext): HttpClientProvider =
    new HttpClientProviderImpl(system, Some(traceContext), proxyInfoHolder, settings)

  private def defaultHeaders = {
    val authHeaders = proxyInfoHolder.remoteIdentificationHeader.map { case (key, value) =>
      RawHeader.create(key, value): HttpHeader
    }

    var otelTraceHeaders = Seq.empty[HttpHeader]
    traceContext.foreach(context =>
      W3CTraceContextPropagator
        .getInstance()
        .inject(
          context,
          null,
          // Note: side-effecting instead of mutable collection
          (_: scala.Any, key: String, value: String) => {
            otelTraceHeaders :+= RawHeader.create(key, value)
          }))

    (otelTraceHeaders ++ Seq(authHeaders).flatten).asJava
  }

}
