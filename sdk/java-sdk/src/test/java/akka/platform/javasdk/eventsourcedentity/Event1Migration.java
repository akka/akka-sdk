/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.eventsourcedentity;

import akka.platform.javasdk.JsonMigration;

import java.util.List;

public class Event1Migration extends JsonMigration {
  @Override
  public int currentVersion() {
    return 1;
  }

  @Override
  public List<String> supportedClassNames() {
    return List.of(OldTestESEvent.OldEvent1.class.getName());
  }
}
