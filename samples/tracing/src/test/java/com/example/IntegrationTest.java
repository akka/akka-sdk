package com.example;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static java.time.temporal.ChronoUnit.SECONDS;


public class IntegrationTest extends TestKitSupport {

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
