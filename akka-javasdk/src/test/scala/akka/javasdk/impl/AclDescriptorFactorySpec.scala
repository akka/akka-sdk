/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.annotations.Acl
import akka.javasdk.impl.http.TestEndpoints
import akka.runtime.sdk.spi.DeploymentInfo
import akka.runtime.sdk.spi.ProjectServiceNamePattern
import com.google.rpc.Code
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AclDescriptorFactorySpec extends AnyWordSpec with Matchers {

  private val withRule: Seq[Class[_]] = Seq(classOf[TestEndpoints.TestEndpointParentProjectAcls])
  private val withoutRule: Seq[Class[_]] = Seq(classOf[TestEndpoints.TestEndpointInvalidAclSpiffePattern])

  private val userService = new DeploymentInfo("abc", "abc", None)
  private val platformService = new DeploymentInfo("abc", "abc-my-platform-service", Some("my-platform-service"))
  private val local = new DeploymentInfo("", "", None)

  "AclDescriptorFactory" should {

    "derive PARENT_PROJECT rules for a gRPC endpoint" in {
      val acl = AclDescriptorFactory
        .deriveAclOptions(
          Option(classOf[TestEndpoints.TestEndpointParentProjectAcls].getAnnotation(classOf[Acl])),
          isGrpc = true)
        .get
      acl.allow.collect { case p: ProjectServiceNamePattern => p.pattern } shouldBe Seq("parent-service")
      acl.denyGrpcCode shouldBe Some(Code.PERMISSION_DENIED)
    }

    "find PARENT_PROJECT rules at class and method level" in {
      AclDescriptorFactory.hasParentProjectRule(classOf[TestEndpoints.TestEndpointParentProjectAcls]) shouldBe true
      AclDescriptorFactory.hasParentProjectRule(classOf[TestEndpoints.TestEndpointAcls]) shouldBe true
      AclDescriptorFactory.hasParentProjectRule(
        classOf[TestEndpoints.TestEndpointInvalidAclSpiffePattern]) shouldBe false
    }

    "fail for PARENT_PROJECT rules in a deployed user service" in {
      intercept[IllegalStateException] {
        AclDescriptorFactory.checkParentProjectRules(withRule, userService)
      }.getMessage should include(classOf[TestEndpoints.TestEndpointParentProjectAcls].getName)
    }

    "accept PARENT_PROJECT rules in a service of a platform service" in {
      AclDescriptorFactory.checkParentProjectRules(withRule, platformService) shouldBe None
    }

    "warn about PARENT_PROJECT rules without a project id" in {
      AclDescriptorFactory.checkParentProjectRules(withRule, local).get should include("never matches")
    }

    "accept a service without PARENT_PROJECT rules" in {
      AclDescriptorFactory.checkParentProjectRules(withoutRule, userService) shouldBe None
      AclDescriptorFactory.checkParentProjectRules(withoutRule, local) shouldBe None
    }
  }
}
