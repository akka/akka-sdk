/*
 * Copyright Lightbend Inc.
 */

package com.example;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;

/**
 * Observes the {@code commons-io} the <em>user</em> classloader actually resolves.
 *
 * <p>This is the crux of the integration test. The akka-runtime image ships its own {@code
 * commons-io} on its (isolated) implementation classpath — a <em>different</em> version from the one
 * this application declares. On a flat classpath the two would collide and one version would win for
 * both runtime and user code. Under classloader isolation the runtime keeps its copy on the system
 * loader while user code resolves the version declared in this module's POM, and both coexist in a
 * single JVM. The test asserts the version seen here is the user-declared one.
 */
final class ClashProbe {

  private static final Pattern COMMONS_IO_JAR =
      Pattern.compile("commons-io-([0-9][0-9A-Za-z.\\-]*)\\.jar");

  private ClashProbe() {}

  /**
   * The {@code commons-io} version backing the {@link IOUtils} class as loaded by user code,
   * derived from the code source (jar) the class was actually defined from. Also exercises the
   * library functionally so we know it is the user's working copy, not merely present.
   */
  static String userVisibleLibraryVersion() {
    // Functional use of the user's copy — proves it loads and runs, not just resolves.
    try {
      String roundTripped =
          IOUtils.toString(
              new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)),
              StandardCharsets.UTF_8);
      if (!"ok".equals(roundTripped)) {
        throw new IllegalStateException("commons-io IOUtils did not behave as expected");
      }
    } catch (IOException e) {
      throw new IllegalStateException("commons-io IOUtils failed", e);
    }

    URL location = IOUtils.class.getProtectionDomain().getCodeSource().getLocation();
    if (location == null) {
      return "[UNKNOWN]";
    }
    Matcher m = COMMONS_IO_JAR.matcher(location.getPath());
    return m.find() ? m.group(1) : "[UNKNOWN:" + location.getPath() + "]";
  }
}
