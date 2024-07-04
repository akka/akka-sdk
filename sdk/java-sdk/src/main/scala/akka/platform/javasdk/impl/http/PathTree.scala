/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.http

import java.lang.reflect.Method

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import akka.annotation.InternalApi
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.PathMatcher.Matched
import akka.http.scaladsl.server.PathMatchers
import akka.platform.javasdk.impl.http.Matchers.ConstMatcher
import akka.platform.javasdk.impl.http.Matchers.MatchersOrdering
import akka.platform.javasdk.impl.http.PathTree.ParameterizedMethodInvoker
import akka.platform.javasdk.impl.http.PathTree.PathNode
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
object PathTree {

  case class JavaMethodInvoker(instanceFactory: () => Any, javaMethod: Method)

  case class ParameterizedMethodInvoker(instanceFactory: () => Any, javaMethod: Method, methodParams: List[Any]) {

    // for now, and by convention, if the number of extracted params + 1 equals
    // the total num of method params, we assume it's a method that receives a body
    val bodyType: Option[Class[_]] =
      Option.when(methodParams.length + 1 == javaMethod.getParameterTypes.length) {
        javaMethod.getParameterTypes.drop(methodParams.length)(0)
      }

    // methodParams are collected using prepend and therefore in reversed order
    private def reversedParamsArray(extraSlot: Int): Array[Any] = {
      val methodParamsArray = new Array[Any](methodParams.length + extraSlot)
      // walk through methodParams in revered order an fill in the array
      var readIndex = methodParams.length - 1
      var writeIndex = 0
      while (readIndex >= 0) {
        methodParamsArray(writeIndex) = methodParams(readIndex)
        readIndex -= 1
        writeIndex += 1
      }
      methodParamsArray
    }

    def invoke(): Any =
      javaMethod.invoke(instanceFactory(), reversedParamsArray(0): _*)

    def invoke(body: Any): Any = {
      val methodParamsPlusBody = reversedParamsArray(1)
      methodParamsPlusBody.update(methodParams.length, body)
      javaMethod.invoke(instanceFactory(), methodParamsPlusBody: _*)
    }
  }

  private def root = new PathNode("/", "/", PathMatchers.Neutral, mutable.ListBuffer.empty, None)

  def empty = new OpenPathTree(new PathNode("/", "/", PathMatchers.Neutral, mutable.ListBuffer.empty, None))

  /**
   * An OpenPathTree is an intermediate state to build a final PathTree.
   *
   * One can append and merge nodes on an open tree, but cannot read (look up) from it. After sealing it, we get hold to
   * a PathTree that can be used to look up. This is to ensure that the tree is not modified afterwards.
   *
   * Internally, the tree is still based on a mutable structure but the its mutability is hidden from the outside.
   */
  class OpenPathTree(_root: PathNode) {

    @volatile private var open: Boolean = true

    private def root = _root

    /** Merges two OpenPathTree if none of the two have been sealed before */
    def ++(other: OpenPathTree): OpenPathTree = {
      require(open, "This tree has been sealed already.")
      require(other.open, "The passed tree has been sealed already.")
      new OpenPathTree(this.root ++ other.root)
    }

    /** Appends a new path to this tree if it hasn't been sealed yet */
    def append(pathStr: String, methodInvoker: JavaMethodInvoker): OpenPathTree = {
      require(open, "This tree has been sealed already.")
      val pathNodes = constructPathNodeList(pathStr, methodInvoker)
      new OpenPathTree(root.append(pathNodes))
    }

    /**
     * Seals the tree and returns a PathTree. During sealing the path matchers are sorted according to the matcher's
     * precedence ordering. See [[MatchersOrdering]] for details.
     */
    def seal: PathTree = {
      open = false
      new PathTree(root.sort)
    }

  }

  def apply(pathPattern: String, methodInvoker: JavaMethodInvoker): OpenPathTree = {
    // first explode the path into a List[PathNode] and then append it to the root
    val pathNodes = constructPathNodeList(pathPattern, methodInvoker)
    new OpenPathTree(root.append(pathNodes))
  }

