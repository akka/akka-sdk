/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Reads the enabled guardrails from the service config. */
final class GuardrailReader {

  static final String GUARDRAILS_PATH = "akka.javasdk.agent.guardrails";

  private static final String DEFAULT_FAIL_MODE = "fail-closed";

  private static final List<String> FAIL_MODES = List.of("fail-closed", "fail-open");

  private static final int MAX_CONTROL_ID_LENGTH = 256;

  static final List<String> KEYS =
      List.of(
          "agent-roles",
          "agents",
          "category",
          "class",
          "control-id",
          "fail-mode",
          "report-only",
          "tools");

  private GuardrailReader() {}

  /**
   * Returns the guardrails with an {@code agents} or {@code agent-roles} list that is non-empty or
   * unresolved, sorted by name. Passes a warning to {@code onUnknown} for each key with an
   * unresolved value. Returns an empty list when the config has no {@code
   * akka.javasdk.agent.guardrails}.
   */
  static List<InventoryGuardrail> read(Config config, Consumer<String> onUnknown)
      throws InventoryException {
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
      String name = entry.getKey();
      ConfigValue value = entry.getValue();
      if (value instanceof ConfigObject object) {
        Optional<InventoryGuardrail> guardrail = read(new Section(name, object, onUnknown));
        guardrail.ifPresent(result::add);
      } else if (isUnresolved(value)) {
        result.add(unknownGuardrail(name, value, onUnknown));
      }
    }

    result.sort(Comparator.comparing(InventoryGuardrail::name));
    return List.copyOf(result);
  }

  private static boolean isUnresolved(ConfigValue value) {
    try {
      value.valueType();
      return false;
    } catch (ConfigException.NotResolved e) {
      return true;
    }
  }

  private static InventoryGuardrail unknownGuardrail(
      String name, ConfigValue value, Consumer<String> onUnknown) {
    for (String key : KEYS) {
      onUnknown.accept(unknownWarning(name, key, value.origin()));
    }
    return new InventoryGuardrail(
        name,
        "",
        "",
        false,
        DEFAULT_FAIL_MODE,
        List.of(),
        List.of(),
        List.of(),
        Optional.empty(),
        KEYS);
  }

  private static String unknownWarning(String guardrail, String key, ConfigOrigin origin) {
    return "The service inventory lists ["
        + key
        + "] of guardrail ["
        + guardrail
        + "] as unknown. The value in "
        + origin.description()
        + " is unresolved.";
  }

  private static Optional<InventoryGuardrail> read(Section section) throws InventoryException {
    List<String> agents = section.stringList("agents");
    List<String> agentRoles = section.stringList("agent-roles");
    boolean selectorsUnknown = section.isUnknown("agents") || section.isUnknown("agent-roles");
    if (agents.isEmpty() && agentRoles.isEmpty() && !selectorsUnknown) {
      return Optional.empty();
    }

    String className = className(section);
    String category = section.string("category");
    boolean reportOnly = section.bool("report-only", false);
    String failMode = failMode(section);
    List<String> tools = section.stringList("tools");
    Optional<String> controlId = controlId(section);

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
            controlId,
            List.copyOf(section.unknownKeys())));
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

  private static String className(Section section) throws InventoryException {
    String className = section.string("class");
    if (!section.isUnknown("class") && className.isBlank()) {
      throw section.failure("class", "blank value", null);
    }
    return className;
  }

  private static Optional<String> controlId(Section section) throws InventoryException {
    Optional<String> controlId = section.optionalString("control-id");
    if (controlId.isEmpty()) {
      return controlId;
    }

    if (section.object().get("control-id").valueType() != ConfigValueType.STRING) {
      throw section.failure("control-id", "wrong type, expected a string", null);
    }
    if (controlId.get().isBlank()) {
      throw section.failure("control-id", "blank value", null);
    }
    if (controlId.get().length() > MAX_CONTROL_ID_LENGTH) {
      throw section.failure(
          "control-id", "value longer than " + MAX_CONTROL_ID_LENGTH + " characters", null);
    }
    return controlId;
  }

  /** A named guardrail section of the config. It records the keys with unresolved values. */
  private record Section(
      String name, ConfigObject object, Set<String> unknownKeys, Consumer<String> onUnknown) {

    Section(String name, ConfigObject object, Consumer<String> onUnknown) {
      this(name, object, new TreeSet<>(), onUnknown);
    }

    boolean isUnknown(String key) {
      return unknownKeys.contains(key);
    }

    void markUnknown(String key) {
      unknownKeys.add(key);
      onUnknown.accept(unknownWarning(name, key, origin(key)));
    }

    List<String> stringList(String key) throws InventoryException {
      try {
        Config config = object.toConfig();
        if (!config.hasPath(key)) {
          return List.of();
        }
        return config.getStringList(key).stream().distinct().sorted().toList();
      } catch (ConfigException.NotResolved e) {
        markUnknown(key);
        return List.of();
      } catch (ConfigException e) {
        throw wrap(key, "a list of strings", e);
      }
    }

    String string(String key) throws InventoryException {
      try {
        return object.toConfig().getString(key);
      } catch (ConfigException.NotResolved e) {
        markUnknown(key);
        return "";
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
      } catch (ConfigException.NotResolved e) {
        markUnknown(key);
        return Optional.empty();
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
      } catch (ConfigException.NotResolved e) {
        markUnknown(key);
        return defaultValue;
      } catch (ConfigException e) {
        throw wrap(key, "a boolean", e);
      }
    }

    InventoryException wrap(String key, String expected, ConfigException e) {
      String problem =
          switch (e) {
            case ConfigException.Missing missing -> "missing value";
            case ConfigException.WrongType wrongType -> "wrong type, expected " + expected;
            default -> "invalid value";
          };
      return failure(key, problem, e);
    }

    InventoryException failure(String key, String problem, Throwable cause) {
      return new InventoryException(
          "Guardrail ["
              + name
              + "] in "
              + origin(key).description()
              + ": "
              + problem
              + " ["
              + key
              + "]",
          cause);
    }

    ConfigOrigin origin(String key) {
      try {
        ConfigValue value = object.get(key);
        return value != null ? value.origin() : object.origin();
      } catch (ConfigException.NotResolved e) {
        return object.origin();
      }
    }
  }
}
