/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.testmodels.action.ActionsTestModels.ActionWithOneParam
import akka.javasdk.testmodels.action.ActionsTestModels.ActionWithoutParam
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TimedActionDescriptorFactorySpec extends AnyWordSpec with Matchers {

  "Action descriptor factory" should {

    "generate mappings for an Action with method without path param" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[ActionWithoutParam], new JsonSerializer)
      desc.methodInvokers should have size 1
    }

    "generate mappings for an Action with method with one param" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[ActionWithOneParam], new JsonSerializer)
      desc.methodInvokers.get("Message") should not be empty
    }
  }

}
