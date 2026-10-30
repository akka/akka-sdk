/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.javasdk.evaluation.EvaluationContext
import akka.javasdk.evaluation.Evaluator
import akka.javasdk.evaluation.Subject
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

      Await.result(impl.evaluate(new SpiEvaluator.EvaluationContext(trigger, telemetryContext)), 3.seconds)

      factoryContext.map(_.get(key)) shouldBe Some("evaluation-1")
    }

    "pass the config for the evaluated agent on the context" in {
      var seen: Option[Config] = None
      val impl = new EvaluatorImpl[Evaluator](
        _ =>
          new Evaluator {
            override def evaluate(context: EvaluationContext): Evaluator.Effect = {
              seen = Some(context.config())
              effects().inconclusive("config seen")
            }
          },
        classOf[Evaluator],
        {
          case interaction: Subject.Interaction =>
            ConfigFactory.parseString(s"""agent = "${interaction.agentComponentId()}" """)
          case _: Subject.Workflow => ConfigFactory.empty()
        })

      Await.result(impl.evaluate(new SpiEvaluator.EvaluationContext(trigger, OtelContext.root())), 3.seconds)

      seen.map(_.getString("agent")) shouldBe Some("my-agent")
    }

    "pass the attributes and the config of the evaluator for a workflow subject" in {
      var seen: Option[(Subject, java.util.Map[String, String], Config)] = None
      val impl = new EvaluatorImpl[Evaluator](
        _ =>
          new Evaluator {
            override def evaluate(context: EvaluationContext): Evaluator.Effect = {
              seen = Some((context.subject(), context.attributes(), context.config()))
              effects().inconclusive("context seen")
            }
          },
        classOf[Evaluator],
        {
          case _: Subject.Interaction => ConfigFactory.empty()
          case _: Subject.Workflow    => ConfigFactory.parseString("""entry = "evaluator" """)
        })
      val manualTrigger = new SpiEvaluator.Trigger(
        "evaluation-2",
        SpiEvaluator.TriggerSource.Manual,
        new SpiEvaluator.Workflow("workflow-1", "order-workflow"),
        Map("interactionIds" -> "i1,i2"))

      Await.result(impl.evaluate(new SpiEvaluator.EvaluationContext(manualTrigger, OtelContext.root())), 3.seconds)

      val (subject, attributes, config) = seen.get
      subject shouldBe new Subject.Workflow("workflow-1", "order-workflow")
      attributes shouldBe java.util.Map.of("interactionIds", "i1,i2")
      config.getString("entry") shouldBe "evaluator"
    }

    "map a not applicable effect" in {
      val impl = new EvaluatorImpl[Evaluator](
        _ =>
          new Evaluator {
            override def evaluate(context: EvaluationContext): Evaluator.Effect =
              effects().notApplicable("no tool call")
          },
        classOf[Evaluator],
        _ => ConfigFactory.empty())

      val effect =
        Await.result(impl.evaluate(new SpiEvaluator.EvaluationContext(trigger, OtelContext.root())), 3.seconds)

      effect.asInstanceOf[SpiEvaluator.NotApplicableEffect].reason shouldBe "no tool call"
    }

    "fail the evaluation when the config cannot be resolved" in {
      val impl = new EvaluatorImpl[SomeEvaluator](
        _ => new SomeEvaluator,
        classOf[SomeEvaluator],
        _ => throw new IllegalStateException("no config"))

      val failure = impl.evaluate(new SpiEvaluator.EvaluationContext(trigger, OtelContext.root())).failed
      Await.result(failure, 3.seconds).getMessage shouldBe "no config"
    }
  }

  private def trigger = new SpiEvaluator.Trigger(
    "evaluation-1",
    SpiEvaluator.TriggerSource.OnInteraction,
    new SpiEvaluator.Interaction("interaction-1", "my-agent", None))
}
