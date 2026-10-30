/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.util

import scala.concurrent.Future

import akka.javasdk.evaluation.Subject
import akka.runtime.sdk.spi.SpiEvaluationClient
import akka.runtime.sdk.spi.SpiEvaluator
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EvaluatorClientImplSpec extends AnyWordSpec with Matchers with OptionValues {

  private final class EvaluationClientProbe extends SpiEvaluationClient {
    var started: Option[(String, SpiEvaluator.Subject, Map[String, String])] = None

    override def startEvaluation(
        evaluatorComponentId: String,
        subject: SpiEvaluator.Subject,
        attributes: Map[String, String]): Future[String] = {
      started = Some((evaluatorComponentId, subject, attributes))
      Future.successful("evaluation-1")
    }
  }

  private val workflowRun = new Subject.Workflow("workflow-1", "order-workflow")

  "EvaluatorClientImpl" should {

    "start an evaluation of a workflow run with the attributes" in {
      val probe = new EvaluationClientProbe
      val client = new EvaluatorClientImpl("order-evaluator", probe)

      val evaluationId = client.startEvaluation(workflowRun, util.Map.of("interactionIds", "i1,i2"))

      evaluationId shouldBe "evaluation-1"
      val (evaluatorComponentId, subject, attributes) = probe.started.value
      evaluatorComponentId shouldBe "order-evaluator"
      subject.asInstanceOf[SpiEvaluator.Workflow].workflowId shouldBe "workflow-1"
      subject.asInstanceOf[SpiEvaluator.Workflow].workflowComponentId shouldBe "order-workflow"
      attributes shouldBe Map("interactionIds" -> "i1,i2")
    }

    "fail the blocking call with the failure of the runtime" in {
      val client = new EvaluatorClientImpl(
        "unknown-evaluator",
        (_, _, _) => Future.failed(new IllegalArgumentException("Unknown evaluator [unknown-evaluator]")))

      val exception = intercept[IllegalArgumentException] {
        client.startEvaluation(workflowRun, util.Map.of())
      }

      exception.getMessage should include("unknown-evaluator")
    }

    "reject a null subject and null attributes" in {
      val client = new EvaluatorClientImpl("order-evaluator", new EvaluationClientProbe)

      a[NullPointerException] should be thrownBy client.startEvaluation(null, util.Map.of())
      a[NullPointerException] should be thrownBy client.startEvaluation(workflowRun, null)
    }
  }
}
