/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous.capability;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.impl.agent.autonomous.capability.TeamLeadershipImpl;
import akka.javasdk.impl.agent.autonomous.capability.TeamMemberImpl;

/**
 * Declares that an agent can lead a team of autonomous agents. The team lead creates backlogs, adds
 * team members, monitors progress, and disbands the team when work is complete.
 *
 * <p>Created via {@link TeamLeadership#of}.
 */
public interface TeamLeadership extends AgentCapability {

  /** Create a team leadership capability with the given member types. */
  static TeamLeadership of(TeamMember first, TeamMember... rest) {
    return TeamLeadershipImpl.create(first, rest);
  }

  /** A team member type that can be added to the team. */
  interface TeamMember {

    /** Create a team member type for the given agent class. */
    static TeamMember of(Class<? extends AutonomousAgent> agentClass) {
      return new TeamMemberImpl(agentClass, 1);
    }

    /** Maximum number of instances of this member type that can be added to the team. */
    TeamMember maxInstances(int max);
  }
}
