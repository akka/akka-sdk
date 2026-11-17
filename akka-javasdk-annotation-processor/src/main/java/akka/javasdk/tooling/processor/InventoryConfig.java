/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import java.net.URL;
import java.util.Optional;

/** Parses and merges the config files that the service inventory reads. */
final class InventoryConfig {

  private InventoryConfig() {}

  /** Parses the config file at {@code url}, with the files it includes by a relative path. */
  static Config parse(URL url) throws InventoryException {
    try {
      return ConfigFactory.parseURL(url, ConfigParseOptions.defaults().setAllowMissing(false));
    } catch (ConfigException e) {
      throw new InventoryException("Cannot parse the config: " + e.getMessage(), e);
    }
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
}
