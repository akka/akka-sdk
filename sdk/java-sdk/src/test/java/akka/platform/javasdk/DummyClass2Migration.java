/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import akka.platform.javasdk.JsonMigration;

public class DummyClass2Migration extends JsonMigration {
  @Override
  public int currentVersion() {
    return 1;
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    if (fromVersion < 1) {
      return ((ObjectNode) json).set("mandatoryStringValue", TextNode.valueOf("mandatory-value"));
    } else {
      return json;
    }
  }
}
