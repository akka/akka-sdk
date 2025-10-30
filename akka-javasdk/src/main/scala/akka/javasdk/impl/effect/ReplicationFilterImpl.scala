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
private[javasdk] final case class ReplicationFilterImpl(addRegions: Set[String], removeRegions: Set[String])
    extends akka.javasdk.eventsourcedentity.ReplicationFilter {
  override def addRegion(region: String): ReplicationFilterImpl =
    copy(addRegions = addRegions + region)

  override def addRegions(regions: util.Set[String]): ReplicationFilterImpl =
    copy(addRegions = addRegions.union(regions.asScala))

  override def removeRegion(region: String): ReplicationFilterImpl =
    copy(removeRegions = removeRegions + region)

  override def removeRegions(regions: util.Set[String]): ReplicationFilterImpl =
    copy(removeRegions = removeRegions.union(regions.asScala))

  def toSpi: SpiEventSourcedEntity.ChangeReplicationFilter =
    new SpiEventSourcedEntity.ChangeReplicationFilter(addRegions, removeRegions)

}
