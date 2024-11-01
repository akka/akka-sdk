/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.http.scaladsl.model.HttpMethods
import akka.javasdk.impl.reflection.Reflect
import AclDescriptorFactory.validateMatcher
import akka.annotation.InternalApi
import akka.http.scaladsl.model.Uri.Path
import akka.javasdk.annotations.Acl
import akka.javasdk.annotations.http.Delete
import akka.javasdk.annotations.http.Get
import akka.javasdk.annotations.http.HttpEndpoint
import akka.javasdk.annotations.http.Patch
import akka.javasdk.annotations.http.Post
import akka.javasdk.annotations.http.Put
import akka.javasdk.annotations.JWT
import akka.javasdk.annotations.JWT.JwtMethodMode
import akka.javasdk.impl.Validations.Invalid
import akka.javasdk.impl.Validations.Validation
import akka.runtime.sdk.spi.ACL
import akka.runtime.sdk.spi.{ JWT => RuntimeJWT }
import akka.runtime.sdk.spi.All
import akka.runtime.sdk.spi.ClaimPattern
import akka.runtime.sdk.spi.ClaimValues
import akka.runtime.sdk.spi.ComponentOptions
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpEndpointDescriptor
import akka.runtime.sdk.spi.HttpEndpointMethodDescriptor
import akka.runtime.sdk.spi.Internet
import akka.runtime.sdk.spi.MethodOptions
import akka.runtime.sdk.spi.PrincipalMatcher
import akka.runtime.sdk.spi.ServiceNamePattern
import akka.runtime.sdk.spi.StaticClaim
import akka.runtime.sdk.spi.StaticClaimContent
import org.slf4j.LoggerFactory

