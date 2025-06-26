/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.annotations.JWT
import akka.javasdk.annotations.JWT.JwtMethodMode
import akka.javasdk.impl.HttpEndpointDescriptorFactory.extractEnvVars
import akka.javasdk.impl.reflection.Reflect.Syntax.AnnotatedElementOps
import akka.javasdk.impl.reflection.Reflect.Syntax.MethodOps
import akka.runtime.sdk.spi.ClaimPattern
import akka.runtime.sdk.spi.ClaimValues
import akka.runtime.sdk.spi.StaticClaim
import akka.runtime.sdk.spi.StaticClaimContent
import akka.runtime.sdk.spi.{ JWT => RuntimeJWT }

import java.lang.reflect.Method
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.matching.Regex

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object JwtDescriptorFactory {

  def hasJwt(javaMethod: Method): Boolean =
    javaMethod.isPublic && javaMethod.hasAnnotation[JWT]

  def deriveJWTOptions(
      jwtAnnotation: Option[JWT],
      className: String,
      method: Option[Method] = None): Option[RuntimeJWT] = {
    //Validates the a.j.a.JWT.StaticClaim and creates a.r.s.spi.StaticClaim out of it
    def createStaticClaim(staticClaim: JWT.StaticClaim): Option[StaticClaim] = {
      val culprit = method.getOrElse(className).toString
      val content: Option[StaticClaimContent] = (staticClaim.values(), staticClaim.pattern) match {
        case (value, pattern) if value.nonEmpty && pattern.nonEmpty =>
          throw new IllegalArgumentException(
            s"Claim in $culprit must have a content at most for one: `value` or `pattern`. This claim has both.")
        case (values, _) if values.nonEmpty =>
          val staticClaimValues = values.map(v => extractEnvVars(v, culprit))
          Some(new ClaimValues(staticClaimValues.toSet))
        case (_, pattern) if pattern.nonEmpty =>
          Try(new Regex(pattern)) match {
            case Success(_) => Some(new ClaimPattern(staticClaim.pattern()))
            case Failure(ex) =>
              throw new IllegalArgumentException(s"Claim in $culprit has an invalid `pattern`.", ex)
          }
        case _ =>
          throw new IllegalArgumentException(
            s"Claim in $culprit must have a content at least for one: `value` or `pattern`.")
      }
      content.map(new StaticClaim(staticClaim.claim(), _))
    }

    jwtAnnotation.map { ann =>
      val spiStaticClaims = ann.staticClaims().flatMap(createStaticClaim)
      new RuntimeJWT(
        validate = ann.validate().contains(JwtMethodMode.BEARER_TOKEN),
        bearerTokenIssuers = ann.bearerTokenIssuers().toSeq,
        claims = spiStaticClaims.toSet)
    }
  }
}
