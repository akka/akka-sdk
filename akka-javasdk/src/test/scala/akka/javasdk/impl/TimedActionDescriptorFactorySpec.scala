/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.NotPublicComponents.NotPublicAction
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.testmodels.action.ActionsTestModels.ActionWithOneParam
import akka.javasdk.testmodels.action.ActionsTestModels.ActionWithoutParam
import org.scalatest.wordspec.AnyWordSpec

class TimedActionDescriptorFactorySpec extends AnyWordSpec with ComponentDescriptorSuite {

  "Action descriptor factory" should {

    "validate an Action must be declared as public" in {
      intercept[ValidationException] {
        Validations.validate(classOf[NotPublicAction]).failIfInvalid()
      }.getMessage should include("NotPublicAction is not marked with `public` modifier. Components must be public.")
    }

    "generate mappings for an Action with method without path param" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[ActionWithoutParam], new JsonSerializer)
      val method = desc.commandHandlers("Message")
      method.methodInvokers should have size 1
    }

    "generate mappings for an Action with method with one param" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[ActionWithOneParam], new JsonSerializer)
      val method = desc.commandHandlers("Message")
      method.methodInvokers.get("") should not be empty
    }
  }

}
