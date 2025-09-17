/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.reflection

import scala.concurrent.ExecutionContext

import akka.Done
import akka.javasdk.client.ComponentClient
import akka.javasdk.impl.client.ComponentClientImpl
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflowAnnotatedAbstractClass
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflowAnnotatedAbstractClassLegacy
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflowAnnotatedInterface
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflowAnnotatedInterfaceLegacy
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflowSealedInterface
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflowSealedInterfaceLegacy
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflowUnannotatedAbstractClass
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflowUnannotatedAbstractClassLegacy
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflowUnannotatedInterface
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflowUnannotatedInterfaceLegacy
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflowWithPrimitives
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SomeClass {
  def a(): Unit = {}
  def b(): Unit = {}
  def c(): Unit = {}
  def c(p1: Int): Unit = {}
  def c(p1: String): Unit = {}
  def c(p1: String, p2: Int): Unit = {}
  def c(p1: Int, p2: Int): Unit = {}
}

class ReflectSpec extends AnyWordSpec with Matchers {
  private val serializer = new JsonSerializer

  "The reflection utils" must {
    "deterministically sort methods of the same class" in {
      import akka.javasdk.impl.reflection.Reflect.methodOrdering
      val methods =
        classOf[SomeClass].getDeclaredMethods.toList.sorted.map(m =>
          (m.getName, m.getParameterTypes.map(_.getSimpleName).toList))
      methods shouldBe (
        ("a", Nil) ::
        ("b", Nil) ::
        ("c", Nil) ::
        ("c", List("int")) ::
        ("c", List("int", "int")) ::
        ("c", List("String")) ::
        ("c", List("String", "int")) :: Nil
      )
    }

    "lookup component client instances" in {
      abstract class Foo(val componentClient: ComponentClient)
      class Bar(val anotherComponentClient: ComponentClient, val parentComponentClient: ComponentClient)
          extends Foo(parentComponentClient)

      val c1 = ComponentClientImpl(null, serializer, Map.empty, None)(ExecutionContext.global, null)
      val c2 = ComponentClientImpl(null, serializer, Map.empty, None)(ExecutionContext.global, null)
      val bar = new Bar(c1, c2)

      Reflect.lookupComponentClientFields(bar) should have size 2
    }

    "return all step input types for a workflow" in {
      val types = Reflect.workflowKnownInputTypes(classOf[TransferWorkflowSealedInterface])

      types should contain theSameElementsAs List(
        classOf[TransferWorkflowSealedInterface.Deposit],
        classOf[TransferWorkflowSealedInterface.Withdraw],
        classOf[TransferWorkflowSealedInterface.Transaction],
        classOf[TransferWorkflowSealedInterface.CreditTransaction],
        classOf[TransferWorkflowSealedInterface.DebitTransaction])
    }

    "return all step input types for a workflow with primitives" in {
      val types = Reflect.workflowKnownInputTypes(classOf[TransferWorkflowWithPrimitives])

      types should contain theSameElementsAs List(
        classOf[Boolean],
        classOf[Long],
        classOf[Int],
        classOf[Float],
        classOf[Double],
        classOf[Short],
        classOf[Char])
    }

    "return all step input types for a workflow with annotated interface" in {
      val types =
        Reflect.workflowKnownInputTypes(classOf[TransferWorkflowAnnotatedInterface])
      types should contain theSameElementsAs List(
        classOf[TransferWorkflowAnnotatedInterface.Transaction],
        classOf[TransferWorkflowAnnotatedInterface.CreditTransaction],
        classOf[TransferWorkflowAnnotatedInterface.DebitTransaction])
    }

    "throw exception if interface subtypes can be found" in {
      intercept[IllegalArgumentException] {
        Reflect.workflowKnownInputTypes(classOf[TransferWorkflowUnannotatedInterface])
      }.getMessage should startWith("Can't determine all existing subtypes")
    }

    "return all step input types for a workflow with annotated abstract clas" in {
      val types =
        Reflect.workflowKnownInputTypes(classOf[TransferWorkflowAnnotatedAbstractClass])
      types should contain theSameElementsAs List(
        classOf[TransferWorkflowAnnotatedAbstractClass.Transaction],
        classOf[TransferWorkflowAnnotatedAbstractClass.CreditTransaction],
        classOf[TransferWorkflowAnnotatedAbstractClass.DebitTransaction])
    }

    "throw exception if abstract class subtypes can be found" in {
      intercept[IllegalArgumentException] {
        Reflect.workflowKnownInputTypes(classOf[TransferWorkflowUnannotatedAbstractClass])
      }.getMessage should startWith("Can't determine all existing subtypes")
    }

    "return all step input types for a workflow (legacy)" in {
      val types = Reflect.workflowKnownInputTypes(new TransferWorkflowSealedInterfaceLegacy)

      types should contain theSameElementsAs List(
        classOf[Done],
        classOf[TransferWorkflowSealedInterfaceLegacy.Deposit],
        classOf[TransferWorkflowSealedInterfaceLegacy.Withdraw],
        classOf[TransferWorkflowSealedInterfaceLegacy.Transaction],
        classOf[TransferWorkflowSealedInterfaceLegacy.CreditTransaction],
        classOf[TransferWorkflowSealedInterfaceLegacy.DebitTransaction])
    }

    "return all step input types for a workflow with annotated interface (legacy)" in {
      val types =
        Reflect.workflowKnownInputTypes(new TransferWorkflowAnnotatedInterfaceLegacy)
      types should contain theSameElementsAs List(
        classOf[Done],
        classOf[TransferWorkflowAnnotatedInterfaceLegacy.Transaction],
        classOf[TransferWorkflowAnnotatedInterfaceLegacy.CreditTransaction],
        classOf[TransferWorkflowAnnotatedInterfaceLegacy.DebitTransaction])
    }

    "throw exception if interface subtypes can be found (legacy)" in {
      intercept[IllegalArgumentException] {
        Reflect.workflowKnownInputTypes(new TransferWorkflowUnannotatedInterfaceLegacy)
      }.getMessage should startWith("Can't determine all existing subtypes")
    }

    "return all step input types for a workflow with annotated abstract clas (legacy)" in {
      val types =
        Reflect.workflowKnownInputTypes(new TransferWorkflowAnnotatedAbstractClassLegacy)
      types should contain theSameElementsAs List(
        classOf[Done],
        classOf[TransferWorkflowAnnotatedAbstractClassLegacy.Transaction],
        classOf[TransferWorkflowAnnotatedAbstractClassLegacy.CreditTransaction],
        classOf[TransferWorkflowAnnotatedAbstractClassLegacy.DebitTransaction])
    }

    "throw exception if abstract class subtypes can be found (legacy)" in {
      intercept[IllegalArgumentException] {
        Reflect.workflowKnownInputTypes(new TransferWorkflowUnannotatedAbstractClassLegacy)
      }.getMessage should startWith("Can't determine all existing subtypes")
    }
  }
}