  /**
   * This method takes a path pattern and a JavaMethodInvoker and constructs a list of PathNode. The list will contain a
   * PathNode for each segment in the path pattern and the last PathNode will have the JavaMethodInvoker.
   */
  private def constructPathNodeList(pathPattern: String, methodInvoker: JavaMethodInvoker): List[PathNode] = {

    var currentIndex = 0
    var numVars = 0
    val methodParams = methodInvoker.javaMethod.getParameterTypes

    def isPathVariable(seg: String) = seg.startsWith("{") && seg.endsWith("}")

    // traverse the path pattern and construct a list of PathNode
    // on each iteration we build back the parent path as a string, as such each PathNode 'knows' where it belongs
    @tailrec
    def traverse(path: Path, parent: String, acc: List[PathNode]): List[PathNode] = {
      path match {
        case Path.Slash(tail) => traverse(tail, parent, acc)

        case Path.Segment(head, tail) if isPathVariable(head) =>
          // lookup a PathMatcher for the type
          numVars += 1
          if (currentIndex == methodParams.length) {
            throw new IllegalArgumentException(
              s"The path pattern [$pathPattern] contains ${numVars} path variable(s), but is being used on a method with " +
              s"only ${methodParams.length} parameter(s). Make sure the path variables match the exising method parameters.")
          }
          val matcher = Matchers.forType(methodParams(currentIndex), head)
          currentIndex = numVars

          if (tail.isEmpty) {
            val pathNode = new PathNode(parent, head, matcher, ListBuffer.empty, Some(methodInvoker))
            pathNode +: acc
          } else {
            val pathNode = new PathNode(parent, head, matcher, ListBuffer.empty, None)
            traverse(tail, parent + "/" + head, pathNode +: acc)
          }

        case Path.Segment(head, tail) => // those are constant paths, using ConstMatcher
          if (tail.isEmpty) {
            val pathNode = new PathNode(parent, head, ConstMatcher(head), ListBuffer.empty, Some(methodInvoker))
            pathNode +: acc
          } else {
            val pathNode = new PathNode(parent, head, ConstMatcher(head), ListBuffer.empty, None)
            traverse(tail, parent + "/" + head, pathNode +: acc)
          }
        case Path.Empty => acc
      }
    }

    // paths like /a/b/c/ or /a/b/c/// need to be sanitized to /a/b/c
    @tailrec
    def sanitize(original: Path, sanitized: Path): Path =
      original match {
        case Path.Slash(tail)         => sanitize(tail, sanitized)
        case Path.Segment(head, tail) => sanitize(tail, sanitized / head)
        case Path.Empty               => sanitized
      }

    val nodes = traverse(sanitize(Path(pathPattern), Path.Empty), "", List.empty)
    nodes.reverse
  }

