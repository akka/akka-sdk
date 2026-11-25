/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Renders an {@link Inventory} as JSON. */
final class InventoryJson {

  private static final int FORMAT_VERSION = 1;

  private static final String ENTRY_INDENT = "    ";
  private static final String FIELD_INDENT = "      ";

  private static final String UNKNOWN = "unknown";

  private InventoryJson() {}

  /**
   * Returns the JSON text of the inventory, with components sorted by class and kind, and routes
   * sorted by class, path or method, HTTP method and method.
   */
  static String render(Inventory inventory) {
    var components =
        inventory.components().stream()
            .sorted(
                Comparator.comparing(InventoryComponent::className)
                    .thenComparing(InventoryComponent::kind))
            .toList();

    var routes =
        inventory.routes().stream()
            .sorted(
                Comparator.comparing(InventoryRoute::className)
                    .thenComparing(route -> route.path().or(route::method).orElse(""))
                    .thenComparing(route -> route.httpMethod().orElse(""))
                    .thenComparing(route -> route.method().orElse("")))
            .toList();

    var json = new StringBuilder();
    json.append("{\n");
    json.append("  \"formatVersion\": ").append(FORMAT_VERSION).append(",\n");
    json.append("  \"groupId\": ").append(quote(inventory.groupId())).append(",\n");
    json.append("  \"artifactId\": ").append(quote(inventory.artifactId())).append(",\n");
    json.append("  \"componentLibrary\": ").append(inventory.componentLibrary()).append(",\n");

    renderArray(json, "components", components, InventoryJson::renderComponent);
    json.append(",\n");

    renderArray(json, "routes", routes, InventoryJson::renderRoute);
    json.append(",\n");

    renderArray(json, "guardrails", inventory.guardrails(), InventoryJson::renderGuardrail);
    json.append("\n");

    json.append("}\n");
    return json.toString();
  }

  private static <T> void renderArray(
      StringBuilder json, String key, List<T> entries, BiConsumer<StringBuilder, T> renderEntry) {
    if (entries.isEmpty()) {
      json.append("  ").append(quote(key)).append(": []");
      return;
    }

    json.append("  ").append(quote(key)).append(": [\n");
    for (int i = 0; i < entries.size(); i++) {
      renderEntry.accept(json, entries.get(i));
      json.append(i < entries.size() - 1 ? ",\n" : "\n");
    }
    json.append("  ]");
  }

  private static void renderComponent(StringBuilder json, InventoryComponent component) {
    List<String> fields = new ArrayList<>();
    fields.add(field("kind", component.kind()));
    component.id().ifPresent(id -> fields.add(field("id", id)));
    fields.add(field("class", component.className()));
    component.name().ifPresent(name -> fields.add(field("name", name)));
    component.description().ifPresent(description -> fields.add(field("description", description)));
    component.role().ifPresent(role -> fields.add(field("role", role)));

    renderEntry(json, fields);
  }

  private static void renderRoute(StringBuilder json, InventoryRoute route) {
    List<String> fields = new ArrayList<>();
    fields.add(field("protocol", route.protocol()));
    fields.add(field("class", route.className()));
    route.service().ifPresent(service -> fields.add(field("service", service)));
    route.method().ifPresent(method -> fields.add(field("method", method)));
    route.httpMethod().ifPresent(httpMethod -> fields.add(field("httpMethod", httpMethod)));
    route.path().ifPresent(path -> fields.add(field("path", path)));
    if (route.webSocket()) {
      fields.add(jsonField("webSocket", "true"));
    }
    fields.add(jsonField("acl", acl(route.acl())));
    route.jwt().ifPresent(jwt -> fields.add(jsonField("jwt", jwt(jwt))));

    renderEntry(json, fields);
  }

