/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import java.util.Optional

import akka.javasdk.evaluation.Subject
import akka.runtime.sdk.spi.SpiEvaluator
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EvaluationConversionsSpec extends AnyWordSpec with Matchers {

  "EvaluationConversions" should {

    "convert an interaction subject both ways" in {
      val subject =
        EvaluationConversions.toSdkSubject(
          new SpiEvaluator.Interaction("interaction-1", "support-agent", Some("flow-1")))

      subject shouldBe new Subject.Interaction("interaction-1", "support-agent", Optional.of("flow-1"))

      val spiSubject = EvaluationConversions.toSpiSubject(subject).asInstanceOf[SpiEvaluator.Interaction]
      spiSubject.interactionId shouldBe "interaction-1"
      spiSubject.agentComponentId shouldBe "support-agent"
      spiSubject.flowId shouldBe Some("flow-1")
    }

    "convert a workflow subject both ways" in {
      val subject = EvaluationConversions.toSdkSubject(new SpiEvaluator.Workflow("workflow-1", "order-workflow"))

      subject shouldBe new Subject.Workflow("workflow-1", "order-workflow")

      val spiSubject = EvaluationConversions.toSpiSubject(subject).asInstanceOf[SpiEvaluator.Workflow]
      spiSubject.workflowId shouldBe "workflow-1"
      spiSubject.workflowComponentId shouldBe "order-workflow"
    }
  }
}
