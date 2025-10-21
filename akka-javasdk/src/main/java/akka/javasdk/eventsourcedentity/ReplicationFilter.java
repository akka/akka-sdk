/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

import akka.javasdk.impl.effect.ReplicationFilterImpl;
import java.util.Set;

/**
 * Replication filter for controlling which regions participate in event replication for an Event
 * Sourced Entity. To enable this feature you must add {@code @EnableReplicationFilter} annotation
 * to the {@code EventSourcedEntity} class, and use the {@code updateReplicationFilter} in the
 * entity effects.
 *
 * <p>Events are by default replicated to all regions that have been enabled for the service. For
 * regulatory reasons or as cost optimization, it is possible to filter which regions participate in
 * the replication for a specific entity. This can be changed at runtime by the entity itself.
 *
 * <p>The replication filter can only be updated from the primary region of the entity, or the
 * entity will become the primary if using the {@code request-region} primary selection strategy.
 * The filter is durable for the specific entity instance and can be changed without deploying a new
 * version.
 *
 * <p>When you define the replication filter, you specify the regions to be included or excluded in
 * the replication. The region where the update is made (the self region) is automatically included
 * in the replication filter. The changes are additive, meaning that if you first update the filter
 * to include one region and then later make another update to include a different region from the
 * same entity, both regions are included.
 *
 * <p>After enabling replication filter with the {@code @EnableReplicationFilter} annotation, the
 * entity will still replicate to all regions until the regions are defined with the {@code
 * updateReplicationFilter} effect. This effect can be combined with persisting events or used
 * without additional events.
 */
public interface ReplicationFilter {
  /**
   * Creates an empty replication filter.
   *
   * @return an empty replication filter
   */
  static ReplicationFilter empty() {
    return ReplicationFilterImpl.empty();
  }

  /**
   * Creates a replication filter that includes the specified region.
   *
   * @param region the region to include in the replication filter
   * @return a replication filter with the specified region included
   */
  static ReplicationFilter includeRegion(String region) {
    return ReplicationFilterImpl.empty().addRegion(region);
  }

  /**
   * Creates a replication filter that includes the specified regions.
   *
   * @param regions the regions to include in the replication filter
   * @return a replication filter with the specified regions included
   */
  static ReplicationFilter includeRegions(Set<String> regions) {
    return ReplicationFilterImpl.empty().addRegions(regions);
  }

  /**
   * Creates a replication filter that excludes the specified region.
   *
   * @param region the region to exclude from the replication filter
   * @return a replication filter with the specified region excluded
   */
  static ReplicationFilter excludeRegion(String region) {
    return ReplicationFilterImpl.empty().removeRegion(region);
  }

  /**
   * Creates a replication filter that excludes the specified regions.
   *
   * @param regions the regions to exclude from the replication filter
   * @return a replication filter with the specified regions excluded
   */
  static ReplicationFilter excludeRegions(Set<String> regions) {
    return ReplicationFilterImpl.empty().removeRegions(regions);
  }

  /**
   * Adds a region to this replication filter. The change is additive to any previously included
   * regions.
   *
   * @param region the region to add
   * @return a new replication filter with the region added
   */
  ReplicationFilter addRegion(String region);

  /**
   * Adds multiple regions to this replication filter. The changes are additive to any previously
   * included regions.
   *
   * @param regions the regions to add
   * @return a new replication filter with the regions added
   */
  ReplicationFilter addRegions(Set<String> regions);

  /**
   * Removes a region from this replication filter.
   *
   * @param region the region to remove
   * @return a new replication filter with the region removed
   */
  ReplicationFilter removeRegion(String region);

  /**
   * Removes multiple regions from this replication filter.
   *
   * @param regions the regions to remove
   * @return a new replication filter with the regions removed
   */
  ReplicationFilter removeRegions(Set<String> regions);
}
