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
    addFilter(new MemoryFilter.IncludeFromAgentId(util.Set.of(id)))

  override def includeFromAgentIds(ids: util.Set[String]): MemoryFilterSupplier =
    addFilter(new MemoryFilter.IncludeFromAgentId(ids))

  override def excludeFromAgentId(id: String): MemoryFilterSupplier =
    addFilter(new MemoryFilter.ExcludeFromAgentId(util.Set.of(id)))

  override def excludeFromAgentIds(ids: util.Set[String]): MemoryFilterSupplier =
    addFilter(new MemoryFilter.ExcludeFromAgentId(ids))

  override def includeFromAgentRole(role: String): MemoryFilterSupplier =
    addFilter(new MemoryFilter.IncludeFromAgentRole(util.Set.of(role)))

  override def includeFromAgentRoles(roles: util.Set[String]): MemoryFilterSupplier =
    addFilter(new MemoryFilter.IncludeFromAgentRole(roles))

  override def excludeFromAgentRole(role: String): MemoryFilterSupplier =
    addFilter(new MemoryFilter.ExcludeFromAgentRole(util.Set.of(role)))

  override def excludeFromAgentRoles(roles: util.Set[String]): MemoryFilterSupplier =
    addFilter(new MemoryFilter.ExcludeFromAgentRole(roles))

  private def addFilter(filter: MemoryFilter): MemoryFilterSupplier = {

    def concatList(l1: util.Set[String], l2: util.Set[String]) = {
      val newList = new util.HashSet(l1)
      newList.addAll(l2)
      newList
    }

    val newFilters =
      if (filters.exists(_.getClass == filter.getClass)) {
        filters
          .map {
            case f: MemoryFilter.IncludeFromAgentId if f.getClass == filter.getClass =>
              val ids = filter.asInstanceOf[MemoryFilter.IncludeFromAgentId].ids()
              new MemoryFilter.IncludeFromAgentId(concatList(f.ids(), ids))

            case f: MemoryFilter.ExcludeFromAgentId if f.getClass == filter.getClass =>
              val ids = filter.asInstanceOf[MemoryFilter.ExcludeFromAgentId].ids()
              new MemoryFilter.ExcludeFromAgentId(concatList(f.ids(), ids))

            case f: MemoryFilter.IncludeFromAgentRole if f.getClass == filter.getClass =>
              val roles = filter.asInstanceOf[MemoryFilter.IncludeFromAgentRole].roles()
              new MemoryFilter.IncludeFromAgentRole(concatList(f.roles(), roles))

            case f: MemoryFilter.ExcludeFromAgentRole if f.getClass == filter.getClass =>
              val roles = filter.asInstanceOf[MemoryFilter.ExcludeFromAgentRole].roles()
              new MemoryFilter.ExcludeFromAgentRole(concatList(f.roles(), roles))

            case any => any // making compiler happy
          }
      } else {
        filters :+ filter // Append the new filter to preserve existing filters
      }

    new MemoryFiltersSupplierImpl(newFilters)
  }

  override def get(): util.List[MemoryFilter] = filters.asJava

}
