/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.NotUsed;
import akka.javasdk.impl.agent.SessionMemoryClient;
import akka.javasdk.impl.agent.SessionMemoryClient.MemorySettings;
import akka.runtime.sdk.spi.EventLogClient;
import akka.runtime.sdk.spi.EventLogClient.Query;
import akka.runtime.sdk.spi.SpiEventSourcedEntity;
import akka.stream.scaladsl.Source;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test-only bridge that constructs a real {@link SessionMemoryClient} backed by a running {@link
 * TestKit}. Lives in {@code akka.javasdk.testkit} to read {@link TestKit}'s package-private {@code
 * memoryClient} field without widening the published TestKit surface.
 */
public final class SessionMemoryClientTestAccess {

  private SessionMemoryClientTestAccess() {}

  /** Build a {@code SessionMemoryClient} using the {@link EventLogClient} the runtime wired up. */
  public static SessionMemoryClient sessionMemoryClient(TestKit testKit) {
    return sessionMemoryClient(testKit, testKit.eventLogClient);
  }

  /**
   * Build a {@code SessionMemoryClient} with an injected {@link EventLogClient}, so a test can
   * substitute the journal-stream source (e.g. with {@link #explodingMemoryClient()} to assert the
   * fallback path is not taken).
   */
  public static SessionMemoryClient sessionMemoryClient(
      TestKit testKit, EventLogClient eventLogClient) {
    return new SessionMemoryClient(
        testKit.getComponentClient(),
        eventLogClient,
        testKit.serializer,
        testKit.getAgentRegistry(),
        testKit.getMaterializer(),
        new MemorySettings(true, true, Optional.empty(), List.of()));
  }

  /**
   * A {@link EventLogClient} that records every query it sees into {@code capturedQuery} and then
   * delegates to the runtime's real client, so the read still produces a real history. Use to
   * assert that {@link SessionMemoryClient} builds the journal query with the right bounds.
   */
  public static EventLogClient capturingMemoryClient(
      TestKit testKit, AtomicReference<Query> capturedQuery) {
    EventLogClient delegate = testKit.eventLogClient;
    return new EventLogClient() {
      @Override
      public Source<SpiEventSourcedEntity.EventEnvelope, NotUsed> currentEventsForEntity(
          Query query) {
        capturedQuery.set(query);
        return delegate.currentEventsForEntity(query);
      }
    };
  }

  /**
   * A {@link EventLogClient} that fails any call to {@code currentEventsForEntity}. Use to prove
   * that the journal fallback path was not exercised — for example when the entity returns {@code
   * Loaded}.
   */
  public static EventLogClient explodingMemoryClient() {
    return new EventLogClient() {
      @Override
      public Source<SpiEventSourcedEntity.EventEnvelope, NotUsed> currentEventsForEntity(
          Query query) {
        throw new AssertionError(
            "EventLogClient.currentEventsForEntity was invoked; "
                + "SessionMemoryClient must serve from the entity when not truncated");
      }
    };
  }
}
