/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes.Forbidden
import akka.javasdk.annotations.Acl
import akka.javasdk.tooling.validation.Validations
import akka.runtime.sdk.spi.ACL
import akka.runtime.sdk.spi.All
import akka.runtime.sdk.spi.DeploymentInfo
import akka.runtime.sdk.spi.Internet
import akka.runtime.sdk.spi.PrincipalMatcher
import akka.runtime.sdk.spi.ProjectServiceNamePattern
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

  val invalidScopeUsage: String =
    "Invalid annotation usage. Matcher has 'scope' defined without 'service'."

  def validateMatcher(matcher: Acl.Matcher): Unit = {
    val definedCount =
      (if (matcher.principal() != Acl.Principal.UNSPECIFIED) 1 else 0) +
      (if (matcher.service().nonEmpty) 1 else 0) +
      (if (matcher.spiffe().nonEmpty) 1 else 0)
    if (definedCount > 1)
      throw new IllegalArgumentException(invalidAnnotationUsage)
    // Same rule the compile-time/runtime validation framework applies (Validations.isValidSpiffePattern) and that
    // the runtime glob compiler enforces: `**` is only valid as the final token. Checked here too so a pattern that
    // somehow slipped past validation still fails when the service descriptor is built.
    if (matcher.spiffe().nonEmpty && !Validations.isValidSpiffePattern(matcher.spiffe()))
      throw new IllegalArgumentException(
        s"Invalid SPIFFE ACL pattern [${matcher.spiffe()}]: `**` is only allowed as the final token (matches everything below)")
    if (matcher.scope() != Acl.Scope.LOCAL && matcher.service().isEmpty)
      throw new IllegalArgumentException(invalidScopeUsage)
  }

  /** True if the class, or one of its methods, has an @Acl matcher with scope PARENT_PROJECT. */
  def hasParentProjectRule(clazz: Class[_]): Boolean =
    (Option(clazz.getAnnotation(classOf[Acl])) ++ clazz.getMethods.flatMap(m => Option(m.getAnnotation(classOf[Acl]))))
      .exists(acl => (acl.allow() ++ acl.deny()).exists(_.scope() == Acl.Scope.PARENT_PROJECT))

  /**
   * Fails for PARENT_PROJECT rules on a user service, where they never match. Returns a warning when the deployment
   * kind is unknown (dev mode, test kit or standalone), where they never match either.
   */
  def checkParentProjectRules(classes: Seq[Class[_]], deploymentInfo: DeploymentInfo): Option[String] = {
    val withRule = classes.filter(hasParentProjectRule)
    if (withRule.isEmpty) None
    else {
      val names = withRule.map(_.getName).mkString(", ")
      if (deploymentInfo.projectId.nonEmpty && deploymentInfo.platformService.isEmpty)
        throw new IllegalStateException(
          s"@Acl.Matcher with scope PARENT_PROJECT is only valid in a service of a platform service, found in [$names]")
      else if (deploymentInfo.projectId.isEmpty)
        Some(
          s"@Acl.Matcher with scope PARENT_PROJECT never matches in dev mode, the test kit or a standalone deployment, " +
          s"found in [$names]. Disable ACL checks, or add a LOCAL rule with the same service name.")
      else None
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
          else
            m.scope match {
              case Acl.Scope.LOCAL          => new ServiceNamePattern(m.service())
              case Acl.Scope.PARENT_PROJECT => new ProjectServiceNamePattern(m.service())
            }
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
