/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util

import scala.jdk.CollectionConverters.SeqHasAsJava

import akka.annotation.InternalApi
import akka.javasdk.agent.MemoryFilter
import akka.javasdk.agent.MemoryFilter.MemoryFilterSupplier

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] class MemoryFiltersSupplierImpl(val filters: Vector[MemoryFilter]) extends MemoryFilterSupplier {

  def this(filter: MemoryFilter) = this(Vector(filter))

  override def includeFromAgentId(id: String): MemoryFilterSupplier =
    addFilter(MemoryFilter.Include.agentId(id))

  override def includeFromAgentIds(ids: util.Set[String]): MemoryFilterSupplier =
    addFilter(MemoryFilter.Include.agentIds(ids))

  override def excludeFromAgentId(id: String): MemoryFilterSupplier =
    addFilter(MemoryFilter.Exclude.agentId(id))

  override def excludeFromAgentIds(ids: util.Set[String]): MemoryFilterSupplier =
    addFilter(MemoryFilter.Exclude.agentIds(ids))

  override def includeFromAgentRole(role: String): MemoryFilterSupplier =
    addFilter(MemoryFilter.Include.agentRole(role))

  override def includeFromAgentRoles(roles: util.Set[String]): MemoryFilterSupplier =
    addFilter(MemoryFilter.Include.agentRoles(roles))

  override def excludeFromAgentRole(role: String): MemoryFilterSupplier =
    addFilter(MemoryFilter.Exclude.agentRole(role))

  override def excludeFromAgentRoles(roles: util.Set[String]): MemoryFilterSupplier =
    addFilter(MemoryFilter.Exclude.agentRoles(roles))

  private def addFilter(filter: MemoryFilter): MemoryFilterSupplier = {

    val newFilters =
      if (filters.exists(_.getClass == filter.getClass)) {
        filters
          .map {
            case f: MemoryFilter.Include if f.getClass == filter.getClass =>
              f.merge(filter.asInstanceOf[MemoryFilter.Include])

            case f: MemoryFilter.Exclude if f.getClass == filter.getClass =>
              f.merge(filter.asInstanceOf[MemoryFilter.Exclude])

            case any => any // making compiler happy
          }
      } else {
        filters :+ filter // Append the new filter to preserve existing filters
      }

    new MemoryFiltersSupplierImpl(newFilters)
  }

  override def get(): util.List[MemoryFilter] = filters.asJava

}