  private static void renderGuardrail(StringBuilder json, InventoryGuardrail guardrail) {
    List<String> fields = new ArrayList<>();
    fields.add(field("name", guardrail.name()));
    fields.add(
        guardrailField(guardrail, "class", "class", key -> field(key, guardrail.className())));
    fields.add(
        guardrailField(guardrail, "category", "category", key -> field(key, guardrail.category())));
    fields.add(
        guardrailField(
            guardrail, "report-only", "reportOnly", key -> field(key, guardrail.reportOnly())));
    fields.add(
        guardrailField(
            guardrail, "fail-mode", "failMode", key -> field(key, guardrail.failMode())));
    fields.add(
        guardrailField(guardrail, "agents", "agents", key -> field(key, guardrail.agents())));
    fields.add(
        guardrailField(
            guardrail, "agent-roles", "agentRoles", key -> field(key, guardrail.agentRoles())));
    fields.add(guardrailField(guardrail, "tools", "tools", key -> field(key, guardrail.tools())));

    if (guardrail.unknownKeys().contains("control-id")) {
      fields.add(field("controlId", UNKNOWN));
    } else {
      guardrail.controlId().ifPresent(controlId -> fields.add(field("controlId", controlId)));
    }

    renderEntry(json, fields);
  }

  private static void renderEntry(StringBuilder json, List<String> fields) {
    json.append(ENTRY_INDENT).append("{\n");
    json.append(String.join(",\n", fields)).append("\n");
    json.append(ENTRY_INDENT).append("}");
  }

  private static String acl(InventoryRoute.Acl acl) {
    List<String> entries = new ArrayList<>();
    if (acl.denyAll()) {
      entries.add(entry("denyAll", "true"));
    }
    if (!acl.allow().isEmpty()) {
      entries.add(entry("allow", array(acl.allow().stream().map(InventoryJson::matcher).toList())));
    }
    if (!acl.deny().isEmpty()) {
      entries.add(entry("deny", array(acl.deny().stream().map(InventoryJson::matcher).toList())));
    }
    entries.add(entry("denyCode", String.valueOf(acl.denyCode())));
    return object(entries);
  }

  private static String matcher(InventoryRoute.Matcher matcher) {
    List<String> entries = new ArrayList<>();
    entries.add(entry(matcher.key(), quote(matcher.value())));
    if (matcher.parentProject()) {
      entries.add(entry("scope", quote("PARENT_PROJECT")));
    }
    return object(entries);
  }

  private static String jwt(InventoryRoute.Jwt jwt) {
    List<String> entries = new ArrayList<>();
    entries.add(entry("bearerToken", String.valueOf(jwt.bearerToken())));
    if (!jwt.issuers().isEmpty()) {
      entries.add(entry("issuers", strings(jwt.issuers())));
    }
    if (!jwt.staticClaims().isEmpty()) {
      entries.add(
          entry(
              "staticClaims",
              array(jwt.staticClaims().stream().map(InventoryJson::claim).toList())));
    }
    return object(entries);
  }

  private static String claim(InventoryRoute.Claim claim) {
    List<String> entries = new ArrayList<>();
    entries.add(entry("claim", quote(claim.claim())));
    if (!claim.values().isEmpty()) {
      entries.add(entry("values", strings(claim.values())));
    }
    claim.pattern().ifPresent(pattern -> entries.add(entry("pattern", quote(pattern))));
    return object(entries);
  }

  /** Renders the field as {@code "unknown"} when {@code configKey} is unresolved. */
  private static String guardrailField(
      InventoryGuardrail guardrail,
      String configKey,
      String jsonKey,
      Function<String, String> known) {
    if (guardrail.unknownKeys().contains(configKey)) {
      return field(jsonKey, UNKNOWN);
    }
    return known.apply(jsonKey);
  }

  private static String field(String key, String value) {
    return jsonField(key, quote(value));
  }

  private static String field(String key, boolean value) {
    return jsonField(key, String.valueOf(value));
  }

  private static String field(String key, List<String> values) {
    return jsonField(key, strings(values));
  }

  private static String jsonField(String key, String json) {
    return FIELD_INDENT + entry(key, json);
  }

  private static String entry(String key, String json) {
    return quote(key) + ": " + json;
  }

  private static String object(List<String> entries) {
    return "{ " + String.join(", ", entries) + " }";
  }

  private static String array(List<String> items) {
    return "[" + String.join(", ", items) + "]";
  }

  private static String strings(List<String> values) {
    return array(values.stream().map(InventoryJson::quote).toList());
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
