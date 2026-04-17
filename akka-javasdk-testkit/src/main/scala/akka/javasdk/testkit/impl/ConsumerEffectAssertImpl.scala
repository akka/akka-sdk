/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.consumer.Consumer
import akka.javasdk.impl.consumer.ConsumerEffectImpl._

/**
 * INTERNAL API
 */
@InternalApi
final class ConsumerEffectAssertImpl(effect: Consumer.Effect) {

  private val resolvedEffect: PrimaryEffect = resolve(effect.asInstanceOf[PrimaryEffect])

  private def resolve(e: PrimaryEffect): PrimaryEffect = e match {
    case AsyncEffect(future) =>
      resolve(Await.result(future, 10.seconds).asInstanceOf[PrimaryEffect])
    case other => other
  }

  def isConsumed: Boolean = resolvedEffect == ConsumedEffect

  def isProduced: Boolean = resolvedEffect.isInstanceOf[ProduceEffect[_]]

  def producedMessage: AnyRef = resolvedEffect match {
    case ProduceEffect(msg, _) => msg.asInstanceOf[AnyRef]
    case _ =>
      throw new IllegalStateException(s"Expected a produce effect, but was ${effectName}")
  }

  def hasMetadata: Boolean = resolvedEffect match {
    case ProduceEffect(_, meta) => meta.isDefined
    case _ =>
      throw new IllegalStateException(s"Expected a produce effect, but was ${effectName}")
  }

  def metadata: Metadata = resolvedEffect match {
    case ProduceEffect(_, Some(meta)) => meta
    case ProduceEffect(_, None) =>
      throw new IllegalStateException("Produce effect has no metadata")
    case _ =>
      throw new IllegalStateException(s"Expected a produce effect, but was ${effectName}")
  }

  def effectName: String = resolvedEffect match {
    case ConsumedEffect      => "consumed (done/ignore)"
    case _: ProduceEffect[_] => "produce"
    case _: AsyncEffect      => "async (unresolved)"
  }
}
