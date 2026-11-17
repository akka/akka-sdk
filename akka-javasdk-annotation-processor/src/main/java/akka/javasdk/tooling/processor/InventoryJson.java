/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Renders an {@link Inventory} as JSON. */
final class InventoryJson {

  private static final int FORMAT_VERSION = 1;

  private static final String COMPONENT_INDENT = "    ";
  private static final String FIELD_INDENT = "      ";

  private InventoryJson() {}

  /** Returns the JSON text of the inventory, with components sorted by class and then by kind. */
  static String render(Inventory inventory) {
    var components =
        inventory.components().stream()
            .sorted(
                Comparator.comparing(InventoryComponent::className)
                    .thenComparing(InventoryComponent::kind))
            .toList();

    var json = new StringBuilder();
    json.append("{\n");
    json.append("  \"formatVersion\": ").append(FORMAT_VERSION).append(",\n");
    json.append("  \"groupId\": ").append(quote(inventory.groupId())).append(",\n");
    json.append("  \"artifactId\": ").append(quote(inventory.artifactId())).append(",\n");
    json.append("  \"componentLibrary\": ").append(inventory.componentLibrary()).append(",\n");

    if (components.isEmpty()) {
      json.append("  \"components\": []\n");
    } else {
      json.append("  \"components\": [\n");
      for (int i = 0; i < components.size(); i++) {
        renderComponent(json, components.get(i));
        json.append(i < components.size() - 1 ? ",\n" : "\n");
      }
      json.append("  ]\n");
    }

    json.append("}\n");
    return json.toString();
  }

  private static void renderComponent(StringBuilder json, InventoryComponent component) {
    List<String> fields = new ArrayList<>();
    fields.add(field("kind", component.kind()));
    component.id().ifPresent(id -> fields.add(field("id", id)));
    fields.add(field("class", component.className()));
    component.name().ifPresent(name -> fields.add(field("name", name)));
    component.description().ifPresent(description -> fields.add(field("description", description)));
    component.role().ifPresent(role -> fields.add(field("role", role)));

    json.append(COMPONENT_INDENT).append("{\n");
    json.append(String.join(",\n", fields)).append("\n");
    json.append(COMPONENT_INDENT).append("}");
  }

  private static String field(String key, String value) {
    return FIELD_INDENT + quote(key) + ": " + quote(value);
  }

  private static String quote(String value) {
    var quoted = new StringBuilder(value.length() + 2);
    quoted.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> quoted.append("\\\"");
        case '\\' -> quoted.append("\\\\");
        case '\n' -> quoted.append("\\n");
        case '\r' -> quoted.append("\\r");
        case '\t' -> quoted.append("\\t");
        default -> {
          if (c < 0x20) {
            quoted
                .append("\\u00")
                .append(Character.forDigit(c >> 4, 16))
                .append(Character.forDigit(c & 0xF, 16));
          } else {
            quoted.append(c);
          }
        }
      }
    }
    quoted.append('"');
    return quoted.toString();
  }
}
