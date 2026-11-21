/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.a2a.A2aEndpointSettings
import akka.javasdk.impl.a2a.A2aPushDeliveryWorkflow
import akka.javasdk.impl.a2a.A2aPushUrlPolicy
import akka.javasdk.impl.a2a.A2aRemoteTaskPollWorkflow
import akka.javasdk.impl.a2a.A2aRemoteTasks
import akka.javasdk.impl.a2a.A2aShortTaskWorkflow
import akka.javasdk.impl.a2a.RemoteA2aTaskWorkflow
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflow
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SdkOnlyDependenciesSpec extends AnyWordSpec with Matchers {

  "The internal A2A dependencies of a workflow" should {

    "be injected into the SDK's own workflows" in {
      SdkRunner.injectsSdkOnlyDependency(classOf[A2aPushDeliveryWorkflow], classOf[A2aPushUrlPolicy]) shouldBe true
      SdkRunner.injectsSdkOnlyDependency(classOf[A2aRemoteTaskPollWorkflow], classOf[A2aRemoteTasks]) shouldBe true
      SdkRunner.injectsSdkOnlyDependency(classOf[RemoteA2aTaskWorkflow], classOf[A2aRemoteTasks]) shouldBe true
      SdkRunner.injectsSdkOnlyDependency(classOf[A2aShortTaskWorkflow], classOf[A2aEndpointSettings]) shouldBe true
    }

    "not be injected into a workflow of the service" in {
      SdkRunner.sdkOnlyWorkflowDependencies.foreach { dependency =>
        SdkRunner.injectsSdkOnlyDependency(classOf[TransferWorkflow], dependency) shouldBe false
      }
    }
  }
}
