/*
 * Copyright Lightbend Inc.
 */

package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Drives a real Akka service running on the classloader-isolated boot layout (embedded mode — the
 * runtime implementation is loaded behind a shared-surface filter, triggered by the {@code
 * akka.runtime.classpathsFile} property the parent POM sets for surefire/failsafe). Asserts the four
 * things the layout is supposed to guarantee:
 *
 * <ol>
 *   <li>the component serves traffic;
 *   <li>a third-party library (commons-io) that clashes with the runtime's internal copy resolves to
 *       the user-declared version, not the runtime's;
 *   <li>runtime implementation types are invisible to user code while the shared SPI surface is
 *       visible;
 *   <li>a user logback include is processed across the classloader boundary.
 * </ol>
 */
public class IsolatedBootIT extends TestKitSupport {

  private static final String NOT_FOUND = "[NOT FOUND]";

  /** commons-io version the app pins in its POM; the runtime ships a different one internally. */
  private static final String USER_COMMONS_IO_VERSION = "2.11.0";

  @Test
  public void componentServesUnderIsolation() {
    assertEquals("Hello World", get("/clash/hello"));
  }

  @Test
  public void userResolvesItsOwnClashingLibraryVersion() {
    // If isolation were broken, the runtime's commons-io (a different version, on its system
    // loader) would shadow the user's and this would report the runtime's version instead.
    assertEquals(USER_COMMONS_IO_VERSION, get("/clash/lib-version"));
  }

  @Test
  public void runtimeInternalsAreInvisibleToUserCodeButSharedSurfaceIsVisible() {
    // JDK type — always visible.
    assertEquals("java.lang.String", get("/clash/classForName/java.lang.String"));
    // Shared SPI surface (boundary) — visible to user code.
    assertEquals("akka.javasdk.ServiceSetup", get("/clash/classForName/akka.javasdk.ServiceSetup"));
    // Runtime implementation — isolated, must not be reachable from user code.
    assertEquals(NOT_FOUND, get("/clash/classForName/kalix.runtime.AkkaRuntimeMain"));
  }

  @Test
  public void userLogbackIncludeIsAppliedAcrossTheClassloaderBoundary() {
    // include-dev-loggers.xml raises this logger to DEBUG. It only takes effect if the runtime
    // drove logback's Joran engine across the classloader boundary to resolve the user's
    // <include resource=...>; otherwise the logger inherits root (INFO) and DEBUG is not enabled.
    assertTrue(
        LoggerFactory.getLogger("com.example.user-marker").isDebugEnabled(),
        "user logback include did not take effect — DEBUG not enabled for com.example.user-marker");
  }

  private String get(String path) {
    var response = httpClient.GET(path).responseBodyAs(String.class).invoke();
    assertEquals(200, response.status().intValue(), "unexpected status for " + path);
    return response.body();
  }
}
