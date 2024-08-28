/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.annotation.InternalApi
import akka.http.javadsl.model.HttpHeader
import akka.http.javadsl.model.headers.RawHeader
import akka.javasdk.http.HttpClient
import akka.javasdk.http.HttpClientProvider
import akka.javasdk.impl.DevModeSettings
import akka.javasdk.impl.HostAndPort
import akka.javasdk.impl.ProxyInfoHolder
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.{ Context => OtelContext }

import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object HttpClientProviderImpl extends ExtensionId[HttpClientProviderImpl] with ExtensionIdProvider {
  override def get(system: ActorSystem): HttpClientProviderImpl = super.get(system)

  override def get(system: ClassicActorSystemProvider): HttpClientProviderImpl = super.get(system)

  override def createExtension(system: ExtendedActorSystem): HttpClientProviderImpl =
    new HttpClientProviderImpl(system)
  override def lookup: ExtensionId[_ <: Extension] = this

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] class HttpClientProviderImpl(system: ExtendedActorSystem, traceContext: Option[OtelContext])
    extends Extension
    with HttpClientProvider {

  def this(system: ExtendedActorSystem) = this(system, None)

  private val proxyInfoHolder = ProxyInfoHolder(system)

  private val devModeSettings = DevModeSettings.fromConfig(system.settings.config).portMappings

  /*  private val MaxCrossServiceResponseContentLength =
    system.settings.config.getBytes("kalix.cross-service.max-content-length").toInt */

  override def httpClientFor(host: String): HttpClient = {
    // read the dev-mode settings directly and use it to override the host and port
    val (mappedHost, mappedPort) =
      devModeSettings
        .get(host)
        .map(HostAndPort.extract)
        .getOrElse((host, 80))
    val client: HttpClient = new HttpClient(system, s"http://$mappedHost:$mappedPort")

    // FIXME fail fast on too large request
    // .filter(ExchangeFilterFunctions.limitResponseSize(MaxCrossServiceResponseContentLength))

    if (defaultHeaders.isEmpty) client
    else client.withDefaultHeaders(defaultHeaders)
  }

  def withTraceContext(traceContext: OtelContext): HttpClientProvider =
    new HttpClientProviderImpl(system, Some(traceContext))

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
