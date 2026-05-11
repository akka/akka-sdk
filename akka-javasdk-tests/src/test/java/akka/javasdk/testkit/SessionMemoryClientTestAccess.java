/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.NotUsed;
import akka.javasdk.impl.agent.SessionMemoryClient;
import akka.javasdk.impl.agent.SessionMemoryClient.MemorySettings;
import akka.runtime.sdk.spi.BytesPayload;
import akka.runtime.sdk.spi.MemoryClient;
import akka.runtime.sdk.spi.MemoryContextRequest;
import akka.stream.scaladsl.Source;
import java.util.List;
import java.util.Optional;

/**
 * Test-only bridge that constructs a real {@link SessionMemoryClient} backed by a running {@link
 * TestKit}. Lives in {@code akka.javasdk.testkit} to read {@link TestKit}'s package-private {@code
 * memoryClient} field without widening the published TestKit surface.
 */
public final class SessionMemoryClientTestAccess {

  private SessionMemoryClientTestAccess() {}

  /** Build a {@code SessionMemoryClient} using the {@link MemoryClient} the runtime wired up. */
  public static SessionMemoryClient sessionMemoryClient(TestKit testKit) {
    return sessionMemoryClient(testKit, testKit.memoryClient);
  }

  /**
   * Build a {@code SessionMemoryClient} with an injected {@link MemoryClient}, so a test can
   * substitute the journal-stream source (e.g. with {@link #explodingMemoryClient()} to assert the
   * fallback path is not taken).
   */
  public static SessionMemoryClient sessionMemoryClient(
      TestKit testKit, MemoryClient memoryClient) {
    return new SessionMemoryClient(
        testKit.getComponentClient(),
        memoryClient,
        testKit.serializer,
        testKit.getAgentRegistry(),
        testKit.getMaterializer(),
        new MemorySettings(true, true, Optional.empty(), List.of()));
  }

  /**
   * A {@link MemoryClient} that fails any call to {@code fetchStream}. Use to prove that the
   * journal fallback path was not exercised — for example when the entity returns {@code Loaded}.
   */
  public static MemoryClient explodingMemoryClient() {
    return new MemoryClient() {
      @Override
      public Source<BytesPayload, NotUsed> fetchStream(MemoryContextRequest request) {
        throw new AssertionError(
            "MemoryClient.fetchStream was invoked; SessionMemoryClient must serve from the entity"
                + " when not truncated");
      }
    };
  }
}
