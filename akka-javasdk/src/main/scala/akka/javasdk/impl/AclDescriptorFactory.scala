/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
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
import akka.runtime.sdk.spi.SpiffePattern
import com.google.rpc.Code

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object AclDescriptorFactory {

  val invalidAnnotationUsage: String =
    "Invalid annotation usage. Matcher must have exactly one of 'principal', 'service' or 'spiffe' defined."

  def validateMatcher(matcher: Acl.Matcher): Unit = {
    val definedCount =
      (if (matcher.principal() != Acl.Principal.UNSPECIFIED) 1 else 0) +
      (if (matcher.service().nonEmpty) 1 else 0) +
      (if (matcher.spiffe().nonEmpty) 1 else 0)
    if (definedCount > 1)
      throw new IllegalArgumentException(invalidAnnotationUsage)
    if (matcher.spiffe().nonEmpty)
      validateSpiffePattern(matcher.spiffe())
  }

  // Mirrors the runtime glob compiler (akka-runtime PrincipalMatcher.compileSegmentGlob): in a SPIFFE ACL glob
  // `*` matches within a single path segment and `**` matches across segments, but `**` is only valid as the final
  // token of the pattern. Reject a non-final `**` (or `***`) here so the mistake surfaces when the service is built
  // rather than only when the runtime compiles the pattern.
  def validateSpiffePattern(pattern: String): Unit = {
    var i = 0
    while (i < pattern.length) {
      if (pattern.charAt(i) == '*' && i + 1 < pattern.length && pattern.charAt(i + 1) == '*') {
        if (i + 2 != pattern.length)
          throw new IllegalArgumentException(
            s"Invalid SPIFFE ACL pattern [$pattern]: `**` is only allowed as the final token (matches everything below)")
        i += 2
      } else i += 1
    }
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
        case Acl.Principal.ALL      => All
        case Acl.Principal.INTERNET => Internet
        case Acl.Principal.UNSPECIFIED =>
          if (m.spiffe().nonEmpty) new SpiffePattern(m.spiffe())
          else new ServiceNamePattern(m.service())
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
