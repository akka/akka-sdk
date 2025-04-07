/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

import akka.javasdk.impl.effect.ReplicationFilterImpl;

import java.util.Set;

public interface ReplicationFilter {
  static ReplicationFilter empty() {
    return ReplicationFilterImpl.empty();
  }

  static ReplicationFilter includeRegion(String region) {
    return ReplicationFilterImpl.empty().addRegion(region);
  }

  static ReplicationFilter includeRegions(Set<String> regions) {
    return ReplicationFilterImpl.empty().addRegions(regions);
  }

  static ReplicationFilter excludeRegion(String region) {
    return ReplicationFilterImpl.empty().removeRegion(region);
  }

  static ReplicationFilter excludeRegions(Set<String> regions) {
    return ReplicationFilterImpl.empty().removeRegions(regions);
  }

  ReplicationFilter addRegion(String region);

  ReplicationFilter addRegions(Set<String> regions);

  ReplicationFilter removeRegion(String region);

  ReplicationFilter removeRegions(Set<String> regions);

}
