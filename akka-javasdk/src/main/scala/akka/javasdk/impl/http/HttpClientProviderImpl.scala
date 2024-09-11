/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.discovery.Discovery
import akka.http.javadsl.model.HttpHeader
import akka.http.javadsl.model.headers.RawHeader
import akka.javasdk.http.HttpClient
import akka.javasdk.http.HttpClientProvider
import akka.javasdk.impl.ProxyInfoHolder
import akka.javasdk.impl.Settings
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.{ Context => OtelContext }
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class HttpClientProviderImpl(
    system: ActorSystem[_],
    traceContext: Option[OtelContext],
    // FIXME could we have the remote identification header as start context and not need this dynamic fetching of it?
    proxyInfoHolder: ProxyInfoHolder,
    settings: Settings)
    extends HttpClientProvider {

  private val log = LoggerFactory.getLogger(classOf[HttpClientProvider])

  // Lazy because not set until runtime has sent discovery (see  fixme above)
  private lazy val authHeaders = proxyInfoHolder.remoteIdentificationHeader.map { case (key, value) =>
    RawHeader.create(key, value): HttpHeader
  }

  private val otelTraceHeaders: Vector[HttpHeader] = {
    val builder = Vector.newBuilder[HttpHeader]
    traceContext.foreach(context =>
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
    val baseUrl =
      if (nameIsService) {
        if (settings.devModeSettings.isDefined) {
          // dev mode, other service name, use Akka discovery to find it
          // the runtime has set up a mechanism that finds locally running
          // services. Since in dev mode blocking is probably fine for now.
          try {
            val result = Await.result(Discovery(system).discovery.lookup(name, 5.seconds), 5.seconds)
            val address = result.addresses.head
            // port is always set
            val port = address.port.get
            log.debug("Local service resolution found service [{}] at [{}:{}]", name, address.host, port)
            // always http because local
            s"http://${address.host}:$port"
          } catch {
            case NonFatal(ex) =>
              throw new RuntimeException(
                s"Failed to look up service [$name] in dev-mode, make sure that it is also running " +
                "with a separate port and service name correctly defined in its application.conf under 'akka.javasdk.dev-mode.service-name'",
                ex)
          }
        } else {
          // production, request to other service, service mesh manages TLS
          s"http://$name"
        }
      } else {
        // if it isn't a service, we expect it is arbitrary http or https server including the protocol part
        if (!name.startsWith("http://") && !name.startsWith("https://"))
          throw new IllegalArgumentException(
            s"httpClientFor accepts an akka service name or an arbitrary http server prefixed by http:// or https://, got [$name]")
        name
      }

    val client: HttpClient = new HttpClient(system, baseUrl)

    // FIXME fail fast on too large request
    // .filter(ExchangeFilterFunctions.limitResponseSize(MaxCrossServiceResponseContentLength))

    if (nameIsService) {
      // cross service request, include auth
      client.withDefaultHeaders((otelTraceHeaders ++ authHeaders).asJava)
    } else {
      // arbitrary http request
      client.withDefaultHeaders(otelTraceHeaders.asJava)
    }
  }

  def withTraceContext(traceContext: OtelContext): HttpClientProvider =
    new HttpClientProviderImpl(system, Some(traceContext), proxyInfoHolder, settings)

}
