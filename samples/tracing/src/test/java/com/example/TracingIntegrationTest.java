package com.example;

import static java.time.temporal.ChronoUnit.SECONDS;

import akka.javasdk.testkit.TestKitSupport;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class TracingIntegrationTest extends TestKitSupport {

  private Duration timeout = Duration.of(5, SECONDS);

  @Test
  public void testTracingPropagation() {
    // TODO
  }

  @Test
  public void testExternalTracingPropagation() {
    // TODO
  }
}
