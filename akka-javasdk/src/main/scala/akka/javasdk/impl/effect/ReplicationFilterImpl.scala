/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.effect

import java.util

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.runtime.sdk.spi.SpiEventSourcedEntity

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object ReplicationFilterImpl {
  val empty: ReplicationFilterImpl = ReplicationFilterImpl(Set.empty, Set.empty)
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class ReplicationFilterImpl(includedRegions: Set[String], excludedRegions: Set[String])
    extends akka.javasdk.eventsourcedentity.ReplicationFilter.Builder {
  override def includeRegion(region: String): ReplicationFilterImpl =
    copy(includedRegions = includedRegions + region)

  override def includeRegions(regions: util.Set[String]): ReplicationFilterImpl =
    copy(includedRegions = includedRegions.union(regions.asScala))

  override def excludeRegion(region: String): ReplicationFilterImpl =
    copy(excludedRegions = excludedRegions + region)

  override def excludeRegions(regions: util.Set[String]): ReplicationFilterImpl =
    copy(excludedRegions = excludedRegions.union(regions.asScala))

  def toSpi: SpiEventSourcedEntity.ChangeReplicationFilter =
    new SpiEventSourcedEntity.ChangeReplicationFilter(includedRegions, excludedRegions)

}
