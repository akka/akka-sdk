/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import akka.http.scaladsl.model.HttpMethods
import akka.platform.javasdk.spi.All
import akka.platform.javasdk.spi.Internet
import akka.platform.javasdk.spi.ServiceNamePattern
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpEndpointDescriptorFactorySpec extends AnyWordSpec with Matchers {

  "The HttpEndpointDescriptorFactory" should {
    "parse annotations on an endpoint class into a descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpoint], _ => null)
      val byMethodName = descriptor.methods.map(md => md.userMethod.getName -> md).toMap

      descriptor.mainPath should ===(Some("prefix"))
      descriptor.methods should have size 6
      descriptor.componentOptions shouldBe empty

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

    "parse ACL annotations into descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpointAcls], _ => null)

      descriptor.mainPath should ===(Some("acls"))
      descriptor.methods should have size 3

      descriptor.componentOptions should not be empty
      descriptor.componentOptions.get.acl.deny shouldBe List(All)

      val byMethodName = descriptor.methods.map(md => md.userMethod.getName -> md).toMap

      val get = byMethodName("secret")
      get.methodOptions should not be empty
      get.methodOptions.get.acl.allow shouldBe List(ServiceNamePattern("backoffice-service"))
      get.methodOptions.get.acl.deny shouldBe List(Internet)

      val noAcl = byMethodName("noAcl")
      noAcl.methodOptions shouldBe empty

      val thisAndThat = byMethodName("thisAndThat")
      thisAndThat.methodOptions should not be empty
      thisAndThat.methodOptions.get.acl.allow shouldBe List(ServiceNamePattern("this"), ServiceNamePattern("that"))
      thisAndThat.methodOptions.get.acl.deny shouldBe empty
    }

    "throw error if annotations are not valid" in {
      assertThrows[IllegalArgumentException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpointInvalidAcl], _ => null)
      }
    }
  }

}
