/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.StatusCodes.Forbidden
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.model.StatusCodes.Unauthorized
import akka.javasdk.impl.http.TestEndpoints.TestEndpointJwtClassAndMethodLevel
import akka.javasdk.impl.http.TestEndpoints.TestEndpointJwtOnlyMethodLevel
import akka.runtime.sdk.spi.All
import akka.runtime.sdk.spi.ClaimPattern
import akka.runtime.sdk.spi.ClaimValues
import akka.runtime.sdk.spi.Internet
import akka.runtime.sdk.spi.ServiceNamePattern
import akka.runtime.sdk.spi.StaticClaim
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpEndpointDescriptorFactorySpec extends AnyWordSpec with Matchers {

  "The HttpEndpointDescriptorFactory" should {
    "parse annotations on an endpoint class into a descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpoint], _ => null)
      val byMethodName = descriptor.methods.map(md => md.userMethod.getName -> md).toMap

      descriptor.mainPath should ===(Some("prefix"))
      descriptor.methods should have size 6
      descriptor.componentOptions.aclOpt shouldBe empty
      descriptor.componentOptions.jwtOpt shouldBe empty

      val list = byMethodName("list")
      list.pathExpression should ===("/")
      list.httpMethod should ===(HttpMethods.GET)

      val get = byMethodName("get")
      get.pathExpression should ===("/{it}")
      get.httpMethod should ===(HttpMethods.GET)

      val delete = byMethodName("delete")
      delete.pathExpression should ===("/{it}")
      delete.httpMethod should ===(HttpMethods.DELETE)

      val update = byMethodName("update")
      update.pathExpression should ===("/{it}")
      update.httpMethod should ===(HttpMethods.PUT)

      val patch = byMethodName("patch")
      patch.pathExpression should ===("/{it}")
      patch.httpMethod should ===(HttpMethods.PATCH)
    }

    "fail when path expression does not match parameters" in {
      val message = intercept[ValidationException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.InvalidEndpointMethods], _ => null)
      }.getMessage

      message should include(
        "There are more parameters in the path expression [/{id}/my-endpoint/] than there are parameters for [akka.javasdk.impl.http.TestEndpoints$InvalidEndpointMethods.list1]")
      message should include(
        "The parameter [id] in the path expression [/{id}/my-endpoint/] does not match the method parameter name [bob] for [akka.javasdk.impl.http.TestEndpoints$InvalidEndpointMethods.list2]")
      message should include(
        "The parameter [bob] in the path expression [/{id}/my-endpoint/something/{bob}] does not match the method parameter name [value] for [akka.javasdk.impl.http.TestEndpoints$InvalidEndpointMethods.list3]")
      message should include(
        "There are [2] parameters ([value,body]) for endpoint method [akka.javasdk.impl.http.TestEndpoints$InvalidEndpointMethods.list5] not matched by the path expression")
    }

    "parse ACL annotations into descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpointAcls], _ => null)

      descriptor.mainPath should ===(Some("acls"))
      descriptor.methods should have size 3

      descriptor.componentOptions.aclOpt should not be empty
      descriptor.componentOptions.aclOpt.get.deny shouldBe List(All)
      descriptor.componentOptions.aclOpt.get.denyHttpCode should contain(NotFound)

      val byMethodName = descriptor.methods.map(md => md.userMethod.getName -> md).toMap

      val get = byMethodName("secret")
      get.methodOptions.acl should not be empty
      get.methodOptions.acl.get.allow.collect { case p: ServiceNamePattern => p.pattern } shouldEqual Seq(
        "backoffice-service")
      get.methodOptions.acl.get.deny shouldBe List(Internet)
      get.methodOptions.acl.get.denyHttpCode should contain(Unauthorized)

      val noAcl = byMethodName("noAcl")
      noAcl.methodOptions.acl shouldBe empty

      val thisAndThat = byMethodName("thisAndThat")
      thisAndThat.methodOptions.acl should not be empty
      thisAndThat.methodOptions.acl.get.allow.collect { case p: ServiceNamePattern => p.pattern } shouldBe Seq(
        "this",
        "that")
      thisAndThat.methodOptions.acl.get.deny shouldBe empty
      thisAndThat.methodOptions.acl.get.denyHttpCode should contain(Forbidden)
    }

    "throw error if annotations are not valid" in {
      assertThrows[IllegalArgumentException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpointInvalidAcl], _ => null)
      }

      val caught = intercept[IllegalArgumentException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpointInvalidAclDenyCode], _ => null)
      }
      caught.getMessage should include("Invalid HTTP status code: 123123")
    }

    //Utility to compare StaticClaim to avoid creating `equals` in the original.
    implicit class ClaimValuesWrapper(staticClaim: StaticClaim) {
      override def equals(obj: Any): Boolean = obj match {
        case sc: StaticClaim if sc.name == staticClaim.name =>
          (sc.content, staticClaim.content) match {
            case (cv0: ClaimValues, cv1: ClaimValues)   => cv0.content == cv1.content
            case (cp0: ClaimPattern, cp1: ClaimPattern) => cp0.content == cp1.content
            case _                                      => false
          }
        case _ => false
      }
      def toWrapper: ClaimValuesWrapper = this
    }

    "parse JWT class level annotations into the descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpointJwtClassLevel], _ => null)
      val jwtComponentOptions = descriptor.componentOptions.jwtOpt.get

      jwtComponentOptions.validate shouldBe true
      jwtComponentOptions.bearerTokenIssuers shouldBe List("a", "b")
      (jwtComponentOptions.claims.map(_.toWrapper).toList should contain).allOf(
        new StaticClaim("aud", new ClaimValues(Set("value1.kalix.io"))),
        new StaticClaim("roles", new ClaimValues(Set("viewer", "editor"))),
        new StaticClaim("sub", new ClaimPattern("^sub-\\S+$")))

      descriptor.methods.head.methodOptions.jwtOpt shouldBe None
    }

    "parse JWT class and method level annotations into the descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[TestEndpointJwtClassAndMethodLevel], _ => null)
      val jwtComponentOptions = descriptor.componentOptions.jwtOpt.get

      jwtComponentOptions.validate shouldBe true
      jwtComponentOptions.bearerTokenIssuers shouldBe List("a", "b")
      (jwtComponentOptions.claims.map(_.toWrapper).toList should contain).allOf(
        new StaticClaim("aud", new ClaimValues(Set("value1.kalix.value2.io"))),
        new StaticClaim("roles", new ClaimValues(Set("viewer", "editor"))),
        new StaticClaim("sub", new ClaimPattern("^sub-\\S+$")))

      val jwtMethodOptions = descriptor.methods.head.methodOptions.jwtOpt.get
      jwtMethodOptions.validate shouldBe true
      jwtMethodOptions.bearerTokenIssuers shouldBe List("c", "d")
      (jwtMethodOptions.claims.map(_.toWrapper).toList should contain).allOf(
        new StaticClaim("aud", new ClaimValues(Set("value1.kalix.dev"))),
        new StaticClaim("roles", new ClaimValues(Set("admin"))),
        new StaticClaim("sub", new ClaimPattern("^-\\S+$")))
    }

    "parse JWT only method level annotations into the descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[TestEndpointJwtOnlyMethodLevel], _ => null)
      val jwtComponentOptions = descriptor.componentOptions.jwtOpt

      jwtComponentOptions shouldBe None

      val jwtMethodOptions = descriptor.methods.head.methodOptions.jwtOpt.get
      jwtMethodOptions.validate shouldBe true
      jwtMethodOptions.bearerTokenIssuers shouldBe List("c", "d")

      (jwtMethodOptions.claims.map(_.toWrapper).toList should contain).allOf(
        new StaticClaim("aud", new ClaimValues(Set("value1.kalix.dev"))),
        new StaticClaim("roles", new ClaimValues(Set("admin"))),
        new StaticClaim("sub", new ClaimPattern("^-\\S+$")))
    }

    "complain if an ENV is missing in component level" in {
      val exception = intercept[IllegalArgumentException] {
        val valueClaimContent = "one-${ENV}-two-${ENV3}-three"
        HttpEndpointDescriptorFactory.extractEnvVars(
          valueClaimContent,
          "origin-ref") shouldBe "one-value1-two-value1-three"
      }
      exception.getMessage shouldBe "[ENV3] env var is missing but it is used in claim [one-${ENV}-two-${ENV3}-three] in [origin-ref]."
    }
  }
}
