/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.eventsourcedentity;

import akka.platform.javasdk.JsonMigration;

import java.util.List;

public class EmployeeCreatedMigration extends JsonMigration {
  @Override
  public int currentVersion() {
    return 1;
  }

  @Override
  public List<String> supportedClassNames() {
    return List.of("old-created");
  }
}
