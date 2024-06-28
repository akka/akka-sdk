/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.http

import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.annotation.InternalApi
import akka.http.javadsl.model.headers.RawHeader
import kalix.devtools.impl.DevModeSettings
import kalix.devtools.impl.HostAndPort
import kalix.javasdk.http.HttpClient
import kalix.javasdk.http.HttpClientProvider
import kalix.javasdk.impl.ProxyInfoHolder

import java.util
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * INTERNAL API
 */
@InternalApi
private[kalix] object HttpClientProviderExtension
    extends ExtensionId[HttpClientProviderExtension]
    with ExtensionIdProvider {
  override def get(system: ActorSystem): HttpClientProviderExtension = super.get(system)

  override def get(system: ClassicActorSystemProvider): HttpClientProviderExtension = super.get(system)

  override def createExtension(system: ExtendedActorSystem): HttpClientProviderExtension =
    new HttpClientProviderExtension(system)
  override def lookup: ExtensionId[_ <: Extension] = this

}

/**
 * INTERNAL API
 */
@InternalApi
private[kalix] class HttpClientProviderExtension(system: ExtendedActorSystem)
    extends Extension
    with HttpClientProvider {

  private val proxyInfoHolder = ProxyInfoHolder(system)
  private val clients: ConcurrentMap[String, HttpClient] = new ConcurrentHashMap()

  private val devModeSettings = DevModeSettings.fromConfig(system.settings.config).portMappings

  /*  private val MaxCrossServiceResponseContentLength =
    system.settings.config.getBytes("kalix.cross-service.max-content-length").toInt */

  override def httpClientFor(host: String): HttpClient = {
    // FIXME what about propagating trace parent?

    // differently from the gRPC client, we don't need to create an extra config on the fly
    // we can read the dev-mode settings directly and use it to override the host and port
    val (mappedHost, mappedPort) =
      devModeSettings
        .get(host)
        .map(HostAndPort.extract)
        .getOrElse((host, 80))

    clients.computeIfAbsent(
      host,
      _ => {
        val remoteAddHeader = proxyInfoHolder.remoteIdentificationHeader
        buildClient(mappedHost, mappedPort, remoteAddHeader)
      })
  }

  /* FIXME when was this used?
  val localWebClient: HttpClient = {
    val localAddHeader = proxyInfoHolder.localIdentificationHeader
    val clientOpt =
      for {
        host <- proxyInfoHolder.proxyHostname
        port <- proxyInfoHolder.proxyPort
      } yield buildClient(host, port, localAddHeader)

    clientOpt.getOrElse {
      throw new IllegalStateException(
        "Service proxy hostname and/or port are not set by proxy at discovery, too old proxy version?")
    }
  }
   */

  private def buildClient(host: String, port: Int, identificationHeader: Option[(String, String)]) = {

    val client: HttpClient = new HttpClient(system, s"http://$host:$port")

    // FIXME fail fast on too large request
    // .filter(ExchangeFilterFunctions.limitResponseSize(MaxCrossServiceResponseContentLength))

    identificationHeader match {
      case Some((key, value)) => client.withDefaultHeaders(util.Arrays.asList(RawHeader.create(key, value)))
      case None               => client
    }
  }
}
