/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import java.util
import java.util.Optional

import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.jdk.OptionConverters.RichOption

import akka.annotation.InternalApi
import akka.http.javadsl.model.HttpHeader
import akka.http.javadsl.model.HttpRequest
import akka.javasdk.JwtClaims
import akka.javasdk.Principals
import akka.javasdk.Tracing
import akka.javasdk.http.QueryParams
import akka.javasdk.http.RequestContext
import akka.javasdk.impl.PrincipalsImpl
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.RegionInfo
import akka.stream.Materializer
import io.opentelemetry.api.trace.Tracer

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class HttpRequestContextImpl(
    context: HttpEndpointConstructionContext,
    sdkTracerFactory: () => Tracer,
    regionInfo: RegionInfo)(implicit val materializer: Materializer)
    extends RequestContext {

  // Note: important to not make this publicly accessible to avoid multiple materializations of the entity stream
  def request: HttpRequest = context.httpRequest

  override def getPrincipals: Principals =
    PrincipalsImpl(context.principal.source, context.principal.service)

  override def getJwtClaims: JwtClaims =
    context.jwt match {
      case Some(jwtClaims) => new JwtClaimsImpl(jwtClaims)
      case None =>
        throw new RuntimeException(
          "There are no JWT claims defined but trying accessing the JWT claims. The class or the method needs to be annotated with @JWT.")
    }

  override def requestHeader(headerName: String): Optional[HttpHeader] =
    // Note: force cast to Java header model
    context.requestHeaders.header(headerName).asInstanceOf[Option[HttpHeader]].toJava

  override def allRequestHeaders(): util.List[HttpHeader] =
    // Note: force cast to Java header model
    context.requestHeaders.allHeaders.asInstanceOf[Seq[HttpHeader]].asJava

  override def tracing(): Tracing = new SpanTracingImpl(Option(context.telemetryContext), sdkTracerFactory)

  override def queryParams(): QueryParams = {
    QueryParamsImpl(context.httpRequest.uri.query())
  }

  override def lastSeenSseEventId(): Optional[String] =
    context.requestHeaders.header("Last-Event-ID").map(_.value()).toJava

  override def selfRegion(): String = regionInfo.selfRegion
}
