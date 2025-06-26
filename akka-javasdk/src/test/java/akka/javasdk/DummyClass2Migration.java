/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import akka.javasdk.JsonMigration;

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
