/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

import akka.javasdk.impl.effect.ReplicationFilterImpl;

public interface ReplicationFilter {
  static ReplicationFilter empty() {
    return ReplicationFilterImpl.empty();
  }

  static ReplicationFilter includeRegion(String region) {
    return ReplicationFilterImpl.empty().addRegion(region);
  }

  static ReplicationFilter excludeRegion(String region) {
    return ReplicationFilterImpl.empty().removeRegion(region);
  }

  ReplicationFilter addRegion(String region);

  ReplicationFilter removeRegion(String region);

}
