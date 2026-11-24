/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.time.{ Duration => JDuration }
import java.util
import java.util.function.Predicate

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.JavaDurationOps

import akka.annotation.InternalApi
import akka.javasdk.agent.RemoteA2aAgent

/**
 * INTERNAL API
 */
@InternalApi
final case class RemoteA2aAgentImpl(name: String, skillFilter: Option[Predicate[String]], timeout: FiniteDuration)
    extends RemoteA2aAgent {

  require(name != null && name.nonEmpty, "The name of a remote A2A agent must not be empty")

  def this(name: String) = this(name, None, Duration.Zero)

  override def withSkillFilter(skillFilter: Predicate[String]): RemoteA2aAgent =
    copy(skillFilter = Some(skillFilter))

  override def withAllowedSkills(allowedSkills: util.Set[String]): RemoteA2aAgent = {
    val skills = Set.from(allowedSkills.toArray.map(_.asInstanceOf[String]))
    copy(skillFilter = Some(skills.contains(_)))
  }

  override def withAllowedSkills(allowedSkill: String, moreAllowedSkills: String*): RemoteA2aAgent = {
    val skills = (allowedSkill +: moreAllowedSkills).toSet
    copy(skillFilter = Some(skills.contains(_)))
  }

  override def withTimeout(timeout: JDuration): RemoteA2aAgent =
    copy(timeout = timeout.toScala)
}
