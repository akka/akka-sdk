/*
 * Copyright Lightbend Inc.
 */

package com.example;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Verifies the staged deployment layout produced by {@code prepareRuntimeLayout} (bound to the
 * {@code package} phase, so it has run by the time failsafe executes). This is the half of the
 * layout the embedded {@link IsolatedBootIT} does not exercise: the {@code user/} image layer and
 * the runtime/user partition that the docker assembly and the deploy-time {@code bash-template}
 * launch consume.
 *
 * <p>The decisive cross-check: the same library (commons-io) appears as the user-declared version in
 * the staged {@code user/} layer and as the runtime's own version in the runtime classpath — two
 * versions, two layers, which is the entire point of the isolated boot.
 */
public class LayoutVerificationIT {

  private static final Path TARGET = Path.of("target");
  private static final Path IMAGE = TARGET.resolve("runtime-image");
  private static final Path USER_LAYER = IMAGE.resolve("user");

  @Test
  public void stagedUserLayerContainsAppAndUserVersionOfClashingLibrary() throws IOException {
    assertTrue(Files.isDirectory(USER_LAYER), "staged user/ layer not found at " + USER_LAYER);

    List<String> jars = jarNames(USER_LAYER);

    assertTrue(
        jars.stream().anyMatch(n -> n.startsWith("isolated-boot-it") && n.endsWith(".jar")),
        "app jar missing from user layer: " + jars);
    assertTrue(
        jars.contains("commons-io-2.11.0.jar"),
        "user-declared commons-io 2.11.0 missing from user layer: " + jars);
    // No runtime implementation jars belong in the user layer either.
    assertFalse(
        jars.stream().anyMatch(n -> n.startsWith("akka-runtime-core")),
        "runtime implementation jar leaked into the user layer: " + jars);

    assertTrue(
        Files.isRegularFile(IMAGE.resolve("user-classpaths.properties")),
        "user-classpaths.properties not staged");
  }

  @Test
  public void runtimeClasspathCarriesItsOwnVersionOfTheClashingLibrary() throws IOException {
    Path runtimeProps = TARGET.resolve("akka-runtime").resolve("runtime-classpaths.properties");
    assertTrue(Files.isRegularFile(runtimeProps), "runtime-classpaths.properties not generated");

    Properties props = new Properties();
    try (var in = Files.newInputStream(runtimeProps)) {
      props.load(in);
    }
    String system = props.getProperty("akka.runtime.classpath.system", "");
    // The runtime keeps its own commons-io on its isolated implementation classpath — a different
    // version from the user's 2.11.0, proving the two are partitioned rather than reconciled.
    assertFalse(
        system.contains("commons-io-2.11.0.jar"),
        "user's commons-io 2.11.0 leaked onto the runtime system classpath");
  }

  private static List<String> jarNames(Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      return files.map(p -> p.getFileName().toString()).sorted().toList();
    }
  }
}
