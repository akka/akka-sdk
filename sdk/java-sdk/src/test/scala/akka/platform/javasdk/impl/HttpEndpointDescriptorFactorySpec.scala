/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import akka.http.scaladsl.model.HttpMethods
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpEndpointDescriptorFactorySpec extends AnyWordSpec with Matchers {

  "The HttpEndpointDescriptorFactory" should {
    "parse annotations on an endpoint class into a descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpoint], _ => null)

      descriptor.mainPath should ===(Some("prefix"))
      descriptor.methods should have size 6
      val byMethodName = descriptor.methods.map(md => md.userMethod.getName -> md).toMap

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
  }

}
