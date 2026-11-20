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
import akka.javasdk.a2a.RemoteA2aAgent

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object RemoteA2aAgentImpl {

  def named(name: String): RemoteA2aAgentImpl = {
    if (name == null || name.isBlank) throw new IllegalArgumentException("A remote A2A agent name must not be blank")
    RemoteA2aAgentImpl(name, None, None, None)
  }

  def fromCard(url: String): RemoteA2aAgentImpl = {
    if (url == null || !(url.startsWith("http://") || url.startsWith("https://")))
      throw new IllegalArgumentException(
        s"The Agent Card URL of a remote A2A agent must start with http:// or https://, got [$url]")
    RemoteA2aAgentImpl(url, None, None, None)
  }
}

/**
 * INTERNAL API
 *
 * @param nameOrCardUrl
 *   the argument of `A2aClient.forAgent`
 */
@InternalApi
private[akka] final case class RemoteA2aAgentImpl(
    nameOrCardUrl: String,
    allowedSkills: Option[Set[String]],
    timeout: Option[FiniteDuration],
    handOffAfter: Option[FiniteDuration])
    extends RemoteA2aAgent {

  def isCardUrl: Boolean = nameOrCardUrl.startsWith("http://") || nameOrCardUrl.startsWith("https://")

  override def withAllowedSkills(skillIds: util.Set[String]): RemoteA2aAgent =
    copy(allowedSkills = Some(skillIds.asScala.toSet))

  override def withTimeout(timeout: JDuration): RemoteA2aAgent = {
    if (timeout.isNegative || timeout.isZero)
      throw new IllegalArgumentException(s"The timeout of a remote A2A agent must be positive, got [$timeout]")
    copy(timeout = Some(timeout.toScala))
  }

  override def handOffTasksStillWorkingAfter(after: JDuration): RemoteA2aAgent = {
    if (after.isNegative)
      throw new IllegalArgumentException(s"The hand-off time of a remote A2A agent must not be negative, got [$after]")
    copy(handOffAfter = Some(after.toScala))
  }
}
