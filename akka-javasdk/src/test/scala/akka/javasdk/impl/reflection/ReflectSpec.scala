/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.reflection

import akka.javasdk.annotations.FunctionTool
import akka.javasdk.annotations.OptionalDescription
import akka.javasdk.client.ComponentClient
import akka.javasdk.impl.client.ComponentClientImpl
import akka.javasdk.impl.serialization.JsonSerializer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

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
  }

  "Reflect.valueOrAlias" should {

    class AnnotatedFunctions {
      @FunctionTool(value = "foo-value")
      def onlyValue(): Unit = {}

      @FunctionTool(description = "foo-description")
      def onlyAlias(): Unit = {}

      @FunctionTool
      def noneSet(): Unit = {}

      @FunctionTool(value = "foo-value", description = "foo-description")
      def bothSet(): Unit = {}

      @OptionalDescription
      def noneSetOpt(): Unit = {}
    }

    val methods = classOf[AnnotatedFunctions].getDeclaredMethods
    def ann(name: String) = methods.find(_.getName == name).get.getAnnotation(classOf[FunctionTool])
    def annOpt(name: String) = methods.find(_.getName == name).get.getAnnotation(classOf[OptionalDescription])

    "return value when only value is set" in {
      Reflect.valueOrAlias[String](ann("onlyValue")) shouldBe "foo-value"
    }

    "return alias when only alias is set" in {
      Reflect.valueOrAlias[String](ann("onlyAlias")) shouldBe "foo-description"
    }

    "throw when none are set and is required" in {
      an[IllegalArgumentException] should be thrownBy Reflect.valueOrAlias[String](ann("noneSet"))
    }

    "throw when both value and alias are set" in {
      an[IllegalArgumentException] should be thrownBy Reflect.valueOrAlias[String](ann("bothSet"))
    }

    "return null when neither is set and is optional" in {
      Reflect.valueOrAlias[String](annOpt("noneSetOpt")) shouldBe null
    }
  }
}
