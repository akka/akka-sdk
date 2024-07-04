/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.http

import java.lang.{ Boolean => JBool }
import java.lang.{ Double => JDouble }
import java.lang.{ Float => JFloat }
import java.lang.{ Integer => JInteger }
import java.lang.{ Long => JLong }
import java.lang.{ Short => JShort }

import akka.annotation.InternalApi
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.PathMatcher.Matched
import akka.http.scaladsl.server.PathMatcher.Unmatched
import akka.http.scaladsl.server.PathMatcher1
import akka.http.scaladsl.server.PathMatchers.IntNumber
import akka.http.scaladsl.server.PathMatchers.LongNumber
import akka.http.scaladsl.server.PathMatchers.Segment

/**
 * INTERNAL API
 */
@InternalApi
object Matchers {

  case class ConstMatcher(seg: String) extends PathMatcher[Unit] {
    def apply(path: Path): PathMatcher.Matching[Unit] =
      path match {
        case Path.Segment(head, tail) if head == seg => Matched(tail, ())
        case _                                       => Unmatched
      }

    override def toString: String = s"ConstMatcher($seg)"
    override def hashCode(): Int = seg.hashCode()
    override def equals(obj: Any): Boolean =
      obj match {
        case cm: ConstMatcher => cm.seg == seg
        case _                => false
      }
  }

  /**
   * A PathMatcher that matches a number and returns the negated value if the number is prefixed with a `-`.
   *
   * It first tried to match using the unsigned matcher.
   *
   * If it fails, and the path starts with a `-` sign, we remove the sign and try to match again.
   *
   * If it fails again, we try to parse the number from its string representation.
   */
  case class SignedMatcher[N](unsignedMatcher: PathMatcher1[N], label: String)(implicit numeric: Numeric[N])
      extends PathMatcher1[N] {
    override def apply(path: Path): PathMatcher.Matching[Tuple1[N]] = {
      unsignedMatcher(path).orElse {
        path match {
          case Path.Segment(head, _) if head.startsWith("-") =>
            // trying to match again without the sign
            // this matcher should produce a match in most of the cases, except when the number is the minimum value
            // then the number will overflow and the unsigned matcher will fail
            unsignedMatcher(path.dropChars(1))
              .map(num => Tuple1(numeric.negate(num._1)))
              .orElse {
                // desperate attempt to match the number by parsing the string
                // this will only happen when the number is the minimum value
                Segment(path).flatMap { string =>
                  try numeric.parseString(string._1).map(Tuple1(_))
                  catch { case _: NumberFormatException => None }
                }
              }
          case _ => Unmatched
        }
      }
    }

    override def toString: String = s"SignedMatcher($label)"
  }

  private val DoubleNumberMatcher: PathMatcher1[Double] =
    PathMatcher("""[+-]?\d*\.?\d*(?:[Ee][+-]?\d+)?""".r).flatMap { string =>
      try Some(java.lang.Double.parseDouble(string))
      catch { case _: NumberFormatException => None }
    }

  private val FloatNumberMatcher: PathMatcher1[Float] =
    DoubleNumberMatcher.flatMap { double =>
      if (double > Float.MaxValue || double < Float.MinValue) None
      else Some(double.toFloat)
    }

  private val ShortNumberMatcher: PathMatcher1[Short] = IntNumber.flatMap { int =>
    if (int > Short.MaxValue || int < Short.MinValue) None
    else Some(int.toShort)
  }

  val SignedShortNumber: SignedMatcher[Short] = SignedMatcher(ShortNumberMatcher, "Short")
  val SignedIntNumber: SignedMatcher[Int] = SignedMatcher(IntNumber, "Int")
  val SignedLongNumber: SignedMatcher[Long] = SignedMatcher(LongNumber, "Long")
  val SignedFloatNumber: SignedMatcher[Float] = SignedMatcher(FloatNumberMatcher, "Float")
  val SignedDoubleNumber: SignedMatcher[Double] = SignedMatcher(DoubleNumberMatcher, "Double")

  private val TRUE = "true"
  private val SomeTrue = Some(true)
  private val FALSE = "false"
  private val SomeFalse = Some(false)

  val BooleanMatcher: PathMatcher1[Boolean] = Segment
    .flatMap { value =>
      if (TRUE.equalsIgnoreCase(value)) SomeTrue
      else if (FALSE.equalsIgnoreCase(value)) SomeFalse
      else None
    }

  val CharMatcher: PathMatcher1[Char] =
    Segment.flatMap { s =>
      if (s.length == 1) Some(s.charAt(0))
      else None
    }

  def forType(typ: Class[_], pathVar: String): PathMatcher[_] =
    if (typ == classOf[Short] || typ == classOf[JShort]) SignedShortNumber
    else if (typ == classOf[Int] || typ == classOf[JInteger]) SignedIntNumber
    else if (typ == classOf[Long] || typ == classOf[JLong]) SignedLongNumber
    else if (typ == classOf[Float] || typ == classOf[JFloat]) SignedFloatNumber
    else if (typ == classOf[Double] || typ == classOf[JDouble]) SignedDoubleNumber
    else if (typ == classOf[Boolean] || typ == classOf[JBool]) BooleanMatcher
    else if (typ == classOf[Char] || typ == classOf[Character]) CharMatcher
    else if (typ == classOf[String]) Segment
    else
      throw new IllegalArgumentException(s"Path variable '$pathVar' can't be mapped to type '${typ.getName}'")

  implicit object MatchersOrdering extends Ordering[PathMatcher[_]] {

    private val ordering: Map[PathMatcher[_], Int] =
      Map(
        // ConstMatcher is not include here because not an object
        SignedShortNumber -> 0,
        SignedIntNumber -> 1,
        SignedLongNumber -> 2,
        SignedFloatNumber -> 3,
        SignedDoubleNumber -> 4,
        BooleanMatcher -> 5,
        CharMatcher -> 6,
        Segment -> 7 // last, but not least everything is a String
      )

    override def compare(left: PathMatcher[_], right: PathMatcher[_]): Int = {
      // constant segment matcher always go first
      if (left.isInstanceOf[ConstMatcher] && right.isInstanceOf[ConstMatcher]) 0
      else if (left.isInstanceOf[ConstMatcher]) -1
      else if (right.isInstanceOf[ConstMatcher]) 1
      else {
        val posLeft = ordering(left)
        val posRight = ordering(right)
        posLeft.compare(posRight)
      }
    }
  }
}
