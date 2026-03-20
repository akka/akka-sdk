/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous.capability;

import akka.javasdk.agent.autonomous.AutonomousAgent;

/**
 * Declares that an agent can lead a team of autonomous agents. The team lead creates backlogs, adds
 * team members, monitors progress, and disbands the team when work is complete.
 *
 * <p>Created via {@link AutonomousAgent#canLeadTeam}.
 */
public interface TeamLeadership extends AgentCapability {

  /** Create a team leadership capability with the given member types. */
  static TeamLeadership of(MemberType... memberTypes) {
    return akka.javasdk.impl.agent.autonomous.capability.TeamLeadershipImpl.create(memberTypes);
  }

  /** A type of team member that can be added to the team. */
  interface MemberType {

    /** Create a member type for the given agent class. */
    static MemberType of(Class<? extends AutonomousAgent> agentClass) {
      return new akka.javasdk.impl.agent.autonomous.capability.MemberTypeImpl(agentClass, 1);
    }

    /** Maximum number of instances of this member type that can be added to the team. */
    MemberType maxInstances(int max);
  }
}
