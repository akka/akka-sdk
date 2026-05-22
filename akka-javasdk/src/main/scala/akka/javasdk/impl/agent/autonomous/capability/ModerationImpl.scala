/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import akka.annotation.InternalApi
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.autonomous.capability.Moderation

/**
 * INTERNAL API
 */
@InternalApi
final case class ModerationImpl(
    participants: Seq[Class[_ <: AutonomousAgent]],
    maxRoundsValue: Int = 5,
    maxIterationsPerTurnValue: Int = 10,
    maxConcurrentConversationsValue: Int = 1)
    extends Moderation {

  override def maxRounds(max: Int): Moderation =
    copy(maxRoundsValue = max)

  override def maxIterationsPerTurn(max: Int): Moderation =
    copy(maxIterationsPerTurnValue = max)

  override def maxConcurrentConversations(max: Int): Moderation =
    copy(maxConcurrentConversationsValue = max)
}

/**
 * INTERNAL API
 */
@InternalApi
object ModerationImpl {
  def create(first: Class[_ <: AutonomousAgent], rest: Array[Class[_ <: AutonomousAgent]]): ModerationImpl =
    ModerationImpl(first +: rest.toSeq)
}
