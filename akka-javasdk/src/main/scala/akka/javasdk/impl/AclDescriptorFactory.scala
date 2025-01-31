/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes.Forbidden
import akka.javasdk.annotations.Acl
import akka.runtime.sdk.spi.ACL
import akka.runtime.sdk.spi.All
import akka.runtime.sdk.spi.Internet
import akka.runtime.sdk.spi.PrincipalMatcher
import akka.runtime.sdk.spi.ServiceNamePattern
import com.google.rpc.Code

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object AclDescriptorFactory {

  val invalidAnnotationUsage: String =
    "Invalid annotation usage. Matcher has both 'principal' and 'service' defined. " +
    "Only one is allowed."

  def validateMatcher(matcher: Acl.Matcher): Unit = {
    if (matcher.principal() != Acl.Principal.UNSPECIFIED && matcher.service().nonEmpty)
      throw new IllegalArgumentException(invalidAnnotationUsage)
  }

  // receives the method, checks if it is annotated with @Acl and if so,
  // converts that into ACL spi object
  def deriveAclOptions(aclAnnotation: Option[Acl], isGrpc: Boolean = false): Option[ACL] =
    aclAnnotation.map { ann =>
      ann.allow().foreach(matcher => validateMatcher(matcher))
      ann.deny().foreach(matcher => validateMatcher(matcher))

      new ACL(
        allow = Option(ann.allow).map(toPrincipalMatcher).getOrElse(Nil),
        deny = Option(ann.deny).map(toPrincipalMatcher).getOrElse(Nil),
        denyHttpCode = if (isGrpc) None else deriveHttpCode(ann.denyCode()),
        denyGrpcCode = if (isGrpc) deriveGrpcCode(ann.denyCode()) else None)
    }

  private def toPrincipalMatcher(matchers: Array[Acl.Matcher]): List[PrincipalMatcher] =
    matchers.map { m =>
      m.principal match {
        case Acl.Principal.ALL         => All
        case Acl.Principal.INTERNET    => Internet
        case Acl.Principal.UNSPECIFIED => new ServiceNamePattern(m.service())
      }
    }.toList

  private val denyCodeUndefined = -1
  private def deriveHttpCode(code: Integer): Some[StatusCode] = try {
    if (code == denyCodeUndefined) Some(Forbidden)
    else Some(StatusCode.int2StatusCode(code))
  } catch {
    case _: RuntimeException => throw new IllegalArgumentException(s"Invalid HTTP status code: $code")
  }

  private def deriveGrpcCode(code: Integer): Option[Code] = {
    val parsedCode =
      if (code == denyCodeUndefined) Some(Code.PERMISSION_DENIED)
      else Option(Code.forNumber(code))

    if (parsedCode.isEmpty)
      throw new IllegalArgumentException(s"Invalid gRPC status code: $code")
    else
      parsedCode
  }
}
