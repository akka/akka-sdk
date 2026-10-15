/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.javasdk.evaluation.EvaluationContext
import akka.javasdk.evaluation.Evaluator
import akka.javasdk.testmodels.evaluation.EvaluatorTestModels.SomeEvaluator
import akka.runtime.sdk.spi.SpiEvaluator
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.{ Context => OtelContext }
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
        classOf[SomeEvaluator],
        _ => ConfigFactory.empty())

      val trigger = new SpiEvaluator.Trigger(
        "evaluation-1",
        SpiEvaluator.TriggerSource.OnInteraction,
        new SpiEvaluator.Interaction("interaction-1", "my-agent", None))
      Await.result(impl.evaluate(new SpiEvaluator.EvaluationContext(trigger, telemetryContext)), 3.seconds)

      factoryContext.map(_.get(key)) shouldBe Some("evaluation-1")
    }

    "pass the settings for the evaluated agent on the context" in {
      var seen: Option[Config] = None
      val impl = new EvaluatorImpl[Evaluator](
        _ =>
          new Evaluator {
            override def evaluate(context: EvaluationContext): Evaluator.Effect = {
              seen = Some(context.config())
              effects().inconclusive("settings seen")
            }
          },
        classOf[Evaluator],
        agentComponentId => ConfigFactory.parseString(s"""agent = "$agentComponentId" """))

      val trigger = new SpiEvaluator.Trigger(
        "evaluation-1",
        SpiEvaluator.TriggerSource.OnInteraction,
        new SpiEvaluator.Interaction("interaction-1", "my-agent", None))
      Await.result(impl.evaluate(new SpiEvaluator.EvaluationContext(trigger, OtelContext.root())), 3.seconds)

      seen.map(_.getString("agent")) shouldBe Some("my-agent")
    }
  }
}