  private class PathNode(
      val parentPath: String,
      // a Path here is always a single one without slashes
      val path: String,
      // the matcher that will extra data for that given single node
      val matcher: PathMatcher[_],
      // all PathNodes under this one (will be sorted to respect matching precedence)
      var children: mutable.ListBuffer[PathNode],
      // will be a Some if end of Path
      var methodInvoker: Option[JavaMethodInvoker]) {

    private val logger = LoggerFactory.getLogger(classOf[PathNode])

    def hasInvoker: Boolean = methodInvoker.nonEmpty

    private def withInvoker(invokerOpt: Option[JavaMethodInvoker]): Unit =
      methodInvoker = invokerOpt

    private def isRootPath(pn: PathNode): Boolean =
      pn.path == root.path && methodInvoker.isEmpty

    def fullPath: String = parentPath + "/" + path

    /**
     * this method merges two PathNode by copying over the nodes from the other PathNode to this PathNode
     */
    def ++(other: PathNode): PathNode = {
      if (this.hasInvoker && other.hasInvoker) {
        val thisMethod = this.methodInvoker.map(_.javaMethod).get
        val otherMethod = other.methodInvoker.map(_.javaMethod).get
        throw new IllegalArgumentException(
          s"Method '$thisMethod' and '$otherMethod' are both mapped to endpoint path '${fullPath}'")
      } else if (other.hasInvoker) {
        // copy other invoker to this one
        this.withInvoker(other.methodInvoker)
      }

      // go over the other's children try to merge it with the children on this path
      other.children.foreach { otherChild =>
        this.children.find(_ == otherChild) match {
          case Some(matchingChild) => matchingChild ++ otherChild
          case None                => this.children.addOne(otherChild)
        }
      }
      this
    }

    def sort: PathNode = {
      // after sorting, we need to overwrite the old ListBuffer
      // because sortBy returns a new sorted list
      children = children.sortBy(_.matcher)
      children.foreach(_.sort)
      this
    }

    // FIXME: pre-validation should ensure that a path is never overwritten

    /*
     * This method takes a List[PathNode] add each PathNode to the tree on the right position.
     */
    def append(nodes: List[PathNode]): PathNode = {

      nodes match {
        case head :: Nil if isRootPath(head) =>
          // special case: not yet supporting a method annotated with Endpoint("/"), so doing nothing for now
          this

        case head :: Nil if head.hasInvoker =>
          // can only be added if no conflicting children at that level
          // this is the end of a path and must have an invoker

          children.find(_ == head) match {
            case Some(samePath) if samePath.hasInvoker =>
              val headMethod = head.methodInvoker.map(_.javaMethod).get
              val otherMethod = samePath.methodInvoker.map(_.javaMethod).get
              throw new IllegalArgumentException(
                s"Path '${head.fullPath}' is being mapped to two different methods: '$headMethod' and '$otherMethod'")

            case Some(samePath) =>
              // they are the same, but now an invoker is being added to it
              // copy existing one as it might have children
              samePath.withInvoker(head.methodInvoker)
            case None =>
              children.addOne(head)
          }

          this

        case _ :: Nil =>
          // for completeness only, this should never happen because we will validate beforehand
          throw new IllegalArgumentException(s"Found an end of path without an associated method.")

        case head :: tail =>
          // if exists already, keep the existing one, otherwise add new
          children.find(_ == head) match {
            case Some(existing) => existing.append(tail) // append children to existing one
            case None =>
              head.append(tail)
              // and the whole become a child of this node
              this.children.addOne(head)
          }

          this

        case _ => this
      }

    }

    // lookup for a match on one of the children and stop iteration as soon a match is found
    private def lookupMatchingChild(path: Path, extractedParams: List[Any]): Option[(PathNode, List[Any])] = {
      @tailrec
      def traverse(childrenNodes: mutable.ListBuffer[PathNode]): Option[(PathNode, List[Any])] = {
        if (childrenNodes.nonEmpty) {
          // note: each PathNode matches a single path seg
          // doesn't matter how long is the path here,
          // if it's a match, it's only for the first seg in Path
          childrenNodes.head.matcher(path) match {
            case Matched(_, Tuple1(value)) =>
              val params = value +: extractedParams

              if (logger.isDebugEnabled()) {
                logger.debug(
                  "'{}' matched '{}' - extracted value is: {}. All extracted values '{}'",
                  path,
                  childrenNodes.head,
                  value,
                  params)
              }
              Some((childrenNodes.head, params))

            case Matched(_, ()) =>
              if (logger.isDebugEnabled()) {
                logger.debug("'{}' matched '{}' - nothing extracted", path, childrenNodes.head)
              }
              Some((childrenNodes.head, extractedParams))

            case _ =>
              if (logger.isDebugEnabled()) {
                logger.debug("'{}' didn't match '{}'", path, childrenNodes.head)
              }
              traverse(childrenNodes.tail)
          }
        } else {
          None
        }
      }

      traverse(children)
    }

    def invokerFor(path: Path): Option[ParameterizedMethodInvoker] = {
      invokerFor(path, List.empty)
    }

    @tailrec
    private def invokerFor(path: Path, accParams: List[Any]): Option[ParameterizedMethodInvoker] = {
      // Important:
      // Whenever we do a lookup, it's always from the root downwards.
      // Therefore we go directly to the children and we don't match on the root itself.
      // After finding a matching child, we come back recursively to this same method, but this time on a
      // child that have already been matched, and then we go look further on its own children.

      def toJavaMethodInvoker(
          methodInvoker: Option[JavaMethodInvoker],
          params: List[Any]): Option[ParameterizedMethodInvoker] = {
        methodInvoker match {
          case Some(invoker) =>
            Some(ParameterizedMethodInvoker(invoker.instanceFactory, invoker.javaMethod, params))
          case None => None
        }
      }

      path match {
        case Path.Slash(tail) => invokerFor(tail, accParams)

        case pathSeg @ Path.Segment(_, tail) =>
          // look up across children and pick first match
          this.lookupMatchingChild(pathSeg, accParams) match {
            case Some((childNode, params)) if tail.isEmpty && childNode.hasInvoker =>
              toJavaMethodInvoker(childNode.methodInvoker, params)
            case Some((childNode, params)) =>
              childNode.invokerFor(tail, params) // drop head and continue
            case _ => None
          }

        case Path.Empty if hasInvoker =>
          // end of path and we have a invoker, however loop should stop earlier
          toJavaMethodInvoker(methodInvoker, accParams)
        case Path.Empty => None
      }

    }

    override def toString: String =
      s"PathNode: " +
      s"path = $path, " +
      s"children = ${children.mkString("[", ", ", "]")}, " +
      s"invoker = ${methodInvoker.map(_.javaMethod.getName)}"

    override def hashCode(): Int = matcher.hashCode()

    override def equals(obj: Any): Boolean = {
      // PathNode equality is defined by the matcher. We can't have two siblings with same matcher
      obj match {
        case other: PathNode => matcher.equals(other.matcher)
        case _               => false
      }
    }
  }
}

class PathTree(root: PathNode) {
  def invokerFor(path: Path): Option[ParameterizedMethodInvoker] =
    root.invokerFor(path)

  // this method produces a list of all leave paths in the tree
  // this is need to build the EndpointDescriptor
  // will become obsolete when this code moves into the runtime
  def allFullPaths: List[String] = {
    @tailrec
    def traverse(nodeList: ListBuffer[PathNode], leaves: ListBuffer[String]): ListBuffer[String] = {
      val (leaveNodes, nonLeaveNodes) = nodeList.partition(_.hasInvoker)
      val collectedLeaves = leaveNodes.map(_.fullPath) ++ leaves

      if (nonLeaveNodes.isEmpty) collectedLeaves
      else {
        val allChildrenAtThatLevel = nonLeaveNodes.flatMap(_.children)
        traverse(allChildrenAtThatLevel, collectedLeaves)
      }
    }

    traverse(root.children, ListBuffer.empty).toList
  }

}
