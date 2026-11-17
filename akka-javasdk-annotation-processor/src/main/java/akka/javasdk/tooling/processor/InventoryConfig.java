/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigIncluderClasspath;
import com.typesafe.config.ConfigIncluderFile;
import com.typesafe.config.ConfigIncluderURL;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import java.io.File;
import java.io.Reader;
import java.net.URL;
import java.util.Optional;
import java.util.function.Consumer;

/** Parses and merges the config files that the service inventory reads. */
final class InventoryConfig {

  private InventoryConfig() {}

  /**
   * Parses the config file at {@code url} without its includes. Passes each include to {@code
   * onInclude}, as written in the file.
   */
  static Config parse(URL url, Consumer<String> onInclude) throws InventoryException {
    try {
      return ConfigFactory.parseURL(url, parseOptions(onInclude));
    } catch (ConfigException e) {
      throw new InventoryException("Cannot parse the config: " + e.getMessage(), e);
    }
  }

  /**
   * Parses the config text from {@code reader} without its includes. Uses {@code description} to
   * name the config text in errors. Passes each include to {@code onInclude}, as written in the
   * text.
   */
  static Config parse(Reader reader, String description, Consumer<String> onInclude)
      throws InventoryException {
    try {
      return ConfigFactory.parseReader(
          reader, parseOptions(onInclude).setOriginDescription(description));
    } catch (ConfigException e) {
      throw new InventoryException("Cannot parse the config: " + e.getMessage(), e);
    }
  }

  private static ConfigParseOptions parseOptions(Consumer<String> onInclude) {
    return ConfigParseOptions.defaults()
        .setAllowMissing(false)
        .setIncluder(new SkippingIncluder(onInclude));
  }

  /**
   * Returns {@code application} over {@code sdkDefaults}. The result ignores system properties and
   * env vars and can hold unresolved values.
   */
  static Config merge(Optional<Config> application, Config sdkDefaults) throws InventoryException {
    try {
      return application
          .orElseGet(ConfigFactory::empty)
          .withFallback(sdkDefaults)
          .resolve(ConfigResolveOptions.noSystem().setAllowUnresolved(true));
    } catch (ConfigException e) {
      throw new InventoryException("Cannot resolve the config: " + e.getMessage(), e);
    }
  }

  /** Replaces each include with an empty object and reports it. */
  private record SkippingIncluder(Consumer<String> onInclude)
      implements ConfigIncluder, ConfigIncluderClasspath, ConfigIncluderFile, ConfigIncluderURL {

    @Override
    public ConfigIncluder withFallback(ConfigIncluder fallback) {
      return this;
    }

    @Override
    public ConfigObject include(ConfigIncludeContext context, String what) {
      return skip("\"" + what + "\"");
    }

    @Override
    public ConfigObject includeResources(ConfigIncludeContext context, String what) {
      return skip("classpath(\"" + what + "\")");
    }

    @Override
    public ConfigObject includeFile(ConfigIncludeContext context, File what) {
      return skip("file(\"" + what + "\")");
    }

    @Override
    public ConfigObject includeURL(ConfigIncludeContext context, URL what) {
      return skip("url(\"" + what + "\")");
    }

    private ConfigObject skip(String include) {
      onInclude.accept(include);
      return ConfigFactory.empty().root();
    }
  }
}
