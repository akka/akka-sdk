/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.http

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.PathMatcher.Matched
import akka.http.scaladsl.server.PathMatcher.Unmatched
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.platform.javasdk.impl.http.Matchers.BooleanMatcher
import akka.platform.javasdk.impl.http.Matchers.CharMatcher
import akka.platform.javasdk.impl.http.Matchers.ConstMatcher
import akka.platform.javasdk.impl.http.Matchers.MatchersOrdering
import akka.platform.javasdk.impl.http.Matchers.SignedDoubleNumber
import akka.platform.javasdk.impl.http.Matchers.SignedFloatNumber
import akka.platform.javasdk.impl.http.Matchers.SignedIntNumber
import akka.platform.javasdk.impl.http.Matchers.SignedLongNumber
import akka.platform.javasdk.impl.http.Matchers.SignedShortNumber
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MatchersSpec extends AnyWordSpec with Matchers {

  "ConstMatcher" should {
    "match only exact segment" in {
      val matcher = ConstMatcher("foo")
      val matchedResult = matcher(Path("foo"))
      matchedResult shouldBe a[Matched[_]]

      val unmatchedResult = matcher(Path("bar"))
      unmatchedResult shouldBe a[Unmatched.type]
    }
  }

  "BooleanMatcher" should {
    "extract boolean value disregarding the case" in {

      val allTrue = "True" :: "TRUE" :: "true" :: "tRuE" :: Nil
      allTrue.foreach { b =>
        val Matched(_, Tuple1(bool)) = BooleanMatcher(Path(b))
        bool shouldBe true
      }

      val allFalse = "False" :: "FALSE" :: "false" :: "faLSe" :: Nil
      allFalse.foreach { b =>
        val Matched(_, Tuple1(bool)) = BooleanMatcher(Path(b))
        bool shouldBe false
      }

      val notBool = "flase" :: "ture" :: Nil
      notBool.foreach { b =>
        val result = BooleanMatcher(Path(b))
        result shouldBe a[Unmatched.type]
      }

    }
  }

  "FloatNumber" should {
    "extract float number" in {
      val Matched(_, Tuple1(f)) = SignedFloatNumber(Path("3.14"))
      f shouldBe 3.14f
    }

    "extract a negative float number" in {
      val Matched(_, Tuple1(f)) = SignedFloatNumber(Path("-3.14"))
      f shouldBe -3.14f
    }

    "extract a negative float number - Float.MinValue" in {
      val Matched(_, Tuple1(f)) = SignedFloatNumber(Path(s"${Float.MinValue}"))
      f shouldBe Float.MinValue
    }

    "not extract a double" in {
      val notFloat = SignedFloatNumber(Path(Double.MaxValue.toString))
      notFloat shouldBe a[Unmatched.type]
    }

    "not extract a Float.MaxValue + 1" in {
      // this test is to ensure that the implementation only drops a char if it's a negative number
      // a naive impl would drop the first char on the first unmatched case and would then produce
      // a match for a wrong number
      val notFloat = SignedFloatNumber(Path("3.4028236E38"))
      notFloat shouldBe a[Unmatched.type]
    }

  }

  "DoubleNumber" should {
    "extract double number" in {
      val Matched(_, Tuple1(f)) = SignedDoubleNumber(Path(Double.MaxValue.toString))
      f shouldBe Double.MaxValue
    }

    "extract a negative double number" in {
      val Matched(_, Tuple1(f)) = SignedDoubleNumber(Path("-3.14"))
      f shouldBe -3.14
    }

    "extract a negative double number - Double.MinValue" in {
      val Matched(_, Tuple1(f)) = SignedDoubleNumber(Path(s"${Double.MinValue}"))
      f shouldBe Double.MinValue
    }
  }

  "SignedShortNumber" should {

    "extract short number" in {
      val Matched(_, Tuple1(s)) = SignedShortNumber(Path("10"))
      s shouldBe 10
    }

    "extract a negative short number" in {
      val Matched(_, Tuple1(s)) = SignedShortNumber(Path("-10"))
      s shouldBe -10
    }

    "extract a negative short number - Short.Minvalue" in {
      val Matched(_, Tuple1(s)) = SignedShortNumber(Path(s"${Short.MinValue}"))
      s shouldBe Short.MinValue
    }

    "not extract an integer" in {
      val notShort = SignedShortNumber(Path(Int.MaxValue.toString))
      notShort shouldBe a[Unmatched.type]
    }

    "not extract a Short.MaxValue + 1" in {
      // this test is to ensure that the implementation only drops a char if it's a negative number
      // a naive impl would drop the first char on the first unmatched case and would then produce
      // a match for a wrong number
      val notShort = SignedShortNumber(Path("32768"))
      notShort shouldBe a[Unmatched.type]
    }
  }

  "SignedIntNumber" should {
    "extract a negative int number" in {
      val Matched(_, Tuple1(s)) = SignedIntNumber(Path("-10"))
      s shouldBe -10
    }

    "extract a negative int number - Int.MinValue" in {
      val Matched(_, Tuple1(s)) = SignedIntNumber(Path(s"${Int.MinValue}"))
      s shouldBe Int.MinValue
    }

    "not extract a Int.MaxValue + 1" in {
      // this test is to ensure that the implementation only drops a char if it's a negative number
      // a naive impl would drop the first char on the first unmatched case and would then produce
      // a match for a wrong number
      val notShort = SignedShortNumber(Path("2147483648"))
      notShort shouldBe a[Unmatched.type]
    }
  }

  "SignedLongNumber" should {
    "extract a negative long number" in {
      val Matched(_, Tuple1(s)) = SignedLongNumber(Path("-10"))
      s shouldBe -10
    }

    "extract a negative long number - Long.MinValue" in {
      val Matched(_, Tuple1(s)) = SignedLongNumber(Path(s"${Long.MinValue}"))
      s shouldBe Long.MinValue
    }

    "not extract a Long.MaxValue + 1" in {
      // this test is to ensure that the implementation only drops a char if it's a negative number
      // a naive impl would drop the first char on the first unmatched case and would then produce
      // a match for a wrong number
      val notShort = SignedShortNumber(Path("9223372036854775808"))
      notShort shouldBe a[Unmatched.type]
    }
  }

  "CharMatcher" should {
    "extract single character" in {
      val Matched(_, Tuple1(c)) = CharMatcher(Path("a"))
      c shouldBe 'a'

      val notChar = BooleanMatcher(Path("ab"))
      notChar shouldBe a[Unmatched.type]
    }
  }

  "MatchersOrdering" should {

    "sort matcher list as expected" in {
      // reverser order of matchers
      val reversedPrecedenceOrder: List[PathMatcher[_]] =
        Segment ::
        CharMatcher ::
        BooleanMatcher ::
        SignedDoubleNumber ::
        SignedFloatNumber ::
        SignedLongNumber ::
        SignedIntNumber ::
        SignedShortNumber ::
        ConstMatcher("abc") ::
        Nil
      reversedPrecedenceOrder.sorted shouldBe reversedPrecedenceOrder.reverse
    }

    "const segment should always be first" in {
      val sorted: List[PathMatcher[_]] =
        ConstMatcher("bar") :: ConstMatcher("foo") :: SignedIntNumber :: SignedIntNumber :: Nil

      sorted.sorted shouldBe sorted // ordering didn't affect it

      val unsorted: List[PathMatcher[_]] =
        SignedIntNumber :: ConstMatcher("bar") :: SignedIntNumber :: ConstMatcher("foo") :: Nil
      unsorted.sorted shouldBe sorted
    }
  }
}
