/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import akka.javasdk.testmodels.workflow.WorkflowTestModels
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WorkflowDescriptorSpec extends AnyWordSpec with OptionValues with Matchers {

  "Workflow descriptor" should {
    "find step by method name" in {
      val workflow = new WorkflowTestModels.TransferWorkflow()
      val descriptor = new WorkflowDescriptor(workflow)

      descriptor.findStepMethodByName("depositStep") shouldBe defined
    }

    "find steps by name defined in annotation" in {
      val workflow = new WorkflowTestModels.TransferWorkflow()
      val descriptor = new WorkflowDescriptor(workflow)

      descriptor.findStepMethodByName("withdraw") shouldBe defined
    }
  }
}
