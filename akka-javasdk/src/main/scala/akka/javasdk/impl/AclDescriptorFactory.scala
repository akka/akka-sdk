/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
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
  def deriveAclOptions(aclAnnotation: Option[Acl]): Option[ACL] =
    aclAnnotation.map { ann =>
      ann.allow().foreach(matcher => validateMatcher(matcher))
      ann.deny().foreach(matcher => validateMatcher(matcher))

      new ACL(
        allow = Option(ann.allow).map(toPrincipalMatcher).getOrElse(Nil),
        deny = Option(ann.deny).map(toPrincipalMatcher).getOrElse(Nil),
        denyHttpCode = None, // FIXME we can probably use http codes instead of grpc ones
        denyGrpcCode = Option(Code.forNumber(ann.denyCode().value)))
    }

  private def toPrincipalMatcher(matchers: Array[Acl.Matcher]): List[PrincipalMatcher] =
    matchers.map { m =>
      m.principal match {
        case Acl.Principal.ALL         => All
        case Acl.Principal.INTERNET    => Internet
        case Acl.Principal.UNSPECIFIED => new ServiceNamePattern(m.service())
      }
    }.toList

}
