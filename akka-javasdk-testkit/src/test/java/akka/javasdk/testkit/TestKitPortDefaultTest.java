/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Holds the default of {@code akka.javasdk.testkit.http-port} below the two ranges where something
 * else can take a fixed port first.
 *
 * <p>The testkit uses the configured port exactly as configured, so the default has to be a number
 * it can actually bind. An outbound connection can hold a port from the range the operating system
 * draws ephemeral ports from as its source port, at the moment the testkit comes to bind it. The
 * testkit draws the eventing testkit's own port from the band {@link
 * TestKit#EVENTING_PORT_BAND_FIRST} starts, and it draws before the runtime binds the HTTP port, so
 * a default inside that band can collide with it.
 */
class TestKitPortDefaultTest {

  /** The lowest start of an ephemeral range any supported platform has. */
  private static final int LOWEST_EPHEMERAL_RANGE_FIRST = 32768;

  private static final Path LINUX_PORT_RANGE = Path.of("/proc/sys/net/ipv4/ip_local_port_range");

  private static final int DEFAULT_PORT =
      ConfigFactory.defaultReference().getInt("akka.javasdk.testkit.http-port");

  @Test
  void defaultPortNeedsNoPrivileges() {
    assertThat(DEFAULT_PORT)
        .as("the default testkit port has to be outside the privileged range, which ends at 1023")
        .isGreaterThan(1023);
  }

  @Test
  void defaultPortIsBelowTheEventingBand() {
    assertThat(DEFAULT_PORT)
        .as(
            "the default testkit port has to stay below %d, the band the testkit draws the"
                + " eventing testkit port from, or the two can collide",
            TestKit.EVENTING_PORT_BAND_FIRST)
        .isLessThan(TestKit.EVENTING_PORT_BAND_FIRST);
  }

  @Test
  void defaultPortIsBelowTheEphemeralRange() {
    int ephemeralRangeFirst = ephemeralRangeFirst();
    assertThat(DEFAULT_PORT)
        .as(
            "the default testkit port has to stay below %d, where this platform starts drawing"
                + " ephemeral ports, or an outbound connection can hold it when the testkit comes"
                + " to bind it",
            ephemeralRangeFirst)
        .isLessThan(ephemeralRangeFirst);
  }

  private static int ephemeralRangeFirst() {
    if (Files.exists(LINUX_PORT_RANGE)) {
      try {
        // this file reports a size of 0, which makes Files.readString and Files.readAllBytes
        // return one byte of it. Files.readAllLines reads it through a buffered reader instead.
        String range = Files.readAllLines(LINUX_PORT_RANGE).get(0);
        return Integer.parseInt(range.trim().split("\\s+")[0]);
      } catch (IOException e) {
        throw new RuntimeException("Could not read " + LINUX_PORT_RANGE, e);
      }
    }
    return LOWEST_EPHEMERAL_RANGE_FIRST;
  }
}
