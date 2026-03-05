/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.enforcer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ManifestParsingTest {

  @Test
  void shouldLoadManifestFromClasspath() throws IOException {
    InputStream is =
        getClass()
            .getClassLoader()
            .getResourceAsStream("META-INF/test-runtime-dependencies.properties");
    assertNotNull(is, "Test manifest resource should be found on classpath");

    Properties props = new Properties();
    props.load(is);
    is.close();

    assertEquals(5, props.size());
    assertEquals("33.5.0-jre", props.getProperty("com.google.guava%guava"));
    assertEquals("2.18.3", props.getProperty("com.fasterxml.jackson.core%jackson-databind"));
    assertEquals("1.48.0", props.getProperty("io.opentelemetry%opentelemetry-api"));
    assertEquals("1.72.0", props.getProperty("io.grpc%grpc-api"));
    assertEquals("2.0.17", props.getProperty("org.slf4j%slf4j-api"));
  }
}
