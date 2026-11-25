/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Reads the compile classpath from the compiler option that the build sets. */
final class InventoryClasspath {

  private static final String SEPARATOR = ", ";

  private InventoryClasspath() {}

  /** Returns the entries of {@code value}, a list written as {@code [first, second]}. */
  static List<Path> parse(String option, String value) throws InventoryException {
    if (!value.startsWith("[") || !value.endsWith("]")) {
      throw new InventoryException(
          "Compiler option '" + option + "' must be a list of paths but was: " + value);
    }

    var entries = value.substring(1, value.length() - 1);
    if (entries.isEmpty()) {
      return List.of();
    }

    var paths = new ArrayList<Path>();
    for (var entry : entries.split(SEPARATOR)) {
      var path = Path.of(entry);
      if (entry.endsWith(".jar") && !Files.isRegularFile(path)) {
        throw new InventoryException(
            "Compiler option '" + option + "' lists a jar that does not exist: " + entry);
      }
      paths.add(path);
    }
    return List.copyOf(paths);
  }

  /**
   * Returns a class loader that searches {@code outputDirectory}, when present, and then {@code
   * classpath}. Searches each location once.
   */
  static URLClassLoader classLoader(Optional<Path> outputDirectory, List<Path> classpath)
      throws IOException {
    var locations = new LinkedHashSet<Path>();
    if (outputDirectory.isPresent()) {
      locations.add(canonical(outputDirectory.get()));
    }
    for (var entry : classpath) {
      locations.add(canonical(entry));
    }

    var urls = new ArrayList<URL>();
    for (var location : locations) {
      urls.add(location.toUri().toURL());
    }
    return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
  }

  private static Path canonical(Path path) throws IOException {
    return Files.exists(path) ? path.toRealPath() : path.toAbsolutePath().normalize();
  }
}
