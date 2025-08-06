/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.workflow;

import akka.javasdk.DummyClass2;
import akka.javasdk.JsonMigration;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public class DummyClassRenamedMigration extends JsonMigration {
  @Override
  public int currentVersion() {
    return 1;
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    return json;
  }

  @Override
  public List<String> supportedClassNames() {
    return List.of(DummyClass2.class.getName());
  }
}
