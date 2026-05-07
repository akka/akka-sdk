/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.agent.MemoryFilter;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.UserMessage;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for {@link MemoryHistoryUtils}. The helper is shared by
 * {@code SessionMemoryEntity#getHistory} and the agent runtime's journal-fallback path, so its
 * behaviour is tested in isolation here in addition to being exercised through the entity tests.
 */
public class MemoryHistoryUtilsTest {

  private static final Instant TS = Instant.now();

  // --- helpers ---------------------------------------------------------------

  private static UserMessage user(String componentId, String text) {
    return new UserMessage(TS, text, componentId);
  }

  private static AiMessage ai(String componentId, String text) {
    return new AiMessage(TS, text, componentId, Collections.emptyList());
  }

  /** Tiny in-memory AgentRegistry: maps componentId -> role. */
  private static AgentRegistry registry(java.util.Map<String, String> componentIdToRole) {
    return new AgentRegistry() {
      @Override
      public Set<AgentInfo> allAgents() {
        return componentIdToRole.entrySet().stream()
            .map(e -> new AgentInfo(e.getKey(), "", "", e.getValue()))
            .collect(java.util.stream.Collectors.toSet());
      }

      @Override
      public Set<AgentInfo> agentsWithRole(String role) {
        return allAgents().stream()
            .filter(a -> role.equals(a.role()))
            .collect(java.util.stream.Collectors.toSet());
      }

      @Override
      public AgentInfo agentInfo(String agentId) {
        return agentInfoOption(agentId).orElseThrow();
      }

      @Override
      public Optional<AgentInfo> agentInfoOption(String agentId) {
        var role = componentIdToRole.get(agentId);
        return role == null
            ? Optional.empty()
            : Optional.of(new AgentInfo(agentId, "", "", role));
      }
    };
  }

  private static Function<String, Optional<String>> emptyRoleLookup() {
    return id -> Optional.empty();
  }

  // --- applyFilters ----------------------------------------------------------

  @Test
  public void emptyFilterListReturnsInputUnchanged() {
    var messages = List.<SessionMessage>of(user("a", "hi"), ai("a", "hello"));
    var result = MemoryHistoryUtils.applyFilters(messages, List.of(), emptyRoleLookup());
    assertThat(result).isEqualTo(messages);
  }

  @Test
  public void includeByAgentIdKeepsOnlyMatchingMessages() {
    var u1 = user("agent-1", "u1");
    var a1 = ai("agent-1", "a1");
    var u2 = user("agent-2", "u2");
    var a2 = ai("agent-2", "a2");

    var result =
        MemoryHistoryUtils.applyFilters(
            List.of(u1, a1, u2, a2),
            List.of((MemoryFilter) MemoryFilter.Include.agentId("agent-1")),
            emptyRoleLookup());

    assertThat(result).containsExactly(u1, a1);
  }

  @Test
  public void includeByAgentRoleResolvesViaRoleLookup() {
    var u1 = user("summarizer-1", "u1");
    var a1 = ai("summarizer-1", "a1");
    var u2 = user("worker-1", "u2");
    var roleLookup =
        MemoryHistoryUtils.roleLookup(
            registry(java.util.Map.of("summarizer-1", "summarizer", "worker-1", "worker")));

    var result =
        MemoryHistoryUtils.applyFilters(
            List.of(u1, a1, u2),
            List.of((MemoryFilter) MemoryFilter.Include.agentRole("summarizer")),
            roleLookup);

    assertThat(result).containsExactly(u1, a1);
  }

  @Test
  public void includeByIdOrRoleUsesOrSemantics() {
    // Include filter with both ids and roles: keep if id matches OR role matches.
    var u1 = user("agent-1", "u1");
    var u2 = user("agent-2", "u2");
    var u3 = user("agent-3", "u3");
    var roleLookup =
        MemoryHistoryUtils.roleLookup(
            registry(java.util.Map.of("agent-3", "summarizer")));

    var result =
        MemoryHistoryUtils.applyFilters(
            List.of(u1, u2, u3),
            List.of(
                (MemoryFilter)
                    new MemoryFilter.Include(Set.of("agent-1"), Set.of("summarizer"))),
            roleLookup);

    assertThat(result).containsExactly(u1, u3);
  }

  @Test
  public void excludeByAgentIdDropsMatchingMessages() {
    var u1 = user("agent-1", "u1");
    var u2 = user("agent-2", "u2");

    var result =
        MemoryHistoryUtils.applyFilters(
            List.of(u1, u2),
            List.of((MemoryFilter) MemoryFilter.Exclude.agentId("agent-2")),
            emptyRoleLookup());

    assertThat(result).containsExactly(u1);
  }

  @Test
  public void excludeByAgentRoleResolvesViaRoleLookup() {
    var u1 = user("agent-1", "u1");
    var u2 = user("agent-2", "u2");
    var roleLookup =
        MemoryHistoryUtils.roleLookup(
            registry(java.util.Map.of("agent-2", "translator")));

    var result =
        MemoryHistoryUtils.applyFilters(
            List.of(u1, u2),
            List.of((MemoryFilter) MemoryFilter.Exclude.agentRole("translator")),
            roleLookup);

    assertThat(result).containsExactly(u1);
  }

  @Test
  public void multipleFiltersChainNarrowing() {
    // Include agent-1 OR agent-2, then exclude agent-2 -> only agent-1 messages remain.
    var u1 = user("agent-1", "u1");
    var u2 = user("agent-2", "u2");
    var u3 = user("agent-3", "u3");

    var result =
        MemoryHistoryUtils.applyFilters(
            List.of(u1, u2, u3),
            List.of(
                MemoryFilter.Include.agentIds(Set.of("agent-1", "agent-2")),
                MemoryFilter.Exclude.agentId("agent-2")),
            emptyRoleLookup());

    assertThat(result).containsExactly(u1);
  }

  @Test
  public void blankRoleIsTreatedAsAbsent() {
    // An agent registered with a blank role must not match a role-based filter.
    var u1 = user("agent-1", "u1");
    var roleLookup =
        MemoryHistoryUtils.roleLookup(registry(java.util.Map.of("agent-1", "  ")));

    var result =
        MemoryHistoryUtils.applyFilters(
            List.of(u1),
            List.of((MemoryFilter) MemoryFilter.Include.agentRole("worker")),
            roleLookup);

    assertThat(result).isEmpty();
  }

  // --- trimToLastN ----------------------------------------------------------

  @Test
  public void trimToLastNReturnsInputWhenLimitAbsent() {
    var messages = List.<SessionMessage>of(user("a", "1"), user("a", "2"), user("a", "3"));
    var result = MemoryHistoryUtils.trimToLastN(messages, Optional.empty());
    assertThat(result).isSameAs(messages);
  }

  @Test
  public void trimToLastNReturnsInputWhenAlreadyWithinLimit() {
    var messages = List.<SessionMessage>of(user("a", "1"), user("a", "2"));
    var result = MemoryHistoryUtils.trimToLastN(messages, Optional.of(5));
    assertThat(result).isSameAs(messages);
  }

  @Test
  public void trimToLastNKeepsTrailingMessagesWhenOverLimit() {
    var m1 = user("a", "1");
    var m2 = user("a", "2");
    var m3 = user("a", "3");
    var m4 = user("a", "4");

    var result = MemoryHistoryUtils.trimToLastN(List.of(m1, m2, m3, m4), Optional.of(2));

    assertThat(result).containsExactly(m3, m4);
  }

  // --- combined: simulates the journal-fallback path -------------------------

  @Test
  public void filterThenLastNMatchesEntityPathBehaviour() {
    // This mirrors what AgentImpl#journalToSpiContextMessages does after decoding events into
    // SessionMessages: apply filters, then trim to lastN. Ensures the journal-callback path
    // returns the same shape the entity path would have returned, just sourced from the journal.
    var u1 = user("agent-1", "u1");
    var a1 = ai("agent-1", "a1");
    var u2 = user("agent-2", "u2"); // filtered out
    var a2 = ai("agent-2", "a2"); // filtered out
    var u3 = user("agent-1", "u3");
    var a3 = ai("agent-1", "a3");
    var u4 = user("agent-1", "u4");
    var a4 = ai("agent-1", "a4");

    var filtered =
        MemoryHistoryUtils.applyFilters(
            List.of(u1, a1, u2, a2, u3, a3, u4, a4),
            List.of((MemoryFilter) MemoryFilter.Include.agentId("agent-1")),
            emptyRoleLookup());
    var trimmed = MemoryHistoryUtils.trimToLastN(filtered, Optional.of(4));

    assertThat(trimmed).containsExactly(u3, a3, u4, a4);
  }
}
