/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous.capability;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import java.util.function.UnaryOperator;

/**
 * Builder for team leadership configuration. Used inside the configuration function passed to
 * {@link akka.javasdk.agent.autonomous.AgentDefinition#canLeadTeam}.
 *
 * <p>The team lead creates backlogs, adds team members, monitors progress, and disbands the team
 * when work is complete.
 */
public interface TeamLeadership extends AgentCapability {

  /**
   * Add a member type to the team with default settings (maxInstances = 1).
   *
   * @param agentClass the autonomous agent class that can serve as a team member
   */
  TeamLeadership withMember(Class<? extends AutonomousAgent> agentClass);

  /**
   * Add a member type to the team with custom configuration.
   *
   * @param agentClass the autonomous agent class that can serve as a team member
   * @param config configuration function for the member type
   */
  TeamLeadership withMember(
      Class<? extends AutonomousAgent> agentClass, UnaryOperator<MemberConfig> config);

  /** Configuration for a team member type. */
  interface MemberConfig {

    /** Maximum number of instances of this member type that can be added to the team. */
    MemberConfig maxInstances(int max);
  }
}
