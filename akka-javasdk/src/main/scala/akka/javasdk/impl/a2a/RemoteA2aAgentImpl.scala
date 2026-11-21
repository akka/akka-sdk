/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.time.{ Duration => JDuration }
import java.util

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

import akka.annotation.InternalApi
import akka.javasdk.agent.RemoteA2aAgent

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object RemoteA2aAgentImpl {

  def named(name: String): RemoteA2aAgentImpl =
    RemoteA2aAgentImpl(A2aRemoteTarget.named(name), None, None, None)

  def fromCardUrl(url: String): RemoteA2aAgentImpl =
    RemoteA2aAgentImpl(A2aRemoteTarget.cardUrl(url), None, None, None)

  def fromService(serviceName: String): RemoteA2aAgentImpl =
    RemoteA2aAgentImpl(A2aRemoteTarget.service(serviceName), None, None, None)
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final case class RemoteA2aAgentImpl(
    target: A2aRemoteTarget,
    allowedSkills: Option[Set[String]],
    timeout: Option[FiniteDuration],
    handOffAfter: Option[FiniteDuration])
    extends RemoteA2aAgent {

  override def withAllowedSkills(skillIds: util.Set[String]): RemoteA2aAgent =
    copy(allowedSkills = Some(skillIds.asScala.toSet))

  override def withAllowedSkills(skillId: String, moreSkillIds: String*): RemoteA2aAgent =
    copy(allowedSkills = Some((skillId +: moreSkillIds).toSet))

  override def withTimeout(timeout: JDuration): RemoteA2aAgent = {
    if (timeout.isNegative || timeout.isZero)
      throw new IllegalArgumentException(s"The timeout of a remote A2A agent must be positive, got [$timeout]")
    copy(timeout = Some(timeout.toScala))
  }

  override def handOffTasksStillWorkingAfter(time: JDuration): RemoteA2aAgent = {
    if (time.isNegative || time.isZero)
      throw new IllegalArgumentException(s"The hand-off time of a remote A2A agent must be positive, got [$time]")
    copy(handOffAfter = Some(time.toScala))
  }
}
