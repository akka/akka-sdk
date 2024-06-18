/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.http

import java.lang.{ Boolean => JBool }
import java.lang.{ Double => JDouble }
import java.lang.{ Float => JFloat }
import java.lang.{ Integer => JInt }
import java.lang.{ Long => JLong }
import java.lang.{ Short => JShort }

import akka.http.scaladsl.model.Uri.Path
import kalix.javasdk.impl.http.DynPathMatcher.MatchedResult
import kalix.javasdk.impl.http.DynPathMatcher.UnmatchedResult
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DynPathMatcherSpec extends AnyWordSpec with Matchers with OptionValues {

  "A PathMatcher" should {

    "not match root / if not so defined" in {
      val matcher = DynPathMatcher("/foo/bar", Array.empty)
      val path = Path("/")
      matcher(path) shouldBe a[UnmatchedResult.type]
    }

    "match root / if so defined" in {
      val matcher = DynPathMatcher("/", Array.empty)
      val path = Path("/")

      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 0 // no parameter extracted
    }

    "match a compatible path with Short in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[Short]))
      val path = Path("/foo/10/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 10
    }

    "match a compatible path with java Short in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[JShort]))
      val path = Path("/foo/10/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 10
    }

    "match a compatible path with Int in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[Int]))
      val path = Path("/foo/10/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 10
    }

    "match a compatible path with java Integer in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[JInt]))
      val path = Path("/foo/10/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 10
    }

    "match a compatible path with Long in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[Long]))
      val path = Path("/foo/10/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 10L
    }

    "match a compatible path with Java Long in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[JLong]))
      val path = Path("/foo/10/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 10
    }

    "match a compatible path with Double in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[Double]))
      val path = Path("/foo/10.0/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 10.0
    }

    "match a compatible path with Java Double in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[JDouble]))
      val path = Path("/foo/10.0/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 10.0
    }

    "match a compatible path with Float in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[Float]))
      val path = Path("/foo/10.0/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 10.0
    }

    "match a compatible path with Java Float in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[JFloat]))
      val path = Path("/foo/10.0/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 10.0
    }

    "match a compatible path with String in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[String]))
      val path = Path("/foo/10/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe "10"
    }

    "match a compatible path with Char in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[Char]))
      val path = Path("/foo/a/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 'a'
    }

    "match a compatible path with Java Char in path" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[Character]))
      val path = Path("/foo/a/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 'a'
    }

    "match a compatible path with Boolean in path" in {
      val matcher = DynPathMatcher("/foo/bar/{bool}", Array(classOf[Boolean]))
      val path = Path("/foo/bar/false")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe false
    }

    "match a compatible path with Java Boolean in path" in {
      val matcher = DynPathMatcher("/foo/bar/{bool}", Array(classOf[JBool]))
      val path = Path("/foo/bar/false")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe false
    }

    "match a compatible path with more then one path var" in {
      val matcher = DynPathMatcher("/foo/bar/{bool}/{num}", Array(classOf[Boolean], classOf[Int]))
      val path = Path("/foo/bar/false/20")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 2
      values.head shouldBe false
      values(1) shouldBe 20
    }

    "match a compatible path with extra slashes" in {
      val matcher = DynPathMatcher("///foo///{abc}////bar", Array(classOf[Int]))
      val path = Path("/foo/10/bar")
      val MatchedResult(Path.Empty, _, values) = matcher(path)
      values should have size 1
      values.head shouldBe 10
    }

    "not match when wrong path variable type is passed" in {
      val matcher = DynPathMatcher("/foo/{abc}/bar", Array(classOf[Int]))
      val path = Path("/foo/hey/bar")
      matcher(path) shouldBe a[UnmatchedResult.type]
    }

    "not match when not the same constants in path" in {
      val matcher = DynPathMatcher("/foo/bar", Array.empty)
      val path = Path("/bar/foo")
      matcher(path) shouldBe a[UnmatchedResult.type]
    }

    "fail to create matcher when wrong number of vars" in {
      val message =
        intercept[IllegalArgumentException] {
          DynPathMatcher("/foo/{abc}/bar/{def}", Array(classOf[Int]))
        }.getMessage

      message should include("The path pattern [/foo/{abc}/bar/{def}]")
      message should include("2 path variable(s)")
      message should include("method with only 1 parameter(s)")
    }

    "fail to create matcher when path variable matches unsupported type" in {

      case class Foo(name: String)
      val message =
        intercept[IllegalArgumentException] {
          DynPathMatcher("/foo/{name}", Array(classOf[Foo]))
        }.getMessage

      message should include(
        "Path variable '{name}' can't be mapped to type 'kalix.javasdk.impl.http.DynPathMatcherSpec$Foo$1'")
    }

  }

}
