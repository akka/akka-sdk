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
private[javasdk] class MemoryFiltersSupplierImpl(val filters: List[MemoryFilter]) extends MemoryFilterSupplier {

  def this(filter: MemoryFilter) = this(List(filter))

  override def includeFromAgentId(id: String): MemoryFilterSupplier =
    new MemoryFiltersSupplierImpl(filters :+ new MemoryFilter.IncludeFromAgentId(util.Set.of(id)))

  override def includeFromAgentId(ids: util.Set[String]): MemoryFilterSupplier =
    new MemoryFiltersSupplierImpl(filters :+ new MemoryFilter.IncludeFromAgentId(ids))

  override def excludeFromAgentId(id: String): MemoryFilterSupplier =
    new MemoryFiltersSupplierImpl(filters :+ new MemoryFilter.ExcludeFromAgentId(util.Set.of(id)))

  override def excludeFromAgentId(ids: util.Set[String]): MemoryFilterSupplier =
    new MemoryFiltersSupplierImpl(filters :+ new MemoryFilter.ExcludeFromAgentId(ids))

  override def includeFromAgentRole(role: String): MemoryFilterSupplier =
    new MemoryFiltersSupplierImpl(filters :+ new MemoryFilter.IncludeFromAgentRole(util.Set.of(role)))

  override def includeFromAgentRole(roles: util.Set[String]): MemoryFilterSupplier =
    new MemoryFiltersSupplierImpl(filters :+ new MemoryFilter.IncludeFromAgentRole(roles))

  override def excludeFromAgentRole(role: String): MemoryFilterSupplier =
    new MemoryFiltersSupplierImpl(filters :+ new MemoryFilter.ExcludeFromAgentRole(util.Set.of(role)))

  override def excludeFromAgentRole(roles: util.Set[String]): MemoryFilterSupplier =
    new MemoryFiltersSupplierImpl(filters :+ new MemoryFilter.ExcludeFromAgentRole(roles))

  override def get(): util.List[MemoryFilter] = filters.asJava
}
