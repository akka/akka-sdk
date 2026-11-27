/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util
import java.util.Optional

import akka.http.javadsl.model.HttpHeader
import akka.javasdk.JwtClaims
import akka.javasdk.Principals
import akka.javasdk.SpiffeContext
import akka.javasdk.Tracing
import akka.javasdk.a2a.A2aRequestContext

/** A request context with the values the SDK derives from the call, and no other request data. */
final class TestRequestContext(call: A2aEndpointDescriptorFactory.CallContext) extends A2aRequestContext {
  override def getPrincipals: Principals = throw new UnsupportedOperationException
  override def getJwtClaims: JwtClaims = throw new UnsupportedOperationException
  override def tracing(): Tracing = throw new UnsupportedOperationException
  override def requestHeader(headerName: String): Optional[HttpHeader] = Optional.empty()
  override def allRequestHeaders(): util.List[HttpHeader] = util.List.of()
  override def getSpiffeContext(): Optional[SpiffeContext] = Optional.empty()
  override def callerId(): Optional[String] = Optional.ofNullable(call.callerId.orNull)
  override def contextId(): String = call.contextId
  override def sessionId(): String = call.sessionId
}
