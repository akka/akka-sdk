/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.javasdk.testmodels.evaluation.EvaluatorTestModels.SomeEvaluator
import akka.runtime.sdk.spi.SpiEvaluator
import io.opentelemetry.context.{ Context => OtelContext }
import io.opentelemetry.context.ContextKey
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EvaluatorImplSpec extends AnyWordSpec with Matchers {

  "EvaluatorImpl" should {

    "create the evaluator with the telemetry context of the evaluation" in {
      val key = ContextKey.named[String]("test")
      val telemetryContext = OtelContext.root().`with`(key, "evaluation-1")
      var factoryContext: Option[OtelContext] = None
      val impl = new EvaluatorImpl[SomeEvaluator](
        context => {
          factoryContext = context
          new SomeEvaluator
        },
        classOf[SomeEvaluator])

      val trigger = new SpiEvaluator.Trigger(
        "evaluation-1",
        SpiEvaluator.TriggerSource.OnInteraction,
        new SpiEvaluator.Interaction("interaction-1", "my-agent", None))
      Await.result(impl.evaluate(new SpiEvaluator.EvaluationContext(trigger, telemetryContext)), 3.seconds)

      factoryContext.map(_.get(key)) shouldBe Some("evaluation-1")
    }
  }
}
