/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.Context
import akka.runtime.sdk.spi.RegionInfo

/**
 * INTERNAL API
 */
@InternalApi
private[akka] trait InternalContext {

  /**
   * Intended to be used by component calls, initially to give to the called component access to the trace parent from
   * the caller. It's empty by default because only actions and workflows can to call other components. Of the two, only
   * actions have traces and can pass them around using `protected final Component components()`.
   */
  def componentCallMetadata: MetadataImpl = MetadataImpl.Empty
}

/**
 * INTERNAL API
 */
@InternalApi
abstract class AbstractContext(regionInfo: RegionInfo) extends Context with InternalContext {
  override final def selfRegion(): String = regionInfo.selfRegion

  // reads the region info on each call: an entity can keep the context from its constructor while regions go down
  override final def downedRegions(): java.util.Set[String] = regionInfo.downedRegions().asJava
}
