/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigUtil;
import com.typesafe.config.ConfigValue;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds the guardrail values in {@code application.conf} that depend on an optional substitution
 * without a value.
 */
final class OptionalSubstitutions {

  private static final Pattern SUBSTITUTION = Pattern.compile("\\$\\{(\\??)([^}]+)}");

  private static final ConfigValue UNRESOLVED =
      ConfigFactory.parseString("value = ${akka.javasdk.inventory.unresolved}").root().get("value");

  private final Config application;
  private final Config resolved;

  private OptionalSubstitutions(Config application, Config resolved) {
    this.application = application;
    this.resolved = resolved;
  }

  /**
   * Returns {@code merged} with an unresolved value in place of each guardrail value in {@code
   * application} that depends on an optional substitution without a value in {@code resolved}.
   */
  static Config markUnresolved(Config application, Config merged, Config resolved) {
    return new OptionalSubstitutions(application, resolved).mark(merged);
  }

  private Config mark(Config merged) {
    var section =
        rawValue(ConfigUtil.splitPath(GuardrailReader.GUARDRAILS_PATH))
            .flatMap(OptionalSubstitutions::plainObject);
    if (section.isEmpty()) {
      return merged;
    }

    Config marked = merged;
    for (Map.Entry<String, ConfigValue> entry : section.get().entrySet()) {
      String path = GuardrailReader.GUARDRAILS_PATH + "." + ConfigUtil.quoteString(entry.getKey());
      marked = markGuardrail(marked, path, entry.getValue());
    }
    return marked;
  }

  private Config markGuardrail(Config merged, String path, ConfigValue guardrail) {
    var object = plainObject(guardrail);
    if (object.isEmpty()) {
      return markValue(merged, path, guardrail);
    }

    Config marked = merged;
    for (String key : GuardrailReader.KEYS) {
      ConfigValue value = object.get().get(key);
      if (value != null) {
        marked = markValue(marked, path + "." + key, value);
      }
    }
    return marked;
  }

  private Config markValue(Config merged, String path, ConfigValue value) {
    if (dependsOnUnsetOptional(value, ConfigUtil.splitPath(path), new HashSet<>())) {
      return merged.withValue(path, UNRESOLVED.withOrigin(value.origin()));
    }
    return merged;
  }

  private boolean dependsOnUnsetOptional(
      ConfigValue value, List<String> path, Set<List<String>> visited) {
    var substitutions = SUBSTITUTION.matcher(value.render(ConfigRenderOptions.concise()));
    while (substitutions.find()) {
      boolean optional = !substitutions.group(1).isEmpty();
      String target = substitutions.group(2);
      List<String> targetPath = ConfigUtil.splitPath(target);

      if (targetPath.equals(path) || !visited.add(targetPath)) {
        continue;
      }
      if (optional && !hasValue(target)) {
        return true;
      }

      var targetValue = rawValue(targetPath);
      if (targetValue.isPresent()
          && dependsOnUnsetOptional(targetValue.get(), targetPath, visited)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasValue(String path) {
    try {
      return resolved.hasPath(path);
    } catch (ConfigException e) {
      return false;
    }
  }

  private Optional<ConfigValue> rawValue(List<String> path) {
    ConfigValue value = application.root();
    for (String key : path) {
      var object = plainObject(value);
      if (object.isEmpty()) {
        return Optional.empty();
      }

      value = object.get().get(key);
      if (value == null) {
        return Optional.empty();
      }
    }
    return Optional.of(value);
  }

  private static Optional<ConfigObject> plainObject(ConfigValue value) {
    if (!(value instanceof ConfigObject object)) {
      return Optional.empty();
    }

    try {
      object.keySet();
      return Optional.of(object);
    } catch (ConfigException.NotResolved e) {
      return Optional.empty();
    }
  }
}
