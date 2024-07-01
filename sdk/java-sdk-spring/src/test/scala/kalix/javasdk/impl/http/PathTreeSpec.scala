/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.http

import scala.reflect.ClassTag

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.http.scaladsl.model.Uri.Path
import kalix.javasdk.annotations.http.Get
import kalix.javasdk.impl.http.PathTree.JavaMethodInvoker
import kalix.javasdk.impl.http.PathTree.OpenPathTree
import kalix.spring.testmodels.EndpointsTestModels
import kalix.spring.testmodels.EndpointsTestModels.FooBar
import kalix.spring.testmodels.EndpointsTestModels.FooBarBaz
import kalix.spring.testmodels.EndpointsTestModels.FooBarBazWithInt
import kalix.spring.testmodels.EndpointsTestModels.FooWithDoubleMapping1
import kalix.spring.testmodels.EndpointsTestModels.FooWithDoubleMapping2
import kalix.spring.testmodels.EndpointsTestModels.TestBoolean
import kalix.spring.testmodels.EndpointsTestModels.TestChar
import kalix.spring.testmodels.EndpointsTestModels.TestDouble
import kalix.spring.testmodels.EndpointsTestModels.TestFloat
import kalix.spring.testmodels.EndpointsTestModels.TestFloatDouble
import kalix.spring.testmodels.EndpointsTestModels.TestInt
import kalix.spring.testmodels.EndpointsTestModels.TestLong
import kalix.spring.testmodels.EndpointsTestModels.TestMultiVar
import kalix.spring.testmodels.EndpointsTestModels.TestShort
import kalix.spring.testmodels.EndpointsTestModels.TestShortIntLong
import kalix.spring.testmodels.EndpointsTestModels.TestString
import kalix.spring.testmodels.EndpointsTestModels.TestUnsupportedType
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PathTreeSpec extends AnyWordSpec with Matchers with LogCapturing with OptionValues {

  private def pathToMethods[T: ClassTag]: Seq[(String, JavaMethodInvoker)] = {
    val cls = implicitly[ClassTag[T]].runtimeClass
    cls.getDeclaredMethods.collect {
      case method if method.getAnnotation(classOf[Get]) != null =>
        (method.getAnnotation(classOf[Get]).value(), JavaMethodInvoker(() => NotUsed, method))
    }.toIndexedSeq
  }

  private def pathTreeFor(pathToMethods: Seq[(String, JavaMethodInvoker)]): OpenPathTree = {
    val pathTree = PathTree.empty
    pathToMethods.map { case (pattern, method) =>
      pathTree.append(pattern, method)
    }
    pathTree
  }

  private def pathTreeFor[T: ClassTag]: PathTree =
    pathTreeFor(pathToMethods[T]).seal

  private def openPathTreeFor[T: ClassTag]: OpenPathTree =
    pathTreeFor(pathToMethods[T])

  "PathTreeSpec" should {

    "return no match for look on a partial path" in {
      // returns a no match because invoker is on /foo/bar/baz and /foo/bar has no invoker
      val pathTree = pathTreeFor[EndpointsTestModels.Foo]
      pathTree.invokerFor(Path("/foo/bar")) shouldBe empty
    }

    "return a match for look up on a complete path" in {
      val pathTree = pathTreeFor[FooBarBaz]
      val result = pathTree.invokerFor(Path("/foo/bar/baz")).value
      result.javaMethod.getName shouldBe "doBazThings"
    }

    "return a match for node in middle of the tree path" in {
      val methods = pathToMethods[FooBarBaz] ++ pathToMethods[FooBar]
      val pathTree = pathTreeFor(methods).seal
      val result = pathTree.invokerFor(Path("/foo/bar")).value
      result.javaMethod.getName shouldBe "doBarThings"
    }

    "return a match for node in middle of the tree path (appended in different order)" in {
      // this is the same as above, but we want to verify that the order in which
      // we discover and append the paths doesn't matter
      val methods = pathToMethods[FooBar] ++ pathToMethods[FooBarBaz]
      val pathTree = pathTreeFor(methods).seal
      val result = pathTree.invokerFor(Path("/foo/bar")).value
      result.javaMethod.getName shouldBe "doBarThings"
    }

    "return a match where constant gets precedence over Int variable" in {
      val pathTree = pathTreeFor(pathToMethods[FooBarBaz] ++ pathToMethods[FooBarBazWithInt]).seal
      val resultMatchOnConst = pathTree.invokerFor(Path("/foo/bar/baz")).value
      resultMatchOnConst.javaMethod.getName shouldBe "doBazThings"

      val resultMatchOnInt = pathTree.invokerFor(Path("/foo/10/baz")).value
      resultMatchOnInt.javaMethod.getName shouldBe "doBazThingsWithInt"
      resultMatchOnInt.methodParams.head shouldBe 10
    }

    "return a match for a path with a Short path variable" in {
      val pathTree = pathTreeFor[TestShort]
      val matchPrimitive = pathTree.invokerFor(Path("/short/1")).value
      matchPrimitive.javaMethod.getName shouldBe "primitive"
      matchPrimitive.methodParams.head shouldBe 1

      val matchBoxed = pathTree.invokerFor(Path("/short/1/boxed")).value
      matchBoxed.javaMethod.getName shouldBe "boxed"
      matchBoxed.methodParams.head shouldBe 1
    }

    "return a match for a path with a negative Short path variable" in {
      val pathTree = pathTreeFor[TestShort]
      val matchPrimitive = pathTree.invokerFor(Path("/short/-1")).value
      matchPrimitive.javaMethod.getName shouldBe "primitive"
      matchPrimitive.methodParams.head shouldBe -1

      val matchBoxed = pathTree.invokerFor(Path("/short/-1/boxed")).value
      matchBoxed.javaMethod.getName shouldBe "boxed"
      matchBoxed.methodParams.head shouldBe -1
    }

    "return a match for a path with an Integer path variable" in {
      val pathTree = pathTreeFor[TestInt]
      val matchPrimitive = pathTree.invokerFor(Path("/int/1")).value
      matchPrimitive.javaMethod.getName shouldBe "primitive"
      matchPrimitive.methodParams.head shouldBe 1

      val matchBoxed = pathTree.invokerFor(Path("/int/1/boxed")).value
      matchBoxed.javaMethod.getName shouldBe "boxed"
      matchBoxed.methodParams.head shouldBe 1

      pathTree.invokerFor(Path(s"/int/${Long.MaxValue}/boxed")) shouldBe empty
    }

    "return a match for a path with a negative Integer path variable" in {
      val pathTree = pathTreeFor[TestInt]
      val matchPrimitive = pathTree.invokerFor(Path("/int/-1")).value
      matchPrimitive.javaMethod.getName shouldBe "primitive"
      matchPrimitive.methodParams.head shouldBe -1

      val matchBoxed = pathTree.invokerFor(Path("/int/-1/boxed")).value
      matchBoxed.javaMethod.getName shouldBe "boxed"
      matchBoxed.methodParams.head shouldBe -1

      pathTree.invokerFor(Path(s"/int/${Long.MaxValue}/boxed")) shouldBe empty
    }

    "return a match for a path with a Long path variable" in {
      val pathTree = pathTreeFor[TestLong]
      val matchPrimitive = pathTree.invokerFor(Path("/long/1")).value
      matchPrimitive.javaMethod.getName shouldBe "primitive"
      matchPrimitive.methodParams.head shouldBe 1

      val matchBoxed = pathTree.invokerFor(Path("/long/1/boxed")).value
      matchBoxed.javaMethod.getName shouldBe "boxed"
      matchBoxed.methodParams.head shouldBe 1
    }

    "return a match for a path with a negative Long path variable" in {
      val pathTree = pathTreeFor[TestLong]
      val matchPrimitive = pathTree.invokerFor(Path("/long/-1")).value
      matchPrimitive.javaMethod.getName shouldBe "primitive"
      matchPrimitive.methodParams.head shouldBe -1

      val matchBoxed = pathTree.invokerFor(Path("/long/-1/boxed")).value
      matchBoxed.javaMethod.getName shouldBe "boxed"
      matchBoxed.methodParams.head shouldBe -1
    }

    "first match for a short, then an int, then a long" in {
      val pathTree = pathTreeFor[TestShortIntLong]

      val matchShort = pathTree.invokerFor(Path("/number/1")).value
      matchShort.javaMethod.getName shouldBe "shortNum"
      matchShort.methodParams.head shouldBe 1

      val matchInt = pathTree.invokerFor(Path(s"/number/${Int.MaxValue}")).value
      matchInt.javaMethod.getName shouldBe "intNum"
      matchInt.methodParams.head shouldBe Int.MaxValue

      val matchLong = pathTree.invokerFor(Path(s"/number/${Long.MaxValue}")).value
      matchLong.javaMethod.getName shouldBe "longNum"
      matchLong.methodParams.head shouldBe Long.MaxValue
    }

    "return a match for a path with a Double path variable" in {
      val pathTree = pathTreeFor[TestDouble]
      val matchPrimitive = pathTree.invokerFor(Path("/double/1")).value
      matchPrimitive.javaMethod.getName shouldBe "primitive"
      matchPrimitive.methodParams.head shouldBe 1.0

      val matchBoxed = pathTree.invokerFor(Path("/double/1/boxed")).value
      matchBoxed.javaMethod.getName shouldBe "boxed"
      matchBoxed.methodParams.head shouldBe 1.0
    }

    "return a match for a path with a negative Double path variable" in {
      val pathTree = pathTreeFor[TestDouble]
      val matchPrimitive = pathTree.invokerFor(Path("/double/-1")).value
      matchPrimitive.javaMethod.getName shouldBe "primitive"
      matchPrimitive.methodParams.head shouldBe -1.0

      val matchBoxed = pathTree.invokerFor(Path("/double/-1/boxed")).value
      matchBoxed.javaMethod.getName shouldBe "boxed"
      matchBoxed.methodParams.head shouldBe -1.0
    }

    "return a match for a path with a Float path variable" in {
      val pathTree = pathTreeFor[TestFloat]
      val matchPrimitive = pathTree.invokerFor(Path("/float/1")).value
      matchPrimitive.javaMethod.getName shouldBe "primitive"
      matchPrimitive.methodParams.head shouldBe 1.0f

      val matchBoxed = pathTree.invokerFor(Path("/float/1/boxed")).value
      matchBoxed.javaMethod.getName shouldBe "boxed"
      matchBoxed.methodParams.head shouldBe 1.0f

      pathTree.invokerFor(Path(s"/float/${Double.MaxValue}/boxed")) shouldBe empty
    }

    "return a match for a path with a negative Float path variable" in {
      val pathTree = pathTreeFor[TestFloat]
      val matchPrimitive = pathTree.invokerFor(Path("/float/-1")).value
      matchPrimitive.javaMethod.getName shouldBe "primitive"
      matchPrimitive.methodParams.head shouldBe -1.0f

      val matchBoxed = pathTree.invokerFor(Path("/float/-1/boxed")).value
      matchBoxed.javaMethod.getName shouldBe "boxed"
      matchBoxed.methodParams.head shouldBe -1.0f
    }

    "first match for a float then a double" in {
      val pathTree = pathTreeFor[TestFloatDouble]
      val matchFloat = pathTree.invokerFor(Path("/number/1")).value
      matchFloat.javaMethod.getName shouldBe "floatNum"
      matchFloat.methodParams.head shouldBe 1

      val matchDouble = pathTree.invokerFor(Path(s"/number/${Double.MaxValue}")).value
      matchDouble.javaMethod.getName shouldBe "doubleNum"
      matchDouble.methodParams.head shouldBe Double.MaxValue
    }

    "return a match for a path with a String path variable" in {
      val pathTree = pathTreeFor[TestString]
      val matchPrimitive = pathTree.invokerFor(Path("/string/joe")).value
      matchPrimitive.javaMethod.getName shouldBe "name"
      matchPrimitive.methodParams.head shouldBe "joe"
    }

    "return a match for a path with a Char path variable" in {
      val pathTree = pathTreeFor[TestChar]
      val matchPrimitive = pathTree.invokerFor(Path("/char/c")).value
      matchPrimitive.javaMethod.getName shouldBe "name"
      matchPrimitive.methodParams.head shouldBe 'c'
    }

    "return a match for a path with a Boolean path variable" in {
      val pathTree = pathTreeFor[TestBoolean]
      val matchPrimitive = pathTree.invokerFor(Path("/bool/true")).value
      matchPrimitive.javaMethod.getName shouldBe "primitive"
      matchPrimitive.methodParams.head shouldBe true

      val matchBoxed = pathTree.invokerFor(Path("/bool/false/boxed")).value
      matchBoxed.javaMethod.getName shouldBe "boxed"
      matchBoxed.methodParams.head shouldBe false
    }

    "match a compatible path with more then one path var" in {
      val pathTree = pathTreeFor[TestMultiVar]
      val matchFirst = pathTree.invokerFor(Path("/multi/1/true/0.2")).value
      matchFirst.javaMethod.getName shouldBe "intBooleanDouble"
      matchFirst.methodParams.reverse shouldBe Array(1, true, 0.2)

      val match2 = pathTree.invokerFor(Path("/multi/name/10/0.2")).value
      match2.javaMethod.getName shouldBe "stringLongFloat"
      match2.methodParams.reverse shouldBe Array("name", 10L, 0.2f)
    }

    "match a compatible path with extra slashes" in {
      val pathTree = pathTreeFor[FooBarBaz]
      val result = pathTree.invokerFor(Path("/foo////bar////////baz")).value
      result.javaMethod.getName shouldBe "doBazThings"
    }

    "not match when wrong path variable type is passed" in {
      val pathTree = pathTreeFor[TestInt]
      pathTree.invokerFor(Path("/int/joe")) shouldBe empty
    }

    "not match when not the same constants in path" in {
      val pathTree = pathTreeFor[FooBar]
      pathTree.invokerFor(Path("/bar/foo")) shouldBe empty
    }

    "fail to create matcher when path variable matches unsupported type" in {
      intercept[IllegalArgumentException] {
        pathTreeFor[TestUnsupportedType]
      }.getMessage should include("Path variable '{name}' can't be mapped to type 'java.util.Optional'")
    }

    "fail to build to merge trees with duplicated mapping" in {
      intercept[IllegalArgumentException] {
        val pathTree2 = openPathTreeFor[FooWithDoubleMapping2]
        val pathTree1 = openPathTreeFor[FooWithDoubleMapping1]
        pathTree1 ++ pathTree2
      }.getMessage should include("are both mapped to endpoint path '/foo/bar/baz'")
    }
  }

}
