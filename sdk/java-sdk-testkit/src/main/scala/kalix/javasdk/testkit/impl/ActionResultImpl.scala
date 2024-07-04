/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit.impl

import akka.platform.javasdk.action.Action
import akka.platform.javasdk.impl.action.ActionEffectImpl
import akka.platform.javasdk.testkit.ActionResult
import java.util.concurrent.CompletionStage

import io.grpc.Status

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext

/**
 * INTERNAL API
 */
final class ActionResultImpl[T](effect: ActionEffectImpl.PrimaryEffect[T]) extends ActionResult[T] {

  def this(effect: Action.Effect[T]) = this(effect.asInstanceOf[ActionEffectImpl.PrimaryEffect[T]])

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  /** @return true if the call had an effect with a reply, false if not */
  override def isReply(): Boolean = effect.isInstanceOf[ActionEffectImpl.ReplyEffect[T]]

  override def getReply(): T = {
    val reply = getEffectOfType(classOf[ActionEffectImpl.ReplyEffect[T]])
    reply.msg
  }

  //TODO add metadata??

  // TODO rewrite
  /** @return true if the call was async, false if not */
  override def isAsync(): Boolean = effect.isInstanceOf[ActionEffectImpl.AsyncEffect[T]]

  override def getAsyncResult(): CompletionStage[ActionResult[T]] = {
    val async = getEffectOfType(classOf[ActionEffectImpl.AsyncEffect[T]])
    async.effect.map(new ActionResultImpl(_).asInstanceOf[ActionResult[T]]).toJava
  }

  /** @return true if the call was an error, false if not */
  override def isError(): Boolean = effect.isInstanceOf[ActionEffectImpl.ErrorEffect[T]]

  override def getError(): String = {
    val error = getEffectOfType(classOf[ActionEffectImpl.ErrorEffect[T]])
    error.description
  }

  override def isIgnore(): Boolean = effect == ActionEffectImpl.IgnoreEffect()

  override def getErrorStatusCode(): Status.Code = {
    val error = getEffectOfType(classOf[ActionEffectImpl.ErrorEffect[T]])
    error.statusCode.getOrElse(Status.Code.UNKNOWN)
  }

  /**
   * Look at effect and verifies that it is of type E or fail if not.
   *
   * @return
   *   The next effect if it is of type E, for additional assertions.
   */
  private def getEffectOfType[E](expectedClass: Class[E]): E = {
    if (expectedClass.isInstance(effect)) effect.asInstanceOf[E]
    else
      throw new NoSuchElementException(
        "expected effect type [" + expectedClass.getName + "] but found [" + effect.getClass.getName + "]")
  }

}
