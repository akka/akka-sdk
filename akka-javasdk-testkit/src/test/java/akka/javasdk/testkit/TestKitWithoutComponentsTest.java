/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.impl.ComponentLocator$;
import org.junit.jupiter.api.Test;

/** Starts the TestKit on the test classpath of this module, which has no component descriptor. */
class TestKitWithoutComponentsTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withServiceName("without-components").withEphemeralPort();
  }

  @Test
  void classpathHasNoComponentDescriptor() {
    var descriptors = ComponentLocator$.MODULE$.findAllDescriptorFiles(getClass().getClassLoader());
    assertThat(descriptors.isEmpty()).isTrue();
  }

  @Test
  void startsWithoutComponents() {
    assertThat(testKit.getPort()).isGreaterThan(0);
  }
}
