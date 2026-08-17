/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.reflection

import akka.javasdk.annotations.Evaluates
import akka.javasdk.evaluation.EvaluationContext
import akka.javasdk.evaluation.Evaluator
import akka.javasdk.evaluation.Subject
import akka.javasdk.impl.evaluation.EvaluatorImpl
import akka.runtime.sdk.spi.SpiEvaluator
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UndeclaredSubjectKindEvaluator extends Evaluator {
  override def evaluate(context: EvaluationContext): Evaluator.Effect = null
}

@Evaluates(value = Array(classOf[Subject.Flow], classOf[Subject.Session]))
class FlowAndSessionEvaluator extends Evaluator {
  override def evaluate(context: EvaluationContext): Evaluator.Effect = null
}

class EvaluatorSubjectKindsSpec extends AnyWordSpec with Matchers {

  "Reflect.readEvaluatorSubjectKinds" should {

    "default to Subject.Interaction when no @Evaluates is present" in {
      Reflect.readEvaluatorSubjectKinds(classOf[UndeclaredSubjectKindEvaluator]) shouldBe Set(
        classOf[Subject.Interaction])
    }

    "read the declared subject kinds from @Evaluates" in {
      Reflect.readEvaluatorSubjectKinds(classOf[FlowAndSessionEvaluator]) shouldBe
      Set(classOf[Subject.Flow], classOf[Subject.Session])
    }
  }

  "EvaluatorImpl.toSpiSubjectKind" should {

    "map every Subject variant to its SPI counterpart" in {
      EvaluatorImpl.toSpiSubjectKind(classOf[Subject.Interaction]) shouldBe SpiEvaluator.SubjectKind.Interaction
      EvaluatorImpl.toSpiSubjectKind(classOf[Subject.Flow]) shouldBe SpiEvaluator.SubjectKind.Flow
      EvaluatorImpl.toSpiSubjectKind(classOf[Subject.Session]) shouldBe SpiEvaluator.SubjectKind.Session
      EvaluatorImpl.toSpiSubjectKind(classOf[Subject.EvaluatedEvaluation]) shouldBe
      SpiEvaluator.SubjectKind.EvaluatedEvaluation
      EvaluatorImpl.toSpiSubjectKind(classOf[Subject.Experiment]) shouldBe SpiEvaluator.SubjectKind.Experiment
    }
  }
}
