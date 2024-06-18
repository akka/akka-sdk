/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.http

import java.lang.{ Boolean => JBool }
import java.lang.{ Double => JDouble }
import java.lang.{ Float => JFloat }
import java.lang.{ Integer => JInteger }
import java.lang.{ Long => JLong }
import java.lang.{ Short => JShort }

import scala.annotation.tailrec

import akka.annotation.InternalApi
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.PathMatcher.Matched
import akka.http.scaladsl.server.PathMatcher.Unmatched
import akka.http.scaladsl.server.PathMatcher1
import akka.http.scaladsl.server.PathMatchers.DoubleNumber
import akka.http.scaladsl.server.PathMatchers.IntNumber
import akka.http.scaladsl.server.PathMatchers.LongNumber
import akka.http.scaladsl.server.PathMatchers.Segment

/**
 * INTERNAL API
 */
@InternalApi
object DynPathMatcher {

  sealed trait MatchingResult

  object MatchedResult {
    def init(path: Path, numVars: Int): MatchedResult = MatchedResult(path, 0, new Array[Any](numVars))
  }

  /**
   * This case class is carrying an Array (mutable) around and therefore its array should never be mutated outside this
   * class.
   */
  case class MatchedResult(path: Path, numExtractedValues: Int, extractedValues: Array[Any]) extends MatchingResult {

    /* Update this MatchedResult by dropping the path head.
     * Useful when consuming constant segments, ie: we update the path, but doesn't append any value
     */
    def dropPathHead: MatchedResult = MatchedResult(path.tail, numExtractedValues, extractedValues)

    /* Update this MatchedResult with a new extracted value
     * This will also update the path by dropping the head, since it has been consumed.
     */
    def appendValue(value: Any): MatchedResult = {
      extractedValues.update(numExtractedValues, value)
      MatchedResult(path.tail, numExtractedValues + 1, extractedValues)
    }

  }

  object UnmatchedResult extends MatchingResult

  private val BooleanSegment: PathMatcher1[Boolean] = Segment
    .map(_.trim.toLowerCase)
    .flatMap {
      case "true"  => Some(true)
      case "false" => Some(false)
      case _       => None
    }

  private val CharSegment: PathMatcher1[Char] =
    Segment.flatMap {
      case s if s.length == 1 => Some(s.charAt(0))
      case _                  => None
    }

  private case class ConstSegment(seg: String) extends PathMatcher[Unit] {
    def apply(path: Path): PathMatcher.Matching[Unit] = path match {
      case Path.Segment(head, tail) if head == seg => Matched(tail, ())
      case _                                       => Unmatched
    }
  }

  private def forType(expectedType: Class[_], pathVar: String): PathMatcher[_] =
    expectedType match {
      case c if c == classOf[Short] | c == classOf[JShort]   => IntNumber
      case c if c == classOf[Int] | c == classOf[JInteger]   => IntNumber
      case c if c == classOf[Long] | c == classOf[JLong]     => LongNumber
      case c if c == classOf[Double] | c == classOf[JDouble] => DoubleNumber
      case c if c == classOf[Float] | c == classOf[JFloat]   => DoubleNumber
      case c if c == classOf[Boolean] | c == classOf[JBool]  => BooleanSegment
      case c if c == classOf[Char] | c == classOf[Character] => CharSegment
      case c if c == classOf[String]                         => Segment
      case _ =>
        throw new IllegalArgumentException(
          s"Path variable '$pathVar' can't be mapped to type '${expectedType.getName}'")
    }

  def apply(pattern: String, methodParams: Array[Class[_]]): DynPathMatcher = {

    var currentIndex = 0
    var numVars = 0

    def isPathVariable(seg: String) = seg.startsWith("{") && seg.endsWith("}")

    // traverses the Path and build up a list of PathMatchers according to the types from `methodParams`
    @tailrec
    def traverse(curPath: Path, acc: List[PathMatcher[_]]): List[PathMatcher[_]] = {
      curPath match {
        case Path.Segment(head, tail) if isPathVariable(head) =>
          numVars += 1
          if (currentIndex == methodParams.length) {
            // TODO: run this at compile time
            throw new IllegalArgumentException(
              s"The path pattern [$pattern] contains ${numVars} path variable(s), but is being used on a method with " +
              s"only ${methodParams.length} parameter(s). Make sure the path variables match the exising method parameters.")
          }

          // lookup a PathMatcher for the type
          val matcher = forType(methodParams(currentIndex), head)
          currentIndex = numVars
          traverse(tail, matcher :: acc)

        case Path.Segment(head, tail) => traverse(tail, ConstSegment(head) :: acc)
        case Path.Slash(tail)         => traverse(tail, acc) // skip slashes
        case Path.Empty               => acc
      }
    }

    val annotationPath = Path(pattern)
    // matchers are appended and at the end we reverse the list
    val matchers = traverse(annotationPath, List.empty).reverse
    new DynPathMatcher(annotationPath, numVars, matchers)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
case class DynPathMatcher(annotationPath: Path, numberOfPathVariables: Int, pathMatchers: List[PathMatcher[_]]) {

  import DynPathMatcher._

  def apply(reqPath: Path): MatchingResult = {

    @tailrec
    def traverse(curResult: MatchingResult, curMatchers: List[PathMatcher[_]]): MatchingResult = {
      curResult match {

        // the iteration ends when both all reqPath segments and matchers have been consumed
        case MatchedResult(Path.Empty, _, _) if curMatchers.isEmpty => curResult

        // slashes are skipped
        case m @ MatchedResult(Path.Slash(_), _, _) => traverse(m.dropPathHead, curMatchers)

        // got a segmented that needs to be matched against the expected type
        case m @ MatchedResult(Path.Segment(pathVar, _), _, _) if curMatchers.nonEmpty =>
          curMatchers.head(Path(pathVar)) match {
            case Matched(_, Tuple1(extractedValue)) =>
              traverse(m.appendValue(extractedValue), curMatchers.tail) // extract a value
            case Matched(_, ()) => traverse(m.dropPathHead, curMatchers.tail) // match and skip constant
            case _ => UnmatchedResult // we are done when unmatched or anything else other than Tuple 1 or ()
          }

        // this catch all covers the situation where there are still paths to match in the reqPath,
        // but this DynPathMatcher has been exhausted
        // for example: DynPathMatcher for /foo/{name}, with requested path being /foo/joe/bar
        // in such case, this DynPathMatcher should NOT return a match
        // Note that Unmatched never reaches this point as the tailrec finishes earlier
        case _ => UnmatchedResult
      }
    }

    traverse(MatchedResult.init(reqPath, numberOfPathVariables), pathMatchers)
  }

}
