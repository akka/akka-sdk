/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.runtime.sdk.spi.SpiEvaluationClient
import akka.runtime.sdk.spi.SpiEvaluator
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.opentelemetry.context.{ Context => OtelContext }
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GuardedEvaluationClientSpec extends AnyWordSpec with Matchers with OptionValues {

  private final class DelegateProbe extends SpiEvaluationClient {
    var started: Option[String] = None

    override def startEvaluation(
        evaluatorComponentId: String,
        subject: SpiEvaluator.Subject,
        attributes: Map[String, String],
        telemetryContext: OtelContext): Future[String] = {
      started = Some(evaluatorComponentId)
      Future.successful("evaluation-1")
    }
  }

  // load the config over reference.conf, the same way an application.conf is loaded
  private def load(config: String): Config = ConfigFactory.load(ConfigFactory.parseString(config))

  private val run = new SpiEvaluator.Workflow("workflow-1", "order-workflow")

  private def start(client: SpiEvaluationClient, evaluatorComponentId: String): String =
    Await.result(client.startEvaluation(evaluatorComponentId, run, Map.empty, OtelContext.root()), 3.seconds)

  "GuardedEvaluationClient" should {

    "start an evaluator that is enabled or has no entry" in {
      val delegate = new DelegateProbe
      val client = new GuardedEvaluationClient(
        delegate,
        load("""
        akka.javasdk.evaluation.evaluators.judge { threshold = 0.7 }
      """))

      start(client, "judge") shouldBe "evaluation-1"
      delegate.started.value shouldBe "judge"
      start(client, "unconfigured") shouldBe "evaluation-1"
    }

    "fail the start of a disabled evaluator without calling the runtime" in {
      val delegate = new DelegateProbe
      val client = new GuardedEvaluationClient(
        delegate,
        load("""
        akka.javasdk.evaluation.evaluators.judge { enabled = false }
      """))

      val exception = intercept[IllegalStateException](start(client, "judge"))

      exception.getMessage should include("judge")
      delegate.started shouldBe None
    }

    "apply the evaluator defaults to an evaluator without an entry" in {
      val client = new GuardedEvaluationClient(
        new DelegateProbe,
        load("""
        akka.javasdk.evaluation.defaults.evaluator.enabled = false
        akka.javasdk.evaluation.evaluators.judge { enabled = true }
      """))

      an[IllegalStateException] should be thrownBy start(client, "unconfigured")
      start(client, "judge") shouldBe "evaluation-1"
    }
  }
}