import java.lang.reflect.Method
import scala.annotation.tailrec
import scala.util.matching.Regex
import scala.util.Success
import scala.util.Failure
import scala.util.Try

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object HttpEndpointDescriptorFactory {

  val logger = LoggerFactory.getLogger(classOf[HttpEndpointDescriptorFactory.type])

  def apply(
      endpointClass: Class[_],
      instanceFactory: HttpEndpointConstructionContext => Any): HttpEndpointDescriptor = {
    assert(Reflect.isRestEndpoint(endpointClass))

    val mainPath = Option(endpointClass.getAnnotation(classOf[HttpEndpoint])).map(_.value()).filterNot(_.isEmpty)

    val methodsWithValidation: Seq[Either[Validation, HttpEndpointMethodDescriptor]] =
      endpointClass.getDeclaredMethods.toVector.flatMap { method =>
        val maybePathMethod = if (method.getAnnotation(classOf[Get]) != null) {
          val path = method.getAnnotation(classOf[Get]).value()
          Some((path, HttpMethods.GET))
        } else if (method.getAnnotation(classOf[Post]) != null) {
          val path = method.getAnnotation(classOf[Post]).value()
          Some((path, HttpMethods.POST))
        } else if (method.getAnnotation(classOf[Put]) != null) {
          val path = method.getAnnotation(classOf[Put]).value()
          Some((path, HttpMethods.PUT))
        } else if (method.getAnnotation(classOf[Patch]) != null) {
          val path = method.getAnnotation(classOf[Patch]).value()
          Some((path, HttpMethods.PATCH))
        } else if (method.getAnnotation(classOf[Delete]) != null) {
          val path = method.getAnnotation(classOf[Delete]).value()
          Some((path, HttpMethods.DELETE))
        } else {
          // non HTTP-available user method
          None
        }

        maybePathMethod.map { case (path, httpMethod) =>
          val fullPathExpression = mainPath.map(m => m + path).getOrElse(path)
          val parsedPath = Path(fullPathExpression)

          @tailrec
          def validatePath(pathLeft: Path, parameterNames: List[String]): Validation = pathLeft match {
            case Path.Segment(segment, rest) if segment.startsWith("{") =>
              // path variable, match against parameter name
              val name = segment.drop(1).dropRight(1)
              if (parameterNames.isEmpty)
                Validations.Invalid(
                  s"There are more parameters in the path expression [$fullPathExpression] than there are parameters for [${endpointClass.getName}.${method.getName}]")
              else {
                val nextParamName = parameterNames.head
                if (name != nextParamName)
                  Validations.Invalid(
                    s"The parameter [$name] in the path expression [$fullPathExpression] does not match the method parameter name [$nextParamName] for [${endpointClass.getName}.${method.getName}]. " +
                    "The parameter names in the expression must match the parameters of the method.")
                else
                  validatePath(rest, parameterNames.tail)
              }
            case Path.Segment(_, rest) =>
              // non variable segment
              validatePath(rest, parameterNames)
            case Path.Slash(rest) =>
              validatePath(rest, parameterNames)
            case Path.Empty =>
              // end of expression, either no more params or one more param for the request body
              if (parameterNames.length > 1)
                Validations.Invalid(s"There are [${parameterNames.size}] parameters ([${parameterNames.mkString(
                  ",")}]) for endpoint method [${endpointClass.getName}.${method.getName}] not matched by the path expression. " +
                "The parameter count and names should match the expression, with one additional possible parameter in the end for the request body.")
              else Validations.Valid
          }

          validatePath(parsedPath, method.getParameters.toList.map(_.getName)) match {
            case Validations.Valid =>
              Right(
                new HttpEndpointMethodDescriptor(
                  httpMethod = httpMethod,
                  pathExpression = path,
                  userMethod = method,
                  methodOptions = new MethodOptions(
                    deriveAclOptions(Option(method.getAnnotation(classOf[Acl]))),
                    deriveJWTOptions(
                      Option(method.getAnnotation(classOf[JWT])),
                      endpointClass.getCanonicalName,
                      Some(method)))))
            case invalid => Left(invalid)
          }
        }
      }

    val (errors, methods) = methodsWithValidation.partitionMap(identity)

    if (errors.nonEmpty) {
      val summedUp = errors
        .foldLeft(Validations.Valid: Validation)((validation, invalid) => validation ++ invalid)
        .asInstanceOf[Invalid]
      summedUp.throwFailureSummary()
    } else {
      new HttpEndpointDescriptor(
        mainPath = mainPath,
        instanceFactory = instanceFactory,
        methods = methods.toVector,
        componentOptions = new ComponentOptions(
          deriveAclOptions(Option(endpointClass.getAnnotation(classOf[Acl]))),
          deriveJWTOptions(Option(endpointClass.getAnnotation(classOf[JWT])), endpointClass.getCanonicalName)))
    }
  }

  // receives the method, checks if it is annotated with @Acl and if so,
  // converts that into ACL spi object
  private def deriveAclOptions(aclAnnotation: Option[Acl]): Option[ACL] =
    aclAnnotation.map { ann =>
      ann.allow().foreach(matcher => validateMatcher(matcher))
      ann.deny().foreach(matcher => validateMatcher(matcher))

      new ACL(
        allow = Option(ann.allow).map(toPrincipalMatcher).getOrElse(Nil),
        deny = Option(ann.deny).map(toPrincipalMatcher).getOrElse(Nil),
        denyHttpCode = None // FIXME we can probably use http codes instead of grpc ones
      )
    }

  private[impl] def extractEnvVars(claimValueContent: String, claimRef: String): String = {
    val pattern = """\$\{([A-Z_][A-Z0-9_]*)\}""".r

    pattern.replaceAllIn(
      claimValueContent,
      matched => {
        val varName = matched.group(1)
        Try(sys.env(varName)) match {
          case Success(varValue) => varValue
          case Failure(ex) =>
            throw new IllegalArgumentException(
              s"[${ex.getMessage}] env var is missing but it is used in claim [$claimValueContent] in [$claimRef].")
        }
      })
  }

  private def deriveJWTOptions(
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

  private def toPrincipalMatcher(matchers: Array[Acl.Matcher]): List[PrincipalMatcher] =
    matchers.map { m =>
      m.principal match {
        case Acl.Principal.ALL         => All
        case Acl.Principal.INTERNET    => Internet
        case Acl.Principal.UNSPECIFIED => new ServiceNamePattern(m.service())
      }
    }.toList

}
