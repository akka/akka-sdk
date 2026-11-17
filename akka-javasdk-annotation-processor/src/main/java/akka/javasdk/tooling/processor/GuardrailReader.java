/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Reads the enabled guardrails from the service config. */
final class GuardrailReader {

  private static final String GUARDRAILS_PATH = "akka.javasdk.agent.guardrails";

  private static final String DEFAULT_FAIL_MODE = "fail-closed";

  private static final List<String> FAIL_MODES = List.of("fail-closed", "fail-open");

  private GuardrailReader() {}

  /**
   * Returns the guardrails with a non-empty {@code agents} or {@code agent-roles} list, sorted by
   * name. Returns an empty list when the config has no {@code akka.javasdk.agent.guardrails}.
   */
  static List<InventoryGuardrail> read(Config config) throws InventoryException {
    ConfigObject guardrails;
    try {
      if (!config.hasPath(GUARDRAILS_PATH)) {
        return List.of();
      }
      guardrails = config.getConfig(GUARDRAILS_PATH).root();
    } catch (ConfigException e) {
      throw new InventoryException("Cannot read the guardrails: " + e.getMessage(), e);
    }

    List<InventoryGuardrail> result = new ArrayList<>();
    for (Map.Entry<String, ConfigValue> entry : guardrails.entrySet()) {
      if (entry.getValue() instanceof ConfigObject object) {
        Optional<InventoryGuardrail> guardrail = read(new Section(entry.getKey(), object));
        guardrail.ifPresent(result::add);
      }
    }

    result.sort(Comparator.comparing(InventoryGuardrail::name));
    return List.copyOf(result);
  }

  private static Optional<InventoryGuardrail> read(Section section) throws InventoryException {
    List<String> agents = section.stringList("agents");
    List<String> agentRoles = section.stringList("agent-roles");
    if (agents.isEmpty() && agentRoles.isEmpty()) {
      return Optional.empty();
    }

    String className = section.string("class");
    String category = section.string("category");
    boolean reportOnly = section.bool("report-only", false);
    String failMode = failMode(section);
    List<String> tools = section.stringList("tools");
    Optional<String> controlId = section.optionalString("control-id");

    return Optional.of(
        new InventoryGuardrail(
            section.name(),
            className,
            category,
            reportOnly,
            failMode,
            agents,
            agentRoles,
            tools,
            controlId));
  }

  private static String failMode(Section section) throws InventoryException {
    Optional<String> configured = section.optionalString("fail-mode");
    if (configured.isEmpty()) {
      return DEFAULT_FAIL_MODE;
    }

    String failMode = configured.get().toLowerCase(Locale.ROOT);
    if (!FAIL_MODES.contains(failMode)) {
      throw section.failure(
          "fail-mode",
          "unknown value \"" + configured.get() + "\", expected fail-closed or fail-open",
          null);
    }
    return failMode;
  }

  /** A named guardrail section of the config. */
  private record Section(String name, ConfigObject object) {

    List<String> stringList(String key) throws InventoryException {
      try {
        Config config = object.toConfig();
        if (!config.hasPath(key)) {
          return List.of();
        }
        return config.getStringList(key).stream().sorted().toList();
      } catch (ConfigException e) {
        throw wrap(key, "a list of strings", e);
      }
    }

    String string(String key) throws InventoryException {
      try {
        return object.toConfig().getString(key);
      } catch (ConfigException e) {
        throw wrap(key, "a string", e);
      }
    }

    Optional<String> optionalString(String key) throws InventoryException {
      try {
        Config config = object.toConfig();
        if (!config.hasPath(key)) {
          return Optional.empty();
        }
        return Optional.of(config.getString(key));
      } catch (ConfigException e) {
        throw wrap(key, "a string", e);
      }
    }

    boolean bool(String key, boolean defaultValue) throws InventoryException {
      try {
        Config config = object.toConfig();
        if (!config.hasPath(key)) {
          return defaultValue;
        }
        return config.getBoolean(key);
      } catch (ConfigException e) {
        throw wrap(key, "a boolean", e);
      }
    }

    InventoryException wrap(String key, String expected, ConfigException e) {
      String problem =
          switch (e) {
            case ConfigException.Missing missing -> "missing value";
            case ConfigException.NotResolved notResolved -> "unresolved substitution";
            case ConfigException.WrongType wrongType -> "wrong type, expected " + expected;
            default -> "invalid value";
          };
      return failure(key, problem, e);
    }

    InventoryException failure(String key, String problem, Throwable cause) {
      ConfigValue value = object.get(key);
      ConfigOrigin origin = value != null ? value.origin() : object.origin();
      return new InventoryException(
          "Guardrail [" + name + "] in " + origin.description() + ": " + problem + " [" + key + "]",
          cause);
    }
  }
}
