/*
 * Copyright Lightbend Inc.
 */

package com.example;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Asserts that depending on the testkit does <em>not</em> drag the akka-runtime implementation — or
 * the runtime's internal dependencies — onto the consumer's test classpath. This is the consumer
 * side of the SDK change that makes {@code akka-runtime-dev} a non-transitive {@code Provided}
 * dependency of the testkit: a project that only depends on the testkit gets the thin boot launcher
 * and the SDK, while the runtime implementation stays isolated and is loaded reflectively at runtime.
 *
 * <p>Reachability is checked against <em>this test class's own classloader</em> — i.e. the surefire/
 * failsafe test classpath, the classpath that depends on the testkit. The thread context classloader
 * is deliberately not used: while a runtime is running it is an isolated boot loader that can see the
 * runtime, which is not what this test is about.
 *
 * <p>(Named to avoid a leading {@code Test}, which surefire's default {@code Test*.java} include
 * would otherwise also pick up as a unit test, running it twice.)
 */
public class ConsumerClasspathIsolationIT {

  private final ClassLoader testClasspath = getClass().getClassLoader();

  @Test
  public void runtimeImplementationIsNotOnTheTestClasspath() {
    // akka-runtime-core (the runtime implementation jar) must not be a dependency of the testkit.
    assertThrows(
        ClassNotFoundException.class,
        () -> load("kalix.runtime.AkkaRuntimeMain"),
        "runtime implementation leaked onto the test classpath");
  }

  @Test
  public void representativeRuntimeInternalDependencyIsNotOnTheTestClasspath() {
    // h2 is a runtime-internal dependency (on the runtime's isolated system classpath); it has no
    // business on a classpath that merely depends on the testkit.
    assertThrows(
        ClassNotFoundException.class,
        () -> load("org.h2.Driver"),
        "runtime-internal dependency (h2) leaked onto the test classpath");
  }

  @Test
  public void testkitAndThinBootLauncherAndSharedSurfaceAreOnTheTestClasspath() {
    // Positive controls — these are exactly what a testkit consumer is supposed to get:
    assertDoesNotThrow(
        () -> load("akka.javasdk.testkit.TestKit"), "testkit missing from the test classpath");
    // The boot launcher is pure URLClassLoader plumbing with no runtime-impl deps, so it is allowed
    // on the test classpath (the testkit uses it to start the runtime in embedded isolated mode).
    assertDoesNotThrow(
        () -> load("akka.runtime.boot.EmbeddedAkkaRuntimeMain"),
        "boot launcher missing from the test classpath");
    // Shared SPI surface is visible to user/test code by design.
    assertDoesNotThrow(
        () -> load("akka.javasdk.ServiceSetup"), "shared SPI surface missing from the test classpath");
  }

  private Class<?> load(String fqn) throws ClassNotFoundException {
    return Class.forName(fqn, false, testClasspath);
  }
}
