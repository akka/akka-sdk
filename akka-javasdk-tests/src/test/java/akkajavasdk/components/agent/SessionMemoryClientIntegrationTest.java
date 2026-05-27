/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.SessionHistory;
import akka.javasdk.agent.SessionHistoryResult;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.UserMessage;
import akka.javasdk.testkit.SessionMemoryClientTestAccess;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.runtime.sdk.spi.EventLogClient.Query;
import akkajavasdk.Junit5LogCapturing;
import com.typesafe.config.ConfigFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for {@link akka.javasdk.impl.agent.SessionMemoryClient#getHistory}:
 *
 * <ul>
 *   <li>journal fallback when the entity reports {@code Truncated},
 *   <li>journal replay starts at the compaction point so superseded messages are not surfaced,
 *   <li>the journal query is bounded by the entity's in-memory high-water mark so events persisted
 *       after the entity reply cannot leak in,
 *   <li>and the journal is left alone entirely when the entity reports {@code Loaded}.
 * </ul>
 */
@ExtendWith(Junit5LogCapturing.class)
public class SessionMemoryClientIntegrationTest extends TestKitSupport {

  // Tight cap so a handful of short interactions trigger truncation.
  private static final int MAX_SIZE_BYTES = 100;

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
        ConfigFactory.parseString(
            "akka.javasdk.agent.memory.limited-window.max-size = " + MAX_SIZE_BYTES + "B"));
  }

  @Test
  public void shouldRecoverFullHistoryFromJournalWhenEntityIsTruncated() {
    var sessionId = UUID.randomUUID().toString();
    var componentId = "test-agent";

    // 6 interactions x ~18B = ~108B → entity truncates its in-memory window.
    int interactions = 6;
    var pushedTexts = new ArrayList<String>();
    for (int i = 0; i < interactions; i++) {
      var userText = "user-msg-" + i;
      var aiText = "ai-msg-" + i;
      pushedTexts.add(userText);
      pushedTexts.add(aiText);
      addInteraction(sessionId, componentId, userText, aiText);
    }

    // Sanity: entity reports Truncated.
    assertThat(fetchHistory(sessionId))
        .as(
            "entity should report Truncated after %d interactions at max-size=%dB",
            interactions, MAX_SIZE_BYTES)
        .isInstanceOf(SessionHistoryResult.Truncated.class);

    // Entity in-memory window holds strictly fewer messages than we pushed (proves the drop).
    var entityTexts = textsOf(historyFromEntity(sessionId));
    assertThat(entityTexts)
        .as("entity in-memory window should be smaller than the full set we pushed")
        .hasSizeLessThan(pushedTexts.size());

    // Client transparently falls back to the journal and surfaces every message we pushed.
    var client = SessionMemoryClientTestAccess.sessionMemoryClient(testKit);
    assertThat(textsOf(client.getHistory(sessionId)))
        .as("journal fallback must restore the full ordered history")
        .containsExactlyElementsOf(pushedTexts);
  }

  @Test
  public void shouldReplayFromCompactionPointWhenEntityIsTruncatedAfterCompaction() {
    var sessionId = UUID.randomUUID().toString();
    var componentId = "test-agent";

    // 1 interaction we expect to be SUPERSEDED by the compaction summary.
    addInteraction(sessionId, componentId, "first-user", "first-ai");

    // Compact: pin the anchor (sequence number) and write a recognisable summary.
    var seqBeforeCompaction = currentSequenceNumber(sessionId);
    var summaryUser = "summary-user";
    var summaryAi = "summary-ai";
    componentClient
        .forEventSourcedEntity(sessionId)
        .method(SessionMemoryEntity::compactHistory)
        .invoke(
            new SessionMemoryEntity.CompactionCmd(
                new UserMessage(Instant.now(), summaryUser, componentId),
                new AiMessage(Instant.now(), summaryAi, componentId, Collections.emptyList()),
                seqBeforeCompaction));

    // 4 post-compaction interactions push the entity past max-size again.
    int postCompactionInteractions = 4;
    for (int i = 0; i < postCompactionInteractions; i++) {
      addInteraction(sessionId, componentId, "after-user-" + i, "after-ai-" + i);
    }

    // Sanity: entity reports Truncated, anchored at the compaction point.
    assertThat(fetchHistory(sessionId))
        .as(
            "entity should report Truncated after %d post-compaction interactions at max-size=%dB",
            postCompactionInteractions, MAX_SIZE_BYTES)
        .isInstanceOf(SessionHistoryResult.Truncated.class);

    // Entity in-memory window holds only the most recent "after-" interactions: the compaction
    // dropped "first-", and the second truncation dropped the "summary-" pair.
    var expectedEntityTexts = new ArrayList<String>();
    for (int i = 0; i < postCompactionInteractions; i++) {
      expectedEntityTexts.add("after-user-" + i);
      expectedEntityTexts.add("after-ai-" + i);
    }
    assertThat(textsOf(historyFromEntity(sessionId)))
        .as("entity in-memory window should only hold post-compaction \"after-\" interactions")
        .containsExactlyElementsOf(expectedEntityTexts);

    // Client journal replay returns summary + after-, and never leaks the pre-compaction "first-".
    var expectedClientTexts = new ArrayList<String>();
    expectedClientTexts.add(summaryUser);
    expectedClientTexts.add(summaryAi);
    expectedClientTexts.addAll(expectedEntityTexts);

    var client = SessionMemoryClientTestAccess.sessionMemoryClient(testKit);
    assertThat(textsOf(client.getHistory(sessionId)))
        .as("journal replay must start at the compaction point")
        .doesNotContain("first-user", "first-ai")
        .containsExactlyElementsOf(expectedClientTexts);
  }

  @Test
  public void shouldBoundJournalQueryByEntityInMemoryHighWaterMark() {
    var sessionId = UUID.randomUUID().toString();
    var componentId = "test-agent";

    // 6 interactions x ~18B = ~108B → entity truncates its in-memory window.
    int interactions = 6;
    for (int i = 0; i < interactions; i++) {
      addInteraction(sessionId, componentId, "user-msg-" + i, "ai-msg-" + i);
    }

    // The high-water mark the journal query must honor.
    var truncated = (SessionHistoryResult.Truncated) fetchHistory(sessionId);

    // Spy the EventLogClient: capture the query, then delegate to the real one so the read still
    // returns a real history.
    var queryRef = new AtomicReference<Query>();
    var spy = SessionMemoryClientTestAccess.capturingMemoryClient(testKit, queryRef);
    var client = SessionMemoryClientTestAccess.sessionMemoryClient(testKit, spy);

    client.getHistory(sessionId);

    var capturedQuery = queryRef.get();
    assertThat(capturedQuery)
        .as("SessionMemoryClient must call the journal when entity is Truncated")
        .isNotNull();
    assertThat(capturedQuery.fromSequenceNr())
        .as("journal query fromSequenceNr must equal the entity's reported compaction point")
        .isEqualTo(truncated.fromSequenceNr());
    assertThat(capturedQuery.toSequenceNr())
        .as("journal query toSequenceNr must equal the entity's reported high-water mark")
        .isEqualTo(truncated.toSequenceNr());
  }

  @Test
  public void shouldNotTouchMemoryClientWhenEntityIsNotTruncated() {
    var sessionId = UUID.randomUUID().toString();
    var componentId = "test-agent";

    // A couple of short interactions, well under the cap — no truncation expected.
    addInteraction(sessionId, componentId, "user-A", "ai-A");
    addInteraction(sessionId, componentId, "user-B", "ai-B");

    // Sanity: entity reports Loaded, not Truncated.
    assertThat(fetchHistory(sessionId)).isInstanceOf(SessionHistoryResult.Loaded.class);

    // Inject a MemoryClient that explodes if touched. If a future change makes the client read
    // the journal on the Loaded branch the AssertionError surfaces here.
    var client =
        SessionMemoryClientTestAccess.sessionMemoryClient(
            testKit, SessionMemoryClientTestAccess.explodingMemoryClient());

    assertThat(textsOf(client.getHistory(sessionId)))
        .as("when not truncated, client must serve the entity reply without touching the journal")
        .containsExactly("user-A", "ai-A", "user-B", "ai-B");
  }

  // --- helpers -------------------------------------------------------------------------------

  private void addInteraction(
      String sessionId, String componentId, String userText, String aiText) {
    var now = Instant.now();
    componentClient
        .forEventSourcedEntity(sessionId)
        .method(SessionMemoryEntity::addInteraction)
        .invoke(
            new SessionMemoryEntity.AddInteractionCmd(
                new UserMessage(now, userText, componentId),
                new AiMessage(now, aiText, componentId, Collections.emptyList())));
  }

  private SessionHistoryResult fetchHistory(String sessionId) {
    return componentClient
        .forEventSourcedEntity(sessionId)
        .method(SessionMemoryEntity::fetchHistory)
        .invoke(new SessionMemoryEntity.GetHistoryCmd());
  }

  private SessionHistory historyFromEntity(String sessionId) {
    return componentClient
        .forEventSourcedEntity(sessionId)
        .method(SessionMemoryEntity::getHistory)
        .invoke(new SessionMemoryEntity.GetHistoryCmd());
  }

  /** Sequence number reflected back by the entity, used as the compaction anchor. */
  private long currentSequenceNumber(String sessionId) {
    return historyFromEntity(sessionId).sequenceNumber();
  }

  private static List<String> textsOf(SessionHistory history) {
    return history.messages().stream()
        .map(
            m ->
                switch (m) {
                  case UserMessage u -> u.text();
                  case AiMessage a -> a.text();
                  default -> throw new AssertionError("unexpected message type: " + m);
                })
        .toList();
  }
}
