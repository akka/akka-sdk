/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import java.util.ArrayList;
import java.util.List;

/** State of a team — member registry. */
public record TeamState(
    String teamId, String taskListId, List<TeamMember> members, boolean disbanded) {

  public record TeamMember(String agentId, String agentType, String description) {}

  public static TeamState empty() {
    return new TeamState("", "", List.of(), false);
  }

  public TeamState withMember(String agentId, String agentType, String description) {
    var updated = new ArrayList<>(members);
    updated.add(new TeamMember(agentId, agentType, description));
    return new TeamState(teamId, taskListId, updated, disbanded);
  }

  public TeamState withoutMember(String agentId) {
    var updated = members.stream().filter(m -> !m.agentId().equals(agentId)).toList();
    return new TeamState(teamId, taskListId, updated, disbanded);
  }

  public TeamState withDisbanded() {
    return new TeamState(teamId, taskListId, members, true);
  }
}
