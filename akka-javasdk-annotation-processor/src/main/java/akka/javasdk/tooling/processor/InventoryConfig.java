/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigIncluderFile;
import com.typesafe.config.ConfigIncluderURL;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/** Parses and merges the config files that the service inventory reads. */
final class InventoryConfig {

  private static final ConfigResolveOptions RESOLVE_OPTIONS =
      ConfigResolveOptions.noSystem().setAllowUnresolved(true);

  static final String APPLICATION_CONF = "application.conf";
  private static final String REFERENCE_CONF = "reference.conf";

  private InventoryConfig() {}

  /**
   * Returns every {@code application.conf} in {@code loader} over every {@code reference.conf} in
   * {@code loader}. See {@link #merge} for how the result treats unresolved values.
   */
  static Config load(ClassLoader loader) throws InventoryException {
    return merge(parseResource(loader, APPLICATION_CONF), parseResource(loader, REFERENCE_CONF));
  }

  /**
   * Parses every resource named {@code resource} in {@code loader}, with its includes. This method
   * rejects file and URL includes.
   */
  static Config parseResource(ClassLoader loader, String resource) throws InventoryException {
    try {
      return ConfigFactory.parseResources(
          loader, resource, ConfigParseOptions.defaults().setIncluder(new ClasspathIncluder(null)));
    } catch (ConfigException e) {
      throw new InventoryException("Cannot parse the config: " + e.getMessage(), e);
    }
  }

  /**
   * Merges {@code application} over {@code reference} and resolves both together. The result
   * ignores system properties and env vars and can hold unresolved values.
   */
  private static Config merge(Config application, Config reference) throws InventoryException {
    try {
      Config parsed = application.withFallback(reference);
      Config resolved = parsed.resolve(RESOLVE_OPTIONS);
      return OptionalSubstitutions.markUnresolved(parsed, resolved).resolve(RESOLVE_OPTIONS);
    } catch (ConfigException e) {
      throw new InventoryException("Cannot resolve the config: " + e.getMessage(), e);
    }
  }

  /** Rejects file and URL includes. Passes every other include to {@code fallback}. */
  private record ClasspathIncluder(ConfigIncluder fallback)
      implements ConfigIncluder, ConfigIncluderFile, ConfigIncluderURL {

    @Override
    public ConfigIncluder withFallback(ConfigIncluder fallback) {
      return new ClasspathIncluder(fallback);
    }

    @Override
    public ConfigObject include(ConfigIncludeContext context, String what) {
      if (hasScheme(what)) {
        throw rejected("\"" + what + "\"");
      }
      return fallback.include(context, what);
    }

    @Override
    public ConfigObject includeFile(ConfigIncludeContext context, File what) {
      throw rejected("file(\"" + what + "\")");
    }

    @Override
    public ConfigObject includeURL(ConfigIncludeContext context, URL what) {
      throw rejected("url(\"" + what + "\")");
    }

    private static boolean hasScheme(String what) {
      try {
        return new URI(what).getScheme() != null;
      } catch (URISyntaxException e) {
        return false;
      }
    }

    private static ConfigException rejected(String include) {
      return new ConfigException.Generic(
          "The service inventory does not support include "
              + include
              + ". Include a file from the classpath.");
    }
  }
}
