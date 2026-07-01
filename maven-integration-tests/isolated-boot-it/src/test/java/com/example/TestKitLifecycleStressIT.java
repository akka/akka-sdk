/*
 * Copyright Lightbend Inc.
 */

package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import akka.javasdk.http.HttpClient;
import akka.javasdk.testkit.TestKit;
import com.sun.management.UnixOperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stress test for the {@link TestKit} start/stop lifecycle under the classloader-isolated boot
 * layout. {@link TestKitSupport} only ever starts and stops one runtime per test class, so it never
 * exercises the path that matters here: that each {@code start()} / {@code stop()} cycle releases the
 * OS resources it acquired rather than accumulating them. Isolated boot makes this especially worth
 * pinning down, since every cycle spins up (and is supposed to tear down) an isolated boot
 * classloader, its actor system, and the HTTP/eventing servers bound to it.
 *
 * <p>The test runs many full cycles back to back and samples two resources around a measured window:
 *
 * <ul>
 *   <li><b>File descriptors</b> — the headline check, and held to a tight bound. A genuine fd leak
 *       (an unclosed server socket, a runtime that doesn't release its listeners on {@code stop()})
 *       grows roughly one-per-cycle and blows past the slack over {@link #MEASURED_CYCLES} cycles;
 *       transient JDK fds (a lazily-opened jar/zip cache) stay well under it. In practice this sits
 *       at zero growth.
 *   <li><b>Threads</b> — also held to a tight, fixed bound. Embedded-isolated boot loads each
 *       runtime behind its <em>own</em> classloader, so libraries that cache process-global pools in
 *       static fields get a fresh copy per cycle. The one that bit here was Reactor: its {@code
 *       parallel}/{@code single} schedulers (core-count-sized daemon pools) are pulled in by the
 *       persistence plugin's r2dbc connection pool and live in Reactor's static cache, not the actor
 *       system, so actor-system shutdown left them running — a steady {@code availableProcessors + 1}
 *       lingering threads per cycle. {@code EmbeddedAkkaRuntimeMain} now disposes this runtime's
 *       Reactor schedulers when its {@code ActorSystem} terminates (alongside closing the runtime
 *       classloader), so a full cycle leaves no thread behind. The bound is therefore a small fixed
 *       slack like the fd one — below one-thread-per-cycle so any per-classloader pool that creeps
 *       back trips it, above incidental JVM jitter (a transient fork-join/reaper thread). A thread
 *       histogram is logged either way for diagnosis.
 * </ul>
 */
public class TestKitLifecycleStressIT {

  private static final Logger log = LoggerFactory.getLogger(TestKitLifecycleStressIT.class);

  /**
   * Cycles in the measured window. Large enough that a per-cycle leak accumulates well past the
   * slack, small enough to keep the IT's wall-clock reasonable (each cycle is a full runtime boot).
   */
  private static final int MEASURED_CYCLES = 12;

  /**
   * A handful of warm-up cycles run before the baseline sample. The first boots load classes,
   * populate caches and grow pools to their steady-state size — one-off costs that are not leaks.
   * Sampling the baseline only after warm-up isolates per-cycle growth from these fixed costs.
   */
  private static final int WARMUP_CYCLES = 2;

  /**
   * Permitted file-descriptor growth across the whole measured window. Steady state is flat; the
   * slack absorbs lazily-opened JDK fds without masking a real per-cycle leak, which over {@link
   * #MEASURED_CYCLES} cycles would dwarf this.
   */
  private static final int FD_SLACK = 64;

  /**
   * Permitted thread growth across the whole measured window. With the per-runtime Reactor
   * schedulers now disposed on {@code ActorSystem} termination (see class doc), steady state is flat,
   * so this is a tight fixed slack like {@link #FD_SLACK} rather than a per-cycle, core-scaled model.
   * It is deliberately well below {@link #MEASURED_CYCLES}: any pool that leaks even one thread per
   * cycle grows past it, while it still absorbs incidental JVM jitter (a transient fork-join or
   * process-reaper thread) that is unrelated to the runtime lifecycle.
   */
  private static final int THREAD_SLACK = 8;

  @Test
  public void repeatedStartStopDoesNotLeakFileDescriptorsOrThreads() {
    OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    assumeTrue(
        os instanceof UnixOperatingSystemMXBean,
        "file-descriptor accounting requires a Unix OperatingSystemMXBean");
    UnixOperatingSystemMXBean unixOs = (UnixOperatingSystemMXBean) os;
    ThreadMXBean threads = ManagementFactory.getThreadMXBean();

    for (int i = 0; i < WARMUP_CYCLES; i++) {
      runOneCycle("warmup-" + i);
    }
    settle();

    long baselineFds = unixOs.getOpenFileDescriptorCount();
    int baselineThreads = threads.getThreadCount();
    log.info(
        "Baseline after {} warm-up cycles: {} fds, {} threads",
        WARMUP_CYCLES, baselineFds, baselineThreads);

    for (int i = 0; i < MEASURED_CYCLES; i++) {
      runOneCycle("measured-" + i);
    }
    settle();

    long fdsAfter = unixOs.getOpenFileDescriptorCount();
    int threadsAfter = threads.getThreadCount();
    long fdGrowth = fdsAfter - baselineFds;
    int threadGrowth = threadsAfter - baselineThreads;
    int threadSlack = THREAD_SLACK;
    log.info(
        "After {} measured cycles: {} fds (+{}, slack {}), {} threads (+{}, slack {})",
        MEASURED_CYCLES, fdsAfter, fdGrowth, FD_SLACK, threadsAfter, threadGrowth, threadSlack);
    logThreadHistogram();

    assertTrue(
        fdGrowth <= FD_SLACK,
        () ->
            "file-descriptor leak across "
                + MEASURED_CYCLES
                + " start/stop cycles: grew by "
                + fdGrowth
                + " (baseline "
                + baselineFds
                + " -> "
                + fdsAfter
                + "), slack "
                + FD_SLACK);
    assertTrue(
        threadGrowth <= threadSlack,
        () ->
            "thread leak across "
                + MEASURED_CYCLES
                + " start/stop cycles: grew by "
                + threadGrowth
                + " (baseline "
                + baselineThreads
                + " -> "
                + threadsAfter
                + "), slack "
                + threadSlack
                + " — a per-cycle thread leak (e.g. a runtime-scoped pool not torn down on stop());"
                + " see logged histogram");
  }

  /** One full lifecycle: start an isolated runtime, prove it serves, then stop it. */
  private void runOneCycle(String label) {
    TestKit testKit = new TestKit(TestKit.Settings.DEFAULT);
    try {
      testKit.start();
      HttpClient httpClient = testKit.getSelfHttpClient();
      var response = httpClient.GET("/clash/hello").responseBodyAs(String.class).invoke();
      assertEquals(200, response.status().intValue(), "cycle " + label + " did not serve traffic");
      assertEquals("Hello World", response.body(), "cycle " + label + " returned unexpected body");
    } finally {
      testKit.stop();
    }
  }

  /**
   * Nudges the JVM to release resources only pinned by unreferenced objects (a finalizer-closed
   * socket, a terminated dispatcher's threads) so the samples reflect genuinely retained resources
   * rather than not-yet-reaped garbage.
   */
  private void settle() {
    System.gc();
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Logs live threads bucketed by name with the trailing pool index stripped, for leak diagnosis. */
  private void logThreadHistogram() {
    Map<String, Integer> byPrefix = new TreeMap<>();
    for (Thread t : Thread.getAllStackTraces().keySet()) {
      String prefix = t.getName().replaceAll("[-_]?\\d+.*$", "");
      byPrefix.merge(prefix, 1, Integer::sum);
    }
    log.info("Live thread histogram (name prefix -> count): {}", byPrefix);
  }
}
