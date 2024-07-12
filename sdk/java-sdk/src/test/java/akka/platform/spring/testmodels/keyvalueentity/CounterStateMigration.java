/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.keyvalueentity;

import akka.platform.javasdk.JsonMigration;

import java.util.List;

public class CounterStateMigration extends JsonMigration {
  @Override
  public int currentVersion() {
    return 1;
  }

  @Override
  public List<String> supportedClassNames() {
    return List.of("counter-state");
  }
}
