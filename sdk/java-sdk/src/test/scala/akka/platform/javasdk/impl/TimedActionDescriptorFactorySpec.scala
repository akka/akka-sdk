/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import akka.platform.javasdk.impl.NotPublicComponents.NotPublicAction
import akka.platform.javasdk.impl.ProtoDescriptorGenerator.fileDescriptorName
import akka.platform.spring.testmodels.action.ActionsTestModels.ActionWithOneParam
import akka.platform.spring.testmodels.action.ActionsTestModels.ActionWithoutParam
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import org.scalatest.wordspec.AnyWordSpec

class TimedActionDescriptorFactorySpec extends AnyWordSpec with ComponentDescriptorSuite {

  "Action descriptor factory" should {

    "validate an Action must be declared as public" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[NotPublicAction]).failIfInvalid
      }.getMessage should include("NotPublicAction is not marked with `public` modifier. Components must be public.")
    }

    "generate mappings for an Action with method without path param" in {
      assertDescriptor[ActionWithoutParam] { desc =>

        val clazz = classOf[ActionWithoutParam]
        desc.fileDescriptor.getName shouldBe fileDescriptorName(clazz.getPackageName, clazz.getSimpleName)

        val methodDescriptor = desc.serviceDescriptor.findMethodByName("Message")
        methodDescriptor.isServerStreaming shouldBe false
        methodDescriptor.isClientStreaming shouldBe false

        val method = desc.commandHandlers("Message")
        method.requestMessageDescriptor.getFields.size() shouldBe 0
      }
    }

    "generate mappings for an Action with method with one param" in {
      assertDescriptor[ActionWithOneParam] { desc =>

        val methodDescriptor = desc.serviceDescriptor.findMethodByName("Message")
        methodDescriptor.isServerStreaming shouldBe false
        methodDescriptor.isClientStreaming shouldBe false

        val method = desc.commandHandlers("Message")
        assertRequestFieldJavaType(method, "json_body", JavaType.MESSAGE)
      }
    }
  }

}
