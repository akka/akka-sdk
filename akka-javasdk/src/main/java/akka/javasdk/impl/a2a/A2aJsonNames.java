/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;

/**
 * INTERNAL API
 *
 * <p>ProtoJSON parsers accept a field under its proto name, such as {@code task_id}, as well as its
 * JSON name, {@code taskId}. Clients built on the proto, such as the A2A compatibility kit, send
 * the proto names in request parameters.
 */
@InternalApi
public final class A2aJsonNames {

  private A2aJsonNames() {}

  /**
   * Renames the snake_case fields of an object to their camelCase JSON names, in place. Nested
   * objects keep their fields, because some of them, such as metadata, are free form.
   *
   * @return the same node
   */
  public static JsonNode withJsonNames(JsonNode node) {
    if (node instanceof ObjectNode object) {
      for (String name : fieldNames(object)) {
        if (name.indexOf('_') > 0) {
          var jsonName = jsonName(name);
          if (!object.has(jsonName)) object.set(jsonName, object.get(name));
          object.remove(name);
        }
      }
    }
    return node;
  }

  static String jsonName(String protoName) {
    var result = new StringBuilder(protoName.length());
    boolean upper = false;
    for (char c : protoName.toCharArray()) {
      if (c == '_') {
        upper = true;
      } else {
        result.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return result.toString();
  }

  private static java.util.List<String> fieldNames(ObjectNode object) {
    var names = new ArrayList<String>();
    object.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
