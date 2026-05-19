/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util
import java.util.Optional
import java.util.function.Function

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import akka.annotation.InternalApi
import akka.javasdk.agent.AgentRegistry
import akka.javasdk.agent.MemoryFilter
import akka.javasdk.agent.SessionMessage

/**
 * INTERNAL API
 *
 * Shared filtering and trimming helpers for session memory history. Used by both `SessionMemoryEntity#getHistory` (when
 * reading the current entity state) and the agent runtime (when falling back to a chunked journal read), so the two
 * paths produce identical context for the model.
 *
 * The public API uses Java collection types so the entity (which is implemented in Java) can call it without conversion
 * noise; Scala callers convert at the call site.
 */
@InternalApi
object MemoryHistoryUtils {

  /** Resolves a component id to its role, or `Optional.empty` if the agent has no usable role. */
  type RoleLookup = Function[String, Optional[String]]

  /**
   * Resolve the role of an agent component, treating null/blank roles as "no role" so a role-based filter does not
   * accidentally match every agent.
   */
  def roleLookup(agentRegistry: AgentRegistry): RoleLookup =
    (componentId: String) =>
      agentRegistry
        .agentInfoOption(componentId)
        .flatMap(info => Optional.ofNullable(info.role()).filter(r => r.trim.nonEmpty))

  /** Apply each filter in order, narrowing the message list as we go. */
  def applyFilters(
      messages: util.List[SessionMessage],
      filters: util.List[MemoryFilter],
      roleLookup: RoleLookup): util.List[SessionMessage] = {
    var current = messages.asScala.toList
    filters.asScala.foreach { filter =>
      current = applyFilter(current, filter, roleLookup)
    }
    current.asJava
  }

  private def applyFilter(
      messages: List[SessionMessage],
      filter: MemoryFilter,
      roleLookup: RoleLookup): List[SessionMessage] = filter match {
    case incl: MemoryFilter.Include =>
      val ids = incl.ids().asScala
      val roles = incl.roles().asScala
      messages.filter { m =>
        ids.contains(m.componentId()) ||
        roleLookup.apply(m.componentId()).toScala.exists(roles.contains)
      }
    case excl: MemoryFilter.Exclude =>
      val ids = excl.ids().asScala
      val roles = excl.roles().asScala
      messages.filter { m =>
        !ids.contains(m.componentId()) &&
        !roleLookup.apply(m.componentId()).toScala.exists(roles.contains)
      }
  }

  /**
   * Keep only the last `n` messages. When `lastN` is empty or the list already has at most `n` elements, the input list
   * is returned untouched.
   */
  def trimToLastN(messages: util.List[SessionMessage], lastN: Optional[Integer]): util.List[SessionMessage] = {
    if (lastN.isPresent && messages.size > lastN.get) {
      messages.subList(messages.size - lastN.get, messages.size)
    } else {
      messages
    }
  }
}
